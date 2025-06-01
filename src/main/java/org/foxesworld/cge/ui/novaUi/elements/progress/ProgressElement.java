package org.foxesworld.cge.ui.novaUi.elements.progress;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.ui.novaUi.elements.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * ProgressElement – основной UI-элемент: плавно анимирует заполнение и
 * при позиционировании внутри родной PanelElement учитывает «коллизию»
 * с другими ProgressElement, чтобы не накладываться друг на друга.
 */
public class ProgressElement extends AbstractUIElement {
    private static final Logger logger = LoggerFactory.getLogger(ProgressElement.class);

    private final CalistaGameEngine engine;

    // Исходные координаты (от левого-верхнего угла родителя), до учёта коллизий
    private float rawPosX = 0f;
    private float rawPosY = 0f;

    // Размеры рамки (px)
    //private float width  = 200f;
    //private float height = 12f;

    // Цвета
    private ColorRGBA borderColor     = new ColorRGBA(0f, 0f, 0f, 0.8f);
    private ColorRGBA backgroundColor = new ColorRGBA(0.2f, 0.2f, 0.2f, 0.8f);
    private ColorRGBA fillColor       = new ColorRGBA(0f, 0.75f, 0.2f, 1f);

    // Толщина рамки, padding и внешний отступ (px)
    private float borderThickness = 1f;
    private float padding         = 1f;
    private float margin          = 0f;

    // Аниматор прогресса (отображаемый и целевой)
    private final ProgressAnimator animator;

    // Рендерер геометрий (border, background, fill)
    private final ProgressRenderer renderer;

    /** Обработчики свойств (ключ → Consumer<String>) */
    private final Map<String, Consumer<String>> propertyHandlers = new HashMap<>();

    // Ссылки на Geometry, получаемые из renderer:
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
            float p = parseFloatOrDefault(v, animator.getTarget());
            setProgress(p);
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

    // ========================================
    // Перестроение всех геометрий (border/bg/fill)
    // ========================================
    private void rebuildAllGeometries() {
        borderGeom = renderer.buildBorder(width, height, borderColor);
        node.attachChild(borderGeom);

        float innerW = Math.max(0f, width - 2f * (borderThickness + padding));
        float innerH = Math.max(0f, height - 2f * (borderThickness + padding));
        bgGeom = renderer.buildBackground(innerW, innerH, backgroundColor,
                borderThickness, padding, 1f);
        node.attachChild(bgGeom);

        float fgW = innerW * clamp(animator.getDisplayed(), 0f, 1f);
        fgGeom = renderer.buildFill(fgW, innerH, fillColor,
                borderThickness, padding, 2f);
        node.attachChild(fgGeom);
    }

    // ========================================
    // Управление прогрессом + анимация
    // ========================================
    public void setProgress(float value) {
        float c = clamp(value, 0f, 1f);
        animator.setTarget(c);
    }

    public float getProgress() {
        return animator.getTarget();
    }

    public void update(float tpf) {
        boolean changed = animator.update(tpf);
        if (!changed) {
            return;
        }
        float innerW = Math.max(0f, width - 2f * (borderThickness + padding));
        float innerH = Math.max(0f, height - 2f * (borderThickness + padding));
        float fgW = innerW * clamp(animator.getDisplayed(), 0f, 1f);
        renderer.updateFill(fgW, innerH, fillColor, borderThickness, padding);
    }

    // ========================================
    // Позиционирование + улучшенная защита от перекрытия
    // ========================================
    public void recalcAndReposition() {
        float parentHeight = (parentPanel != null
                ? parentPanel.getCurrentHeight()
                : engine.getCamera().getHeight());

        if (parentPanel != null) {
            List<ProgressElement> siblings = new ArrayList<>();
            for (UIElement child : parentPanel.getChildren()) {
                if (child instanceof ProgressElement pe) {
                    siblings.add(pe);
                }
            }
            siblings.sort(Comparator.comparing(pe -> pe.rawPosY));

            float prevBottom = Float.POSITIVE_INFINITY;
            for (ProgressElement pe : siblings) {
                float basePy = parentHeight - pe.rawPosY - pe.height - pe.margin;
                float finalPy = basePy;
                if (prevBottom != Float.POSITIVE_INFINITY) {
                    // верхняя граница текущего: basePy + height
                    float currentTop = basePy + pe.height;
                    // если текущий топ выше или пересекает предыдущий низ, сдвигаем вниз
                    if (currentTop > prevBottom - pe.margin) {
                        finalPy = prevBottom - pe.height - pe.margin;
                    }
                }
                float px = pe.rawPosX + pe.margin;
                pe.node.setLocalTranslation(px, finalPy, 0f);
                // обновляем prevBottom: это Y нижней границы (нижний край) текущего
                prevBottom = finalPy;
            }
        } else {
            float basePy = parentHeight - rawPosY - height - margin;
            float px = rawPosX + margin;
            node.setLocalTranslation(px, basePy, 0f);
        }
    }

    // ========================================
    // Геттеры для PanelElement.layout и AABB-проверки
    // ========================================
    public float getRawPosX() { return rawPosX; }
    public float getRawPosY() { return rawPosY; }
    public float getWidth()   { return width;  }
    public float getHeight()  { return height; }

    // ========================================
    // Вспомогательные утилиты
    // ========================================
    private float parseFloatOrDefault(String s, float def) {
        if (s == null || s.isEmpty()) return def;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            logger.warn("ProgressElement '{}': cannot parse '{}' as float, using {}", id, s, def);
            return def;
        }
    }

    private ColorRGBA parseColorOrDefault(String s, ColorRGBA def) {
        if (s == null || s.isEmpty()) {
            return def.clone();
        }
        String[] parts = s.split(",");
        if (parts.length != 4) {
            logger.warn("ProgressElement '{}': invalid color format '{}'", id, s);
            return def.clone();
        }
        try {
            float r = Float.parseFloat(parts[0].trim());
            float g = Float.parseFloat(parts[1].trim());
            float b = Float.parseFloat(parts[2].trim());
            float a = Float.parseFloat(parts[3].trim());
            return new ColorRGBA(r, g, b, a);
        } catch (NumberFormatException ex) {
            logger.warn("ProgressElement '{}': failed to parse color '{}'", id, s);
            return def.clone();
        }
    }

    private float clamp(float val, float min, float max) {
        return (val < min) ? min : Math.min(val, max);
    }

    public Geometry getBorderGeom() {
        return borderGeom;
    }
}