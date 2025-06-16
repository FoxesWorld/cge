package org.foxesworld.cge.modules.ui.novaUi;

import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.progress.ProgressElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.text.TextElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * NovaUIUpdater is the runtime engine for NovaUI:
 *  • bindAllFields() — binds all TextElement and ProgressElement fields to eventHandlerTarget object fields via reflection.
 *  • update(tpf) — per-frame update:
 *      • Scans boundFields, compares previous/current values and updates UI if needed.
 *      • Calls update(tpf) on TextElement and ProgressElement.
 *      • Collects "dirty" panels and recomputes them once per frame.
 *      • After recomputeSize, runs overlap check.
 *  • fixOverlaps(panel) — resolves overlaps of child elements within a panel.
 *
 * Improved:
 *  • Panel backgrounds with alpha channel are preserved and not recreated/lost.
 *  • UI updates are more efficient and visually stable.
 */
public class NovaUIUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(NovaUIUpdater.class);

    private Object eventHandlerTarget = null;
    private Map<String, UIElement> allElements;

    private final Map<UIElement, Field> boundFields = new LinkedHashMap<>();
    private final Map<UIElement, Object> lastKnownValues = new HashMap<>();
    private final Set<PanelElement> dirtyPanels = new LinkedHashSet<>();

    public void setEventHandlerTarget(Object handler) {
        this.eventHandlerTarget = handler;
    }

    public void setAllElements(Map<String, UIElement> allElements) {
        this.allElements = allElements;
    }

    /**
     * Binds all UIElements with matching field names to fields in the eventHandlerTarget.
     */
    public void bindAllFields() {
        boundFields.clear();
        lastKnownValues.clear();

        if (eventHandlerTarget == null) {
            LOGGER.debug("bindAllFields: no eventHandlerTarget");
            return;
        }

        Map<String, Field> fieldMap = new HashMap<>();
        for (Field f : eventHandlerTarget.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            fieldMap.put(f.getName(), f);
        }
        LOGGER.debug("bindAllFields: handler fields = {}", fieldMap.keySet());

        for (UIElement ue : allElements.values()) {
            String id = ue.getId();
            if (!fieldMap.containsKey(id)) continue;
            Field field = fieldMap.get(id);

            if (ue instanceof TextElement te) {
                boundFields.put(te, field);
                try {
                    Object raw = field.get(eventHandlerTarget);
                    String text = (raw != null) ? raw.toString() : "";
                    te.setText(text);
                    lastKnownValues.put(te, text);
                } catch (Exception ex) {
                    LOGGER.warn("Cannot bind initial text for '{}'", id, ex);
                    lastKnownValues.put(te, "");
                }

            } else if (ue instanceof ProgressElement pe) {
                boundFields.put(pe, field);
                try {
                    Object raw = field.get(eventHandlerTarget);
                    float val = 0f;
                    if (raw instanceof Number) {
                        val = ((Number) raw).floatValue();
                    } else if (raw instanceof String) {
                        try {
                            val = Float.parseFloat((String) raw);
                        } catch (NumberFormatException ignore) {
                            val = 0f;
                        }
                    }
                    val = clamp01(val);
                    pe.setProgress(val);
                    lastKnownValues.put(pe, val);
                } catch (Exception ex) {
                    LOGGER.warn("Cannot bind initial progress for '{}'", id, ex);
                    lastKnownValues.put(pe, 0f);
                }
            }
        }

        for (UIElement ue : allElements.values()) {
            if (ue instanceof PanelElement panel) {
                fixOverlaps(panel);
            }
        }
    }

    /**
     * Updates all bound fields and UI elements per frame.
     * Only recomputes panels that are marked dirty.
     * Preserves and restores panel backgrounds with alpha.
     */
    public void update(float tpf) {
        if (boundFields.isEmpty()) {
            return;
        }

        for (var entry : boundFields.entrySet()) {
            UIElement ue = entry.getKey();
            Field field = entry.getValue();

            try {
                Object raw = field.get(eventHandlerTarget);

                if (ue instanceof TextElement te) {
                    String newText = (raw != null) ? raw.toString() : "";
                    String oldText = (String) lastKnownValues.getOrDefault(te, "");
                    if (!newText.equals(oldText)) {
                        te.setText(newText);
                        lastKnownValues.put(te, newText);
                        markDirty(te.getParentPanel());
                    }
                    te.update(tpf);

                } else if (ue instanceof ProgressElement pe) {
                    float newVal = 0f;
                    if (raw instanceof Number) {
                        newVal = ((Number) raw).floatValue();
                    } else if (raw instanceof String) {
                        try {
                            newVal = Float.parseFloat((String) raw);
                        } catch (NumberFormatException ignore) {
                            newVal = 0f;
                        }
                    }
                    newVal = clamp01(newVal);
                    float oldVal = (Float) lastKnownValues.getOrDefault(pe, -1f);
                    if (Math.abs(newVal - oldVal) > 1e-5f) {
                        pe.setProgress(newVal);
                        lastKnownValues.put(pe, newVal);
                        markDirty(pe.getParentPanel());
                    }
                    pe.updateSelf(tpf);
                }

            } catch (IllegalAccessException iae) {
                LOGGER.warn("Cannot access field '{}' for '{}'", field.getName(), ue.getId());
            }
        }

        // Efficiently update only dirty panels and preserve their backgrounds with alpha
        if (!dirtyPanels.isEmpty()) {
            for (PanelElement panel : dirtyPanels) {
                PanelElement cur = panel;
                while (cur != null) {
                    preserveBackgroundAndReapply(cur);
                    cur = cur.getParentPanel();
                }
            }
            dirtyPanels.clear();
        }
    }

    /**
     * Marks the panel as dirty, so it will be recomputed at the end of the frame.
     */
    public void markDirty(PanelElement panel) {
        if (panel != null) {
            dirtyPanels.add(panel);
        }
    }

    /**
     * Resolves overlaps of child elements in a panel for horizontal/vertical layouts.
     */
    public void fixOverlaps(PanelElement panel) {
        List<UIElement> children = new ArrayList<>(panel.getChildren());
        String layout = panel.getLayout();
        float margin = panel.getMargin();

        if ("horizontal".equalsIgnoreCase(layout)) {
            children.sort(Comparator.comparingDouble(e -> e.getNode().getLocalTranslation().x));

            for (int i = 1; i < children.size(); i++) {
                UIElement previousElement = children.get(i - 1);
                UIElement currentElement = children.get(i);

                float previousPosX = previousElement.getNode().getLocalTranslation().x;
                float previousWidth = previousElement.getWidth();

                float nextPosX = previousPosX + previousWidth + margin;
                float currentPosY = currentElement.getNode().getLocalTranslation().y;

                currentElement.getNode().setLocalTranslation(nextPosX, currentPosY, 0f);
            }
        } else {
            // Vertical (default): sort by Y descending (top to bottom)
            children.sort((a, b) -> Float.compare(
                    b.getNode().getLocalTranslation().y,
                    a.getNode().getLocalTranslation().y
            ));

            for (int i = 1; i < children.size(); i++) {
                UIElement previous = children.get(i - 1);
                UIElement current = children.get(i);

                float previousY = previous.getNode().getLocalTranslation().y;
                float previousHeight = previous.getHeight();

                float desiredY = previousY - previousHeight - (margin > 0 ? margin : 0);
                float currentX = current.getNode().getLocalTranslation().x;

                current.getNode().setLocalTranslation(currentX, desiredY, 0f);
            }
        }

        for (UIElement child : children) {
            if (child instanceof PanelElement pe) {
                fixOverlaps(pe);
            }
        }
    }

    private float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));
    }

    /**
     * Preserves panel background and child positions before recomputing,
     * then restores them, ensuring alpha is not lost for background quads.
     */
    private void preserveBackgroundAndReapply(PanelElement panel) {
        // Capture child positions
        Map<UIElement, float[]> childPositions = new HashMap<>();
        for (UIElement child : panel.getChildren()) {
            var pos = child.getNode().getLocalTranslation();
            childPositions.put(child, new float[]{pos.x, pos.y, pos.z});
        }
        // Capture background color and alpha
        var renderer = panel.getRenderer();
        float width = panel.getCurrentWidth();
        float height = panel.getCurrentHeight();
        var bgColor = panel.getBgColor();

        panel.recomputeSizeAndRepositionChildren();

        // Restore positions
        for (Map.Entry<UIElement, float[]> e : childPositions.entrySet()) {
            float[] pos = e.getValue();
            e.getKey().getNode().setLocalTranslation(pos[0], pos[1], pos[2]);
        }

        // Reapply background color with alpha channel preserved
        renderer.setBgColor(bgColor);
        renderer.setSize(width, height);
    }
}