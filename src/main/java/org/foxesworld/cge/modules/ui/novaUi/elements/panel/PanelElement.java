package org.foxesworld.cge.modules.ui.novaUi.elements.panel;

import com.jme3.math.ColorRGBA;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Float.parseFloat;

/**
 * PanelElement is a container UI element that can hold and layout child UI elements,
 * correctly accounting for both padding (internal space) and margin (external space).
 *
 * Margin and padding can be set as a single float or as two floats (horizontal, vertical).
 */
public class PanelElement extends AbstractUIElement {
    private static final Logger logger = LoggerFactory.getLogger(PanelElement.class);

    private final CalistaGameEngine engine;
    private final List<UIElement> children = new ArrayList<>();

    /** External margin of the panel (space outside the border). */
    private float marginH = 0f; // horizontal margin (left/right)
    private float marginV = 0f; // vertical margin (top/bottom)
    /** Internal padding of the panel (space between border and children). */
    private float paddingH = 0f; // horizontal padding (left/right)
    private float paddingV = 0f; // vertical padding (top/bottom)
    /** Background color of the panel. */
    private ColorRGBA bgColor = new ColorRGBA(0f, 0f, 0f, 0.5f);

    private boolean autoWidth = true;
    private boolean autoHeight = true;
    private float fixedWidth = 0f;
    private float fixedHeight = 0f;

    /** Alignment keyword or coordinates for positioning relative to parent. */
    private String align = "top-left";
    /** Layout mode: "none", "horizontal" or "vertical". */
    private String layout = "none";
    /** Spacing between children. */
    private float spacing = 0f;

    private final PanelRenderer renderer;
    private final PanelProperties properties;
    private final PanelLayout layoutHelper;

    public PanelElement(CalistaGameEngine engine, String id, PanelElement parent) {
        super();
        this.engine = engine;
        this.id = id;
        this.parentPanel = parent;
        this.node.setName("Panel_" + id);
        this.renderer = new PanelRenderer(engine, this);
        this.properties = new PanelProperties(this);
        this.layoutHelper = new PanelLayout(this, new MetricsRegistry());
        this.autoWidth = true;
        this.autoHeight = true;
        logger.debug("PanelElement created: id='{}'", id);
    }

    public void updateSelf(float tpf) {
        for (UIElement child : children) {
            if (child instanceof AbstractUIElement abs) {
                abs.updateSelf(tpf);
            }
        }
    }

    @Override
    public boolean hasOwnAlign() {
        return false;
    }

    @Override
    public String getOwnAlign() {
        return null;
    }

    @Override
    public void setProperty(String key, String value) {
        if ("margin".equalsIgnoreCase(key)) {
            setMargin(value);
        } else if ("padding".equalsIgnoreCase(key)) {
            setPadding(value);
        } else {
            properties.apply(key, value);
        }
        recalcAndRepositionSelfAndAncestors();
    }

    @Override
    public void setOnClickHandler(String methodName, Object eventHandlerTarget) {
        super.setOnClickHandler(methodName, eventHandlerTarget);
    }

    public void addChild(UIElement child) {
        children.add(child);
        if (child instanceof AbstractUIElement abs) {
            abs.setParentPanel(this);
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
     * Computes the size needed for the panel based on its children, padding and margin,
     * and positions children accordingly.
     * Padding is added to both sides, margin is handled externally in positioning.
     */
    public void recomputeSizeAndRepositionChildren() {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        // Располагаем детей согласно layout
        if ("none".equalsIgnoreCase(layout)) {
            float currentX = paddingH;
            for (UIElement child : children) {
                if (child instanceof AbstractUIElement abs) {
                    float cw = abs.getWidth();
                    float ch = abs.getHeight();

                    // Layout: горизонтально, сдвиг по X
                    abs.getNode().setLocalTranslation(currentX, paddingV, 0f);
                    currentX += cw + spacing;
                }
            }
        } else {
            layoutHelper.recomputeAndLayOut(0, 0); // пусть layoutHelper сам располагает
        }

        // 1. Найти границы дочерних элементов
        for (UIElement child : children) {
            if (child instanceof AbstractUIElement abs) {
                float x = abs.getNode().getLocalTranslation().x;
                float y = abs.getNode().getLocalTranslation().y;
                float w = abs.getWidth();
                float h = abs.getHeight();
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + w);
                maxY = Math.max(maxY, y + h);
            }
        }

        // 2. Если дети есть — вычисляем нужные размеры панели
        float neededW = (minX < Float.POSITIVE_INFINITY && maxX > Float.NEGATIVE_INFINITY)
                ? (maxX - minX) + 2f * paddingH
                : 2f * paddingH;
        float neededH = (minY < Float.POSITIVE_INFINITY && maxY > Float.NEGATIVE_INFINITY)
                ? (maxY - minY) + 2f * paddingV
                : 2f * paddingV;

        // 3. Если режим autoWidth/autoHeight — обновляем размеры панели
        if (autoWidth) {
            fixedWidth = neededW;
        }
        if (autoHeight) {
            fixedHeight = neededH;
        }

        // 4. Проверка на выход детей за пределы панели (ширина и высота)
        float panelWidth = getCurrentWidth();
        float panelHeight = getCurrentHeight();
        for (UIElement child : children) {
            if (child instanceof AbstractUIElement abs) {
                float x = abs.getNode().getLocalTranslation().x;
                float y = abs.getNode().getLocalTranslation().y;
                float w = abs.getWidth();
                float h = abs.getHeight();

                // Clamp по X
                if (x < paddingH) {
                    abs.getNode().setLocalTranslation(paddingH, y, 0f);
                }
                if (x + w > panelWidth - paddingH) {
                    float newX = Math.max(paddingH, panelWidth - paddingH - w);
                    abs.getNode().setLocalTranslation(newX, y, 0f);
                }
                // Clamp по Y
                if (y < paddingV) {
                    abs.getNode().setLocalTranslation(abs.getNode().getLocalTranslation().x, paddingV, 0f);
                }
                if (y + h > panelHeight - paddingV) {
                    float newY = Math.max(paddingV, panelHeight - paddingV - h);
                    abs.getNode().setLocalTranslation(abs.getNode().getLocalTranslation().x, newY, 0f);
                }
            }
        }

        renderer.updateGeometry(getCurrentWidth(), getCurrentHeight());
    }


    /**
     * Recalculates and repositions this panel and all ancestor panels.
     * Margin is handled when positioning the panel within its parent.
     */
    public void recalcAndRepositionSelfAndAncestors() {
        PanelElement current = this;
        while (current != null) {
            current.recomputeSizeAndRepositionChildren();
            float parentW, parentH;
            if (current.parentPanel != null) {
                parentW = current.parentPanel.getCurrentWidth();
                parentH = current.parentPanel.getCurrentHeight();
            } else {
                parentW = engine.getCamera().getWidth();
                parentH = engine.getCamera().getHeight();
            }
            current.repositionRecursively(parentW, parentH);
            current = current.parentPanel;
        }
    }

    /**
     * Positions the panel within its parent, taking margin into account.
     * Margin is the space outside the panel's border.
     */
    public void repositionRecursively(float parentW, float parentH) {
        if (parentPanel != null) {
            parentW = parentPanel.getCurrentWidth();
            parentH = parentPanel.getCurrentHeight();
        }
        float w = getCurrentWidth();
        float h = getCurrentHeight();
        float px;
        float py;
        String a = align.toLowerCase();
        float mh = marginH;
        float mv = marginV;
        if (a.contains(",")) {
            String[] coords = a.split(",");
            try {
                px = mh + parseFloat(coords[0].trim());
                py = parentH - mv - parseFloat(coords[1].trim());
            } catch (Exception ex) {
                logger.warn("Panel '{}' invalid align coords: {}", id, align);
                px = mh;
                py = parentH - mv;
            }
        } else {
            switch (a) {
                case "top-left":
                    px = mh;
                    py = parentH - mv;
                    break;
                case "top-right":
                    px = parentW - mh - w;
                    py = parentH - mv;
                    break;
                case "bottom-left":
                    px = mh;
                    py = h + mv;
                    break;
                case "bottom-right":
                    px = parentW - mh - w;
                    py = h + mv;
                    break;
                case "center":
                    px = (parentW - w) / 2f;
                    py = (parentH + h) / 2f;
                    break;
                default:
                    logger.warn("Panel '{}' unknown align '{}'", id, align);
                    px = mh;
                    py = parentH - mv;
                    break;
            }
        }
        node.setLocalTranslation(px, py - h, 0f);
        for (UIElement child : children) {
            if (child instanceof PanelElement) {
                ((PanelElement) child).repositionRecursively(0f, 0f);
            }
        }
    }

    public float getCurrentWidth() {
        float w = renderer.getWidth();
        return w > 0f ? w : fixedWidth;
    }

    public float getCurrentHeight() {
        float h = renderer.getHeight();
        return h > 0f ? h : fixedHeight;
    }

    @Override
    public float getWidth() {
        return getCurrentWidth();
    }

    @Override
    public float getHeight() {
        return getCurrentHeight();
    }

    /** Gets the horizontal margin (external space) of the panel. */
    public float getMarginH() {
        return marginH;
    }
    /** Gets the vertical margin (external space) of the panel. */
    public float getMarginV() {
        return marginV;
    }
    /** For backward compatibility. */
    public float getMargin() {
        return Math.max(marginH, marginV);
    }

    /** Gets the horizontal padding (internal space) of the panel. */
    public float getPaddingH() {
        return paddingH;
    }
    /** Gets the vertical padding (internal space) of the panel. */
    public float getPaddingV() {
        return paddingV;
    }
    /** For backward compatibility. */
    public float getPadding() {
        return Math.max(paddingH, paddingV);
    }

    /** Gets the layout mode. */
    public String getLayout() {
        return layout;
    }

    /** Gets the children of this panel. */
    public List<UIElement> getChildren() {
        return children;
    }

    /**
     * Sets margin with float: sets both horizontal and vertical to same value.
     */
    public void setMargin(float m) {
        this.marginH = m;
        this.marginV = m;
    }

    /**
     * Sets margin from a string. Accepts:
     *  - "10" (sets both horizontal and vertical to 10)
     *  - "10,20" (sets horizontal=10, vertical=20)
     */
    public void setMargin(String m) {
        if (m == null) return;
        String[] arr = m.split(",");
        try {
            if (arr.length == 1) {
                float v = parseFloat(arr[0].trim());
                setMargin(v);
            } else if (arr.length >= 2) {
                marginH = parseFloat(arr[0].trim());
                marginV = parseFloat(arr[1].trim());
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid margin value '{}': {}", m, e.toString());
        }
    }

    /**
     * Sets padding with float: sets both horizontal and vertical to same value.
     */
    public void setPadding(float p) {
        this.paddingH = p;
        this.paddingV = p;
    }

    /**
     * Sets padding from a string. Accepts:
     *  - "10" (sets both horizontal and vertical to 10)
     *  - "10,20" (sets horizontal=10, vertical=20)
     */
    public void setPadding(String p) {
        if (p == null) return;
        String[] arr = p.split(",");
        try {
            if (arr.length == 1) {
                float v = parseFloat(arr[0].trim());
                setPadding(v);
            } else if (arr.length >= 2) {
                paddingH = parseFloat(arr[0].trim());
                paddingV = parseFloat(arr[1].trim());
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid padding value '{}': {}", p, e.toString());
        }
    }

    public void setBgColor(ColorRGBA c) {
        this.bgColor = c;
        renderer.setBgColor(c);
    }

    public void setFixedWidth(float w) {
        this.fixedWidth = w;
        this.autoWidth = false;
    }

    public void setFixedHeight(float h) {
        this.fixedHeight = h;
        this.autoHeight = false;
    }

    public void setAutoWidth() {
        this.autoWidth = true;
    }

    public void setAutoHeight() {
        this.autoHeight = true;
    }

    public void setAlign(String a) {
        this.align = a;
    }

    public void setLayout(String l) {
        this.layout = l;
    }

    public void setSpacing(float s) {
        this.spacing = s;
    }

    public boolean isAutoWidth() {
        return autoWidth;
    }

    public boolean isAutoHeight() {
        return autoHeight;
    }

    public float getFixedWidth() {
        return fixedWidth;
    }

    public float getFixedHeight() {
        return fixedHeight;
    }

    public ColorRGBA getBgColor() {
        return bgColor;
    }

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