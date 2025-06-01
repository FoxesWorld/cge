package org.foxesworld.cge.ui.elements;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.AbstractUIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ProgressElement — элемент UI, стилизованный под полосу прогресса GTA V.
 *
 * Особенности:
 *  • Тонкая чёрная граница
 *  • Тёмно-серый фон внутри
 *  • Цветная заливка, заполняющаяся слева → справа
 *  • Небольшой внутренний отступ между границей и фоном (padding)
 *  • Автоматическое позиционирование внутри родительской панели
 *
 * Поддерживаемые параметры (через setProperty или XML-атрибуты):
 *  • x, y             : координаты от левого-верхнего угла родительской панели (rawPosX/rawPosY)
 *  • width, height    : общий размер границы (в пикселях)
 *  • borderColor      : цвет границы в формате "r,g,b,a" (по умолчанию: полупрозрачный чёрный)
 *  • backgroundColor  : цвет внутреннего фона в формате "r,g,b,a" (по умолчанию: тёмно-серый)
 *  • fillColor        : цвет индикатора прогресса в формате "r,g,b,a" (по умолчанию: зелёный)
 *  • borderThickness  : толщина границы в пикселях
 *  • progress         : значение прогресса [0.0..1.0]
 *  • padding          : внутренний отступ между границей и фоном/заливкой (в пикселях)
 *  • margin           : внешний отступ (в пикселях) от левого/верхнего края родителя
 */
public class ProgressElement extends AbstractUIElement {
    private static final Logger logger = LoggerFactory.getLogger(ProgressElement.class);

    private final CalistaGameEngine calistaGameEngine;

    // Позиция относительно левого-верхнего угла родительской панели
    private float rawPosX = 0f;
    private float rawPosY = 0f;

    // Внешние размеры (граница) в пикселях
    private float width = 200f;
    private float height = 12f;

    // Цвета: граница, фон и заливка
    private ColorRGBA borderColor     = new ColorRGBA(0f, 0f, 0f, 0.8f);
    private ColorRGBA backgroundColor = new ColorRGBA(0.2f, 0.2f, 0.2f, 0.8f);
    private ColorRGBA fillColor       = new ColorRGBA(0f, 0.75f, 0.2f, 1f);

    // Толщина границы в пикселях
    private float borderThickness = 1f;

    // Внутренний отступ между границей и бэкграундом/заливкой
    private float padding = 1f;

    // Внешний отступ от родительской панели (или экрана) в пикселях
    private float margin = 0f;

    // Текущий прогресс [0..1]
    private float progress = 0f;

    // Геометрии: рамка, фон и заливка
    private Geometry borderGeom;
    private Geometry bgGeom;
    private Geometry fgGeom;

    /** Обработчики свойств (ключ → Consumer<String>) */
    private final Map<String, Consumer<String>> propertyHandlers = new HashMap<>();

    public ProgressElement(CalistaGameEngine calistaGameEngine, String id, PanelElement parent) {
        super();
        this.calistaGameEngine = calistaGameEngine;
        this.id = id;
        this.parentPanel = parent;
        this.node.setName("Progress_" + id);

        initPropertyHandlers();

        // Построение геометрий: рамка, фон и заливка
        buildBorderGeom();
        buildBackgroundGeom();
        buildForegroundGeom();

        // Немедленное позиционирование внутри родительской панели
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
            rebuildAllGeoms();
            recalcAndReposition();
        });
        propertyHandlers.put("height", v -> {
            height = parseFloatOrDefault(v, height);
            rebuildAllGeoms();
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
            rebuildAllGeoms();
            recalcAndReposition();
        });
        propertyHandlers.put("padding", v -> {
            padding = parseFloatOrDefault(v, padding);
            rebuildAllGeoms();
            recalcAndReposition();
        });
        propertyHandlers.put("margin", v -> {
            margin = parseFloatOrDefault(v, margin);
            recalcAndReposition();
        });
        propertyHandlers.put("progress", v -> {
            float p = parseFloatOrDefault(v, progress);
            setProgress(p);
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
        // По умолчанию полоса прогресса не интерактивна
        super.setOnClickHandler(methodName, eventHandlerTarget);
    }

    // ================================
    // Построение геометрий
    // ================================

    /**
     * Построить (или перестроить) геометрию рамки.
     * Граница — это Quad размером width×height, закрашенный borderColor.
     */
    private void buildBorderGeom() {
        if (borderGeom != null) {
            node.detachChild(borderGeom);
        }
        Quad quad = new Quad(width, height);
        borderGeom = new Geometry("Border_" + id, quad);
        Material mat = new Material(calistaGameEngine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        mat.setColor("Color", borderColor);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        borderGeom.setMaterial(mat);
        // Z = 0: рамка рисуется на самом нижнем уровне
        borderGeom.setLocalTranslation(0f, 0f, 0f);
        node.attachChild(borderGeom);
    }

    /**
     * Построить (или перестроить) геометрию внутреннего фона.
     * Фон смещён от границы на (borderThickness + padding) с каждой стороны.
     */
    private void buildBackgroundGeom() {
        if (bgGeom != null) {
            node.detachChild(bgGeom);
        }
        // Размер внутренней области (фон) с учётом borderThickness и padding с обеих сторон
        float innerW = Math.max(0f, width - 2f * (borderThickness + padding));
        float innerH = Math.max(0f, height - 2f * (borderThickness + padding));
        Quad quad = new Quad(innerW, innerH);
        bgGeom = new Geometry("BG_" + id, quad);
        Material mat = new Material(calistaGameEngine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        mat.setColor("Color", backgroundColor);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        bgGeom.setMaterial(mat);
        // Позиционируем фон внутри рамки: смещение по (borderThickness + padding)
        bgGeom.setLocalTranslation(borderThickness + padding, borderThickness + padding, 1f);
        node.attachChild(bgGeom);
    }

    /**
     * Построить (или перестроить) геометрию заливки (foreground).
     * Ширина Quad пропорциональна текущему progress.
     */
    private void buildForegroundGeom() {
        if (fgGeom != null) {
            node.detachChild(fgGeom);
        }
        // Вычисляем ту же внутреннюю область, что и для фона
        float innerW = Math.max(0f, width - 2f * (borderThickness + padding));
        float innerH = Math.max(0f, height - 2f * (borderThickness + padding));
        // Ширина заливки зависит от уровня прогресса [0..1]
        float fgW = innerW * clamp(progress, 0f, 1f);
        Quad quad = new Quad(fgW, innerH);
        fgGeom = new Geometry("FG_" + id, quad);
        Material mat = new Material(calistaGameEngine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        mat.setColor("Color", fillColor);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        fgGeom.setMaterial(mat);
        // Смещение заливки совпадает с позицией фона, но Z = 2 (чтобы закрывать фон)
        fgGeom.setLocalTranslation(borderThickness + padding, borderThickness + padding, 2f);
        node.attachChild(fgGeom);
    }

    /**
     * Перестроить все три геометрии (рамка, фон и заливка).
     * Вызывается при изменении width, height, borderThickness или padding.
     */
    private void rebuildAllGeoms() {
        buildBorderGeom();
        buildBackgroundGeom();
        buildForegroundGeom();
    }

    // ================================
    // Управление прогрессом
    // ================================

    /**
     * Установить уровень прогресса [0..1]. Перерисовывает заливку.
     */
    public void setProgress(float value) {
        float clamped = clamp(value, 0f, 1f);
        if (Math.abs(clamped - this.progress) < 1e-5f) {
            return;
        }
        this.progress = clamped;
        updateForegroundGeom();
    }

    public float getProgress() {
        return progress;
    }

    /**
     * Обновить только геометрию заливки при изменении progress.
     */
    private void updateForegroundGeom() {
        if (fgGeom == null || bgGeom == null) {
            buildForegroundGeom();
            return;
        }
        // Повторяем вычисление внутренней области
        float innerW = Math.max(0f, width - 2f * (borderThickness + padding));
        float innerH = Math.max(0f, height - 2f * (borderThickness + padding));
        float fgW = innerW * clamp(progress, 0f, 1f);

        // Заменяем mesh у fgGeom на новый Quad нужной ширины
        fgGeom.setMesh(new Quad(fgW, innerH));
        fgGeom.setLocalTranslation(borderThickness + padding, borderThickness + padding, 2f);
    }

    // ================================
    // Позиционирование внутри родителя
    // ================================

    /**
     * Считает и устанавливает локальные координаты узла внутри родительской панели (или экрана).
     * margin используется как внешний отступ от лев.-верх. угла родителя.
     */
    public void recalcAndReposition() {
        float px = rawPosX + margin;
        float parentHeight = (parentPanel != null
                ? parentPanel.getCurrentHeight()
                : calistaGameEngine.getCamera().getHeight());
        float py = parentHeight - rawPosY - height - margin;

        node.setLocalTranslation(px, py, 0f);
    }

    // ================================
    // Геттеры для metricsRegistry в PanelElement
    // ================================

    /** Горизонтальное смещение от левого-верхнего угла родителя в пикселях. */
    public float getRawPosX() {
        return rawPosX;
    }

    /** Вертикальное смещение от левого-верхнего угла родителя в пикселях. */
    public float getRawPosY() {
        return rawPosY;
    }

    /** Общая ширина элемента (вместе с рамкой и padding). */
    public float getWidth() {
        return width;
    }

    /** Общая высота элемента (вместе с рамкой и padding). */
    public float getHeight() {
        return height;
    }

    // ================================
    // Вспомогательные методы
    // ================================

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
}
