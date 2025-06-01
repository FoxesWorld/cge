package org.foxesworld.cge.ui.novaUi.elements.panel;

import com.jme3.math.ColorRGBA;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.ui.novaUi.elements.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Float.parseFloat;

/**
 * PanelElement — «центральный» класс панели, собирает воедино:
 *  • свойства (через PanelProperties)
 *  • рендер фона (через PanelRenderer)
 *  • layout-логику (через PanelLayout)
 *  • registry метрик (через MetricsRegistry)
 * Изменения в этом варианте:
 *  – если layout="none", считаем итоговую ширину всех детей (суммируем их ширины) + 2*padding,
 *    и «кладём» их горизонтально от левого края с отступом padding.
 *  – высота панели = max(высот детей) + 2*padding.
 *  – autoWidth/autoHeight по-прежнему позволяют (если включены) «расти» панели под содержимое.
 */
public class PanelElement extends AbstractUIElement {
    private static final Logger logger = LoggerFactory.getLogger(PanelElement.class);

    private final CalistaGameEngine engine;
    private final List<UIElement> children = new ArrayList<>();

    // Margin, padding, background color
    private float margin  = 0f;
    private float padding = 0f;
    private ColorRGBA bgColor = new ColorRGBA(0f, 0f, 0f, 0.5f);

    // Авто-/фиксированные размеры
    private boolean autoWidth  = true;   // по умолчанию авто-ширина
    private boolean autoHeight = true;   // по умолчанию авто-высота
    private float fixedWidth   = 0f;
    private float fixedHeight  = 0f;

    // Align относительно родителя (top-left, center и т.п.) — сейчас не трогаем, если нужно, можно вернуть
    private String align = "top-left";

    // Layout-mode & spacing
    // Если "none" — вручную кладём всех детей в левый-верхний угол (по горизонтали подряд).
    private String layout  = "none"; // "none", "vertical", "horizontal"
    private float spacing  = 0f;

    // Geometry фона
    private final PanelRenderer renderer;
    // Обработчики свойств (bgColor, width, height, margin, padding, align, layout, spacing)
    private final PanelProperties properties;
    // Логика подсчёта размеров и позиционирования (vertical/horizontal)
    private final PanelLayout layoutHelper;

    public PanelElement(CalistaGameEngine engine, String id, PanelElement parent) {
        super();
        this.engine = engine;
        this.id = id;
        this.parentPanel = parent;
        this.node.setName("Panel_" + id);

        // 1. инициализируем renderer
        this.renderer = new PanelRenderer(engine, this);
        // 2. property-handlers
        this.properties = new PanelProperties(this);
        // 3. registry метрик
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        // 4. layout-логика
        this.layoutHelper = new PanelLayout(this, metricsRegistry);

        // По умолчанию панель «растёт» под свои дети
        this.autoWidth  = true;
        this.autoHeight = true;

        logger.debug("PanelElement created: id='{}'", id);
    }

    // ----------------------------------------------------
    // API
    // ----------------------------------------------------

    @Override
    public boolean hasOwnAlign() {
        return false;
    }

    @Override
    public String getOwnAlign() {
        return null;
    }

    /**
     * Внешний вызов (например, UIPanel.setProperty) вызывает setProperty,
     * а дальше PanelProperties его разруливает.
     */
    @Override
    public void setProperty(String key, String value) {
        properties.apply(key, value);
        // Если поменялись width/height, margin, padding и т.д. — надо пересчитать
        recalcAndRepositionSelfAndAncestors();
    }

    @Override
    public void setOnClickHandler(String methodName, Object eventHandlerTarget) {
        super.setOnClickHandler(methodName, eventHandlerTarget);
    }

    /**
     * Добавить ребёнка в панель — автоматически помечаем всё «грязным»
     * и рекурсивно пересчитываем размеры и позиции.
     */
    public void addChild(UIElement child) {
        children.add(child);
        if (child instanceof AbstractUIElement) {
            ((AbstractUIElement) child).setParentPanel(this);
        }
        node.attachChild(child.getNode());
        recalcAndRepositionSelfAndAncestors();
    }

    public void removeChild(UIElement child) {
        children.remove(child);
        node.detachChild(child.getNode());
        recalcAndRepositionSelfAndAncestors();
    }

    /**
     * Основной метод пересчёта панели:
     * 1) Если layout="none" — суммируем ширины всех детей + 2*padding, в качестве высоты берём max высоты + 2*padding.
     *    Затем размещаем их горизонтально, «начиная от начала» (left-top внутри панели с учётом padding).
     * 2) Иначе (vertical/horizontal) — делегируем PanelLayout (он позаботится о габаритах и позициях).
     * 3) Обновляем geometry фона (PanelRenderer) под полученные fixedWidth/fixedHeight.
     */
    public void recomputeSizeAndRepositionChildren() {
        float totalWidth = 0f, totalHeight = 0f, maxChildHeight = 0f;

        for (UIElement child : children) {
            if (child instanceof AbstractUIElement abs) {
                // Важно: используем текущие реальные размеры, а не геттеры width/height конфигурации
                float cw = abs.getWidth();
                float ch = abs.getHeight();
                totalWidth += cw;
                totalHeight += ch;
                maxChildHeight = Math.max(maxChildHeight, ch);
            }
        }
        if ("none".equalsIgnoreCase(layout)) {
            float neededW = totalWidth + 2f * padding;
            float neededH = maxChildHeight + 2f * padding;

            // 3) Если авто-режим включён — перезаписываем fixedWidth/fixedHeight
            if (autoWidth) {
                fixedWidth = neededW;
            }
            if (autoHeight) {
                fixedHeight = neededH;
            }

            // 4) Укладываем детей горизонтально «с самого начала»,
            //    но прижимаем их к нижнему краю (localY = padding).
            float currentX = padding;
            for (UIElement child : children) {
                if (child instanceof AbstractUIElement abs) {
                    float cw = abs.getWidth();
                    //float ch = abs.getHeight();

                    float localX = currentX;
                    float localY = padding; // прижимаем к нижнему краю панели
                    abs.getNode().setLocalTranslation(localX, localY, 0f);

                    currentX += cw;
                    // Если нужен gap между элементами, можно: currentX += cw + spacing;
                }
            }

        } else {
            // Для vertical/horizontal — как и раньше, делегируем PanelLayout.
            layoutHelper.recomputeAndLayOut(totalWidth, totalHeight);
        }

        // 5) Обновляем фон (PanelRenderer) под новые размеры панели
        //renderer.updateGeometry(totalWidth, totalHeight);
    }

    /**
     * Рекурсивно пересчитываем эту панель и всех её предков.
     */
    public void recalcAndRepositionSelfAndAncestors() {
        PanelElement current = this;
        while (current != null) {
            current.recomputeSizeAndRepositionChildren();
            // Репозиционируем саму панель относительно родителя
            if (current.parentPanel != null) {
                current.repositionRecursively(
                        current.parentPanel.getCurrentWidth(),
                        current.parentPanel.getCurrentHeight()
                );
            } else {
                // Если нет родителя — прикрепляем к корневому GUI-узлу
                current.repositionRecursively(
                        engine.getCamera().getWidth(),
                        engine.getCamera().getHeight()
                );
            }
            current = current.parentPanel;
        }
    }

    /**
     * Теперь панель позиционируется внутри родителя с учётом align + margin,
     * а не «всегда в (0,0)». При этом в recomputeSizeAndRepositionChildren()
     * сохранился горизонтальный «стек» детей и авто-расширение.
     */
    public void repositionRecursively(float parentW, float parentH) {
        if (parentPanel != null) {
            parentW = parentPanel.getCurrentWidth();
            parentH = parentPanel.getCurrentHeight();
        }

        float w = getCurrentWidth();
        float h = getCurrentHeight();
        float px, py;

        String a = align.toLowerCase();
        if (a.contains(",")) {
            String[] coords = a.split(",");
            try {
                px = margin + parseFloat(coords[0].trim());
                py = parentH - margin - parseFloat(coords[1].trim());
            } catch (Exception ex) {
                logger.warn("Panel '{}' invalid align coords: {}", id, align);
                px = margin;
                py = parentH - margin;
            }
        } else {
            switch (a) {
                case "top-left"   -> { px = margin;                 py = parentH - margin;           }
                case "top-right"  -> { px = parentW - margin - w;    py = parentH - margin;           }
                case "bottom-left"-> { px = margin;                  py = h + margin;                  }
                case "bottom-right"->{ px = parentW - margin - w;    py = h + margin;                  }
                case "center"     -> { px = (parentW - w) / 2f;      py = (parentH + h) / 2f;         }
                default           -> {
                    logger.warn("Panel '{}' unknown align '{}'", id, align);
                    px = margin;
                    py = parentH - margin;
                }
            }
        }

        if (parentPanel == null) {
            engine.getGuiNode().attachChild(node);
        } else {
            parentPanel.getNode().attachChild(node);
        }
        node.setLocalTranslation(px, py - h, 0f);

        // Рекурсивно «спускаемся» к вложенным панелям
        for (UIElement child : children) {
            if (child instanceof PanelElement) {
                ((PanelElement) child).repositionRecursively(0f, 0f);
            }
        }
    }



    /** Текущая ширина (PanelRenderer) */
    public float getCurrentWidth() {
        return renderer.getWidth();
    }

    /** Текущая высота (PanelRenderer) */
    public float getCurrentHeight() {
        return renderer.getHeight();
    }

    // Стандартные геттеры/сеттеры для margin/padding и т.п.
    public float getMargin() { return margin; }
    public float getPadding() { return padding; }
    public String getLayout() { return layout; }
    public List<UIElement> getChildren() { return children; }

    // Методы, используемые PanelProperties:
    void setMargin(float m)  { this.margin = m; }
    void setPadding(float p) { this.padding = p; }
    void setBgColor(ColorRGBA c) { this.bgColor = c; renderer.setBgColor(c); }
    public void setFixedWidth(float w)  { this.fixedWidth = w; this.autoWidth = false; }
    public void setFixedHeight(float h) { this.fixedHeight = h; this.autoHeight = false; }
    void setAutoWidth() { this.autoWidth = true; }
    void setAutoHeight(){ this.autoHeight = true; }
    void setAlign(String a) { this.align = a; }
    void setLayout(String l){ this.layout = l; }
    void setSpacing(float s){ this.spacing = s; }

    public boolean isAutoWidth() { return autoWidth; }
    public boolean isAutoHeight(){ return autoHeight; }
    public float getFixedWidth() { return fixedWidth; }
    public float getFixedHeight(){ return fixedHeight; }

    ColorRGBA getBgColor() { return bgColor; }

    public CalistaGameEngine getEngine() {
        return engine;
    }

    public float getSpacing() {
        return spacing;
    }

    public PanelRenderer getRenderer() {
        return renderer;
    }
}
