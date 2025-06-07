package org.foxesworld.cge.ui.novaUi.elements.progress;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.ui.novaUi.elements.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

public class ProgressElement extends AbstractUIElement {
    private static final Logger logger = LoggerFactory.getLogger(ProgressElement.class);

    private final CalistaGameEngine engine;
    private final ProgressAnimator animator;
    private final ProgressRenderer renderer;

    // Цвета (RGBA поддерживает прозрачность)
    private ColorRGBA borderColor     = new ColorRGBA(0f, 0f, 0f, 0.8f);
    private ColorRGBA backgroundColor = new ColorRGBA(0.2f, 0.2f, 0.2f, 0.8f);
    private ColorRGBA fillColor       = new ColorRGBA(0f, 0.75f, 0.2f, 1f);

    // Положение и размеры (rawPosX/Y берутся из AbstractUIElement)
    // rawPosX, rawPosY унаследованы
    private float width  = 200f;
    private float height = 12f;

    // Толщина рамки, padding и внешний отступ
    private float borderThickness = 1f;
    private float padding         = 1f;
    private float margin          = 0f;

    private final Map<String, Consumer<String>> propertyHandlers = new HashMap<>();

    private Geometry borderGeom;
    private Geometry bgGeom;
    private Geometry fgGeom;

    public ProgressElement(CalistaGameEngine engine, String id, PanelElement parent) {
        super();
        this.engine = engine;
        this.id = id;
        this.parentPanel = parent;
        this.node.setName("Progress_" + id);

        this.renderer = new ProgressRenderer(engine);
        this.animator = new ProgressAnimator(0f, 1f);

        initPropertyHandlers();
        rebuildAllGeometries();
        recalcAndReposition();
        logger.debug("ProgressElement created: id='{}'", id);
    }

    private void initPropertyHandlers() {
        propertyHandlers.put("x", v -> {
            rawPosX = parseFloatOrDefault(v, rawPosX);
            recalcAndReposition();
        });
        propertyHandlers.put("y", v -> {
            rawPosY = parseFloatOrDefault(v, rawPosY);
            recalcAndReposition();
        });
        propertyHandlers.put("width", v -> {
            width = parseFloatOrDefault(v, width);
            rebuildAllGeometries();
            recalcAndReposition();
        });
        propertyHandlers.put("height", v -> {
            height = parseFloatOrDefault(v, height);
            rebuildAllGeometries();
            recalcAndReposition();
        });
        propertyHandlers.put("borderColor", v -> {
            borderColor = parseColorOrDefault(v, borderColor);
            if (borderGeom != null) {
                borderGeom.getMaterial().setColor("Color", borderColor);
            }
        });
        propertyHandlers.put("backgroundColor", v -> {
            backgroundColor = parseColorOrDefault(v, backgroundColor);
            if (bgGeom != null) {
                bgGeom.getMaterial().setColor("Color", backgroundColor);
            }
        });
        propertyHandlers.put("fillColor", v -> {
            fillColor = parseColorOrDefault(v, fillColor);
            if (fgGeom != null) {
                fgGeom.getMaterial().setColor("Color", fillColor);
            }
        });
        propertyHandlers.put("borderThickness", v -> {
            borderThickness = parseFloatOrDefault(v, borderThickness);
            rebuildAllGeometries();
            recalcAndReposition();
        });
        propertyHandlers.put("padding", v -> {
            padding = parseFloatOrDefault(v, padding);
            rebuildAllGeometries();
            recalcAndReposition();
        });
        propertyHandlers.put("margin", v -> {
            margin = parseFloatOrDefault(v, margin);
            recalcAndReposition();
        });
        propertyHandlers.put("progress", v -> {
            float p = clamp(parseFloatOrDefault(v, animator.getTarget()), 0f, 1f);
            animator.setTarget(p);
        });
        propertyHandlers.put("animationSpeed", v -> {
            float s = parseFloatOrDefault(v, animator.getSpeed());
            animator.setSpeed(s);
        });
    }

    @Override
    public void setProperty(String key, String value) {
        Consumer<String> handler = propertyHandlers.get(key);
        if (handler != null) {
            handler.accept(value);
        } else {
            logger.warn("ProgressElement '{}': unknown property '{}'", id, key);
        }
    }

    @Override
    public void setOnClickHandler(String methodName, Object eventHandlerTarget) {
        super.setOnClickHandler(methodName, eventHandlerTarget);
    }

    /**
     * Полная перестройка геометрий: рамка, фон и заливка.
     */
    private void rebuildAllGeometries() {
        // Удаляем старые при повторном создании
        if (borderGeom != null) { borderGeom.removeFromParent(); }
        if (bgGeom != null)     { bgGeom.removeFromParent(); }
        if (fgGeom != null)     { fgGeom.removeFromParent(); }

        // 1. Рамка (скорее всего самый задний zIndex = 0)
        borderGeom = renderer.buildOrUpdateBorder(width, height, borderColor, 0f);
        node.attachChild(borderGeom);

        // 2. Фон
        float innerW = Math.max(0f, width - 2f * borderThickness);
        float innerH = Math.max(0f, height - 2f * borderThickness);
        bgGeom = renderer.buildOrUpdateBackground(innerW, innerH, backgroundColor,
                borderThickness, padding, 1f);
        node.attachChild(bgGeom);

        // 3. Заливка (по текущему прогрессу)
        float innerContentW = Math.max(0f, innerW - 2f * padding);
        float displayed = clamp(animator.getDisplayed(), 0f, 1f);
        float fgW = innerContentW * displayed;
        fgGeom = renderer.buildOrUpdateFill(fgW, Math.max(0f, innerH - 2f * padding),
                fillColor, borderThickness, padding, 2f);
        node.attachChild(fgGeom);
    }

    /**
     * Устанавливает целевой прогресс (0..1), анимация будет выполняться автоматически.
     */
    public void setProgress(float value) {
        float c = clamp(value, 0f, 1f);
        animator.setTarget(c);
    }

    public float getProgress() {
        return animator.getTarget();
    }

    /**
     * Вызвать из NovaUI.update(tpf) для плавной анимации заливки.
     */
    public void updateSelf(float tpf) {
        boolean changed = animator.update(tpf);
        if (!changed) {
            return;
        }
        float innerW = Math.max(0f, width - 2f * borderThickness);
        float innerH = Math.max(0f, height - 2f * borderThickness);
        float innerContentW = Math.max(0f, innerW - 2f * padding);
        float displayed = clamp(animator.getDisplayed(), 0f, 1f);
        float fgW = innerContentW * displayed;
        renderer.updateFill(fgW, Math.max(0f, innerH - 2f * padding),
                fillColor, borderThickness, padding, 2f);
    }

    /**
     * Позиционирование с учётом коллизий: не допускаем наложения вертикальных полос.
     */
    public void recalcAndReposition() {
        float parentHeight = (parentPanel != null)
                ? parentPanel.getCurrentHeight()
                : engine.getCamera().getHeight();

        if (parentPanel != null) {
            List<ProgressElement> siblings = new ArrayList<>();
            for (UIElement child : parentPanel.getChildren()) {
                if (child instanceof ProgressElement pe) {
                    siblings.add(pe);
                }
            }
            // Сортируем по rawPosY (чем больше rawPosY, тем выше на экране)
            siblings.sort(Comparator.comparing(pe -> pe.rawPosY));

            float prevBottom = Float.POSITIVE_INFINITY;
            for (ProgressElement pe : siblings) {
                float basePy = parentHeight - pe.rawPosY - pe.height - pe.margin;
                float finalPy = basePy;
                if (prevBottom != Float.POSITIVE_INFINITY) {
                    float currentTop = basePy + pe.height;
                    if (currentTop > prevBottom - pe.margin) {
                        finalPy = prevBottom - pe.height - pe.margin;
                    }
                }
                float px = pe.rawPosX + pe.margin;
                pe.node.setLocalTranslation(px, finalPy, 0f);
                prevBottom = finalPy;
            }
        } else {
            float basePy = parentHeight - rawPosY - height - margin;
            float px = rawPosX + margin;
            node.setLocalTranslation(px, basePy, 0f);
        }
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public Geometry getBorderGeom() {
        return borderGeom;
    }

    private float parseFloatOrDefault(String s, float def) {
        if (s == null || s.isEmpty()) return def;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            logger.warn("ProgressElement '{}': cannot parse '{}' as float, using {}", id, s, def);
            return def;
        }
    }

    /**
     * Разбирает строку вида "r,g,b" или "r,g,b,a", где компоненты могут быть
     * заданы либо в диапазоне [0..1], либо в [0..255]. Если указаны только
     * три компонента, альфа считается равной 1. Если указаны четыре,
     * альфа делится на 255 только если > 1.
     */
    private ColorRGBA parseColorOrDefault(String s, ColorRGBA def) {
        if (s == null || s.isEmpty()) {
            return def.clone();
        }
        String[] parts = s.split(",");
        if (parts.length < 3 || parts.length > 4) {
            logger.warn("ProgressElement '{}': invalid color format '{}'", id, s);
            return def.clone();
        }
        try {
            float r = Float.parseFloat(parts[0].trim());
            float g = Float.parseFloat(parts[1].trim());
            float b = Float.parseFloat(parts[2].trim());
            float a = 1f;
            if (parts.length == 4) {
                a = Float.parseFloat(parts[3].trim());
            }

            // Если rgb > 1, предполагаем, что они в [0..255]
            if (r > 1f || g > 1f || b > 1f) {
                r /= 255f;
                g /= 255f;
                b /= 255f;
            }

            // Альфу делим на 255 только если она явно > 1
            if (a > 1f) {
                a /= 255f;
            }

            return new ColorRGBA(r, g, b, a);
        } catch (NumberFormatException ex) {
            logger.warn("ProgressElement '{}': failed to parse color '{}'", id, s);
            return def.clone();
        }
    }


    private float clamp(float val, float min, float max) {
        return (val < min) ? min : Math.min(val, max);
    }
}
