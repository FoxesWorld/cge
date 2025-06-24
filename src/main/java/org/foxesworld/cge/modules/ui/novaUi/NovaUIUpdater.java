package org.foxesworld.cge.modules.ui.novaUi;

import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.progress.ProgressElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.text.TextElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.math.ColorRGBA;

import java.lang.reflect.Field;
import java.util.*;

/**
 * NovaUIUpdater is the runtime engine for NovaUI.
 * Now automatically collects allElements from eventHandlerTarget fields.
 * UI updates are efficient and stable, with extended logging.
 * Panel backgrounds' alpha channel is preserved on update.
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
        this.allElements = collectElementsFromFields(handler);
        LOGGER.info("Event handler target set: {}, collected allElements: {}", handler != null ? handler.getClass().getName() : "null", allElements != null ? allElements.keySet() : "null");
    }

    public void setAllElements(Map<String, UIElement> allElements) {
        this.allElements = allElements;
        LOGGER.info("All elements set: {} elements", allElements != null ? allElements.size() : 0);
    }

    private Map<String, UIElement> collectElementsFromFields(Object handler) {
        if (handler == null) {
            LOGGER.warn("collectElementsFromFields: handler is null");
            return Collections.emptyMap();
        }
        Map<String, UIElement> elements = new LinkedHashMap<>();
        int totalFields = 0, uiFields = 0, nullFields = 0, duplicateIds = 0;

        for (Field field : handler.getClass().getDeclaredFields()) {
            totalFields++;
            field.setAccessible(true);
            try {
                Object value = field.get(handler);
                if (value == null) {
                    nullFields++;
                    LOGGER.trace("Field '{}' is null, skipped.", field.getName());
                    continue;
                }
                if (value instanceof UIElement uiElem) {
                    uiFields++;
                    String id = uiElem.getId();
                    if (id == null || id.isEmpty()) {
                        LOGGER.warn("UIElement field '{}' has null/empty id and will be skipped.", field.getName());
                        continue;
                    }
                    if (elements.containsKey(id)) {
                        duplicateIds++;
                        LOGGER.warn("Duplicate UIElement id '{}' found in field '{}'. Previous field will be overwritten.", id, field.getName());
                    }
                    elements.put(id, uiElem);
                    LOGGER.debug("Collected UIElement field: '{}', id='{}', type={}", field.getName(), id, uiElem.getClass().getSimpleName());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to collect UIElement from field '{}': {}", field.getName(), e.toString());
                LOGGER.debug("Exception details:", e);
            }
        }
        LOGGER.info("collectElementsFromFields: scanned {} fields, found {} UIElement(s), {} null field(s), {} duplicate id(s). Final element count: {}.",
                totalFields, uiFields, nullFields, duplicateIds, elements.size());
        return elements;
    }

    public void bindAllFields() {
        boundFields.clear();
        lastKnownValues.clear();

        if (eventHandlerTarget == null) {
            LOGGER.warn("bindAllFields: eventHandlerTarget is not set.");
            return;
        }

        if (allElements == null || allElements.isEmpty()) {
            LOGGER.warn("bindAllFields: allElements is not set or empty.");
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
                    LOGGER.info("Bound TextElement '{}' to field '{}', initial value: '{}'", id, field.getName(), text);
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
                    LOGGER.info("Bound ProgressElement '{}' to field '{}', initial value: {}", id, field.getName(), val);
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
        LOGGER.info("Binding complete: {} fields bound.", boundFields.size());
    }

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
                        //LOGGER.debug("TextElement '{}' updated: '{}' -> '{}'", te.getId(), oldText, newText);
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
                        //LOGGER.debug("ProgressElement '{}' updated: {} -> {}", pe.getId(), oldVal, newVal);
                    }
                    pe.updateSelf(tpf);
                }

            } catch (IllegalAccessException iae) {
                LOGGER.warn("Cannot access field '{}' for '{}'", field.getName(), ue.getId(), iae);
            }
        }

        // Efficiently update only dirty panels and preserve their backgrounds with alpha
        if (!dirtyPanels.isEmpty()) {
            //LOGGER.info("Recomputing {} dirty panels...", dirtyPanels.size());
            for (PanelElement panel : dirtyPanels) {
                panel.setBgColor(new ColorRGBA(0,0,0,0));
                PanelElement cur = panel;
                while (cur != null) {
                    //preserveBackgroundAndReapplyAlpha(cur);
                    cur = cur.getParentPanel();
                }
            }
            dirtyPanels.clear();
        }
    }

    public void markDirty(PanelElement panel) {
        if (panel != null) {
            dirtyPanels.add(panel);
            //LOGGER.debug("Panel '{}' marked as dirty.", panel.getId());
        }
    }

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
                LOGGER.trace("fixOverlaps: Set '{}' X to {}", currentElement.getId(), nextPosX);
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
                LOGGER.trace("fixOverlaps: Set '{}' Y to {}", current.getId(), desiredY);
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
}