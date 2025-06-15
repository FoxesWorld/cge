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

public class PanelElement extends AbstractUIElement {
    private static final Logger logger = LoggerFactory.getLogger(PanelElement.class);

    private final CalistaGameEngine engine;
    private final List<UIElement> children = new ArrayList<>();

    private float margin = 0f;
    private float padding = 0f;
    private ColorRGBA bgColor = new ColorRGBA(0f, 0f, 0f, 0.5f);

    private boolean autoWidth = true;
    private boolean autoHeight = true;
    private float fixedWidth = 0f;
    private float fixedHeight = 0f;

    private String align = "top-left";
    private String layout = "none";
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
        // Optional: uncomment if layout is dynamic
        // recomputeSizeAndRepositionChildren();

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
        properties.apply(key, value);
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

    public void recomputeSizeAndRepositionChildren() {
        float totalWidth = 0f;
        float maxChildHeight = 0f;
        for (UIElement child : children) {
            if (child instanceof AbstractUIElement abs) {
                float cw = abs.getWidth();
                float ch = abs.getHeight();
                totalWidth += cw;
                maxChildHeight = Math.max(maxChildHeight, ch);
            }
        }
        if ("none".equalsIgnoreCase(layout)) {
            float neededW = totalWidth + 2f * padding;
            float neededH = maxChildHeight + 2f * padding;
            if (autoWidth) {
                fixedWidth = neededW;
            }
            if (autoHeight) {
                fixedHeight = neededH;
            }
            float currentX = padding;
            for (UIElement child : children) {
                if (child instanceof AbstractUIElement abs) {
                    float cw = abs.getWidth();
                    float localX = currentX;
                    float localY = padding;
                    abs.getNode().setLocalTranslation(localX, localY, 0f);
                    currentX += cw + spacing;
                }
            }
        } else {
            layoutHelper.recomputeAndLayOut(totalWidth, maxChildHeight);
        }
        renderer.updateGeometry(getCurrentWidth(), getCurrentHeight());
    }

    public void recalcAndRepositionSelfAndAncestors() {
        PanelElement current = this;
        while (current != null) {
            current.recomputeSizeAndRepositionChildren();
            if (current.parentPanel != null) {
                current.repositionRecursively(
                        current.parentPanel.getCurrentWidth(),
                        current.parentPanel.getCurrentHeight()
                );
            } else {
                current.repositionRecursively(
                        engine.getCamera().getWidth(),
                        engine.getCamera().getHeight()
                );
            }
            current = current.parentPanel;
        }
    }

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
                case "top-left":
                    px = margin;
                    py = parentH - margin;
                    break;
                case "top-right":
                    px = parentW - margin - w;
                    py = parentH - margin;
                    break;
                case "bottom-left":
                    px = margin;
                    py = h + margin;
                    break;
                case "bottom-right":
                    px = parentW - margin - w;
                    py = h + margin;
                    break;
                case "center":
                    px = (parentW - w) / 2f;
                    py = (parentH + h) / 2f;
                    break;
                default:
                    logger.warn("Panel '{}' unknown align '{}'", id, align);
                    px = margin;
                    py = parentH - margin;
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

    public float getMargin() {
        return margin;
    }

    public float getPadding() {
        return padding;
    }

    public String getLayout() {
        return layout;
    }

    public List<UIElement> getChildren() {
        return children;
    }

    void setMargin(float m) {
        this.margin = m;
    }

    void setPadding(float p) {
        this.padding = p;
    }

    void setBgColor(ColorRGBA c) {
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

    void setAutoWidth() {
        this.autoWidth = true;
    }

    void setAutoHeight() {
        this.autoHeight = true;
    }

    void setAlign(String a) {
        this.align = a;
    }

    void setLayout(String l) {
        this.layout = l;
    }

    void setSpacing(float s) {
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

    ColorRGBA getBgColor() {
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
