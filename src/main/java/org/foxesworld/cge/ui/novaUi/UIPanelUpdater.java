package org.foxesworld.cge.ui.novaUi;

import org.foxesworld.cge.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.ui.novaUi.elements.progress.ProgressElement;
import org.foxesworld.cge.ui.novaUi.elements.text.TextElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * UIPanelUpdater – «мотор» runtime-логики:
 *  • bindAllFields() – первая привязка всех TextElement и ProgressElement
 *       к полям у eventHandlerTarget (через рефлексию Field→UIElement)
 *  • update(tpf) – каждый кадр:
 *       • сканит boundFields, сравнивает старое/новое значение → обновляет UI
 *       • вызывает TextElement.update(tpf) и ProgressElement.update(tpf)
 *       • собирает «грязные» панели и пересчитывает их один раз за кадр
 *       • после recomputeSize делает overlap-проверку
 *  • fixOverlaps(panel) – устраняет перекрытия дочерних элементов внутри панели
 */
public class UIPanelUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIPanelUpdater.class);

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
                    pe.update(tpf);
                }

            } catch (IllegalAccessException iae) {
                LOGGER.warn("Cannot access field '{}' for '{}'", field.getName(), ue.getId());
            }
        }

        if (!dirtyPanels.isEmpty()) {
            for (PanelElement panel : dirtyPanels) {
                PanelElement cur = panel;
                while (cur != null) {
                    preserveChildPositionsAndReapply(cur);
                    cur = cur.getParentPanel();
                }
            }
            dirtyPanels.clear();
        }
    }

    public void markDirty(PanelElement panel) {
        if (panel != null) {
            dirtyPanels.add(panel);
        }
    }

    public void fixOverlaps(PanelElement panel) {
        List<UIElement> children = new ArrayList<>(panel.getChildren());
        String layout = panel.getLayout();
        //float padding = panel.getPadding();
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

                // Устанавливаем текущий элемент вплотную к предыдущему
                currentElement.getNode().setLocalTranslation(nextPosX, currentPosY, 0f);
            }


        } else {
            // Сортировка по убыванию Y (от верхнего к нижнему)
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

                // Устанавливаем Y вплотную под предыдущий элемент
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
     * Сохраняет позицию дочерних элементов перед пересчётом и восстанавливает её после.
     */
    private void preserveChildPositionsAndReapply(PanelElement panel) {
        Map<UIElement, float[]> childPositions = new HashMap<>();
        for (UIElement child : panel.getChildren()) {
            var pos = child.getNode().getLocalTranslation();
            childPositions.put(child, new float[]{pos.x, pos.y, pos.z});
        }

        panel.recomputeSizeAndRepositionChildren();

        for (Map.Entry<UIElement, float[]> e : childPositions.entrySet()) {
            float[] pos = e.getValue();
            e.getKey().getNode().setLocalTranslation(pos[0], pos[1], pos[2]);
        }
    }
}
