package org.foxesworld.cge.tmp.menu.components;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.Spatial.CullHint;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.RoundedQuad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptive UI Panel with relative sizing, DPI scaling, anchor positioning and layout control.
 * Теперь поддерживает clipping (обрезание) контента, выходящего за пределы области контента.
 */
public class Panel extends UIComponent implements InteractiveComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(Panel.class);

    public enum Anchor {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER_LEFT,
        CENTER,
        CENTER_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT,
        // New anchors
        LEFT_CENTER,
        RIGHT_CENTER,
        CENTER_TOP,
        CENTER_BOTTOM
    }

    public enum LayoutDirection {
        VERTICAL, HORIZONTAL
    }

    public enum Alignment {
        START, CENTER, END
    }

    // ==== FIELDS ====

    private final SimpleApplication app;
    private final Geometry background;
    private final Style style;
    private final List<UIComponent> children = new ArrayList<>();

    private float width = 128f, height = 64f;
    private float padding, spacing;
    private LayoutDirection layout = LayoutDirection.VERTICAL;
    private Alignment alignment = Alignment.START;
    private boolean autoSize = false;

    // Relative positioning strings (e.g. "50%", "200")
    private String relX, relY, relW, relH;
    private Anchor anchor = Anchor.TOP_LEFT;

    private int lastScreenW = -1, lastScreenH = -1;
    private boolean dirtyLayout = true;

    // Minimum hard limits (px)
    private static final float MIN_PANEL_WIDTH = 32f;
    private static final float MIN_PANEL_HEIGHT = 32f;

    // ==== CONSTRUCTOR ====

    public Panel(String id, SimpleApplication app, Style style, float padding, float spacing) {
        super(id);
        this.app = app;
        this.style = style != null ? style : Style.getDefaultStyle();
        this.padding = padding;
        this.spacing = spacing;

        Material bgMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", this.style.backgroundColor);
        bgMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

        this.background = new Geometry("PanelBackground");
        this.background.setMaterial(bgMat);
        this.node.attachChild(background);

        app.getGuiNode().attachChild(node);

        LOGGER.debug("Panel '{}' created", id);
    }

    // ==== CONFIGURATION SETTERS ====

    public void setLayoutDirection(LayoutDirection layout) {
        this.layout = layout;
        dirtyLayout = true;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment;
        dirtyLayout = true;
    }

    public void setAutoSize(boolean autoSize) {
        this.autoSize = autoSize;
        dirtyLayout = true;
    }

    public void setRelativeBounds(String x, String y, String w, String h) {
        this.relX = x;
        this.relY = y;
        this.relW = w;
        this.relH = h;
        dirtyLayout = true;
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
        dirtyLayout = true;
    }

    @Override
    public void setDpiScale(float scale) {
        super.setDpiScale(scale);
        dirtyLayout = true;
    }

    public void addComponent(UIComponent component) {
        if (component == null) return;
        children.add(component);
        node.attachChild(component.getNode());
        dirtyLayout = true;
    }

    // ==== UPDATE ====

    @Override
    public void update(float tpf) {
        Camera cam = app.getGuiViewPort().getCamera();
        int sw = cam.getWidth(), sh = cam.getHeight();

        if (sw != lastScreenW || sh != lastScreenH) {
            lastScreenW = sw;
            lastScreenH = sh;
            dirtyLayout = true;
        }

        if (dirtyLayout) {
            applyBounds(sw, sh);
            relayoutAndClip();
            dirtyLayout = false;
        }

        for (UIComponent c : children) {
            c.update(tpf);
        }
    }

    // ==== LAYOUT & POSITIONING ====

    private void applyBounds(int sw, int sh) {
        float x = relX != null ? parseRelative(relX, sw) : 0;
        float y = relY != null ? parseRelative(relY, sh) : 0;

        // If relW / relH not provided, use computed minimal size (based on children, padding, spacing).
        // Otherwise parse relative and scale by dpi.
        float computedW = relW != null ? parseRelative(relW, sw) * dpiScale : -1f;
        float computedH = relH != null ? parseRelative(relH, sh) * dpiScale : -1f;

        if (computedW <= 0f || computedH <= 0f) {
            Vector2f minimal = computeMinimalSize();
            if (computedW <= 0f) computedW = minimal.x;
            if (computedH <= 0f) computedH = minimal.y;
        }

        // Ensure hard minimums
        computedW = Math.max(computedW, MIN_PANEL_WIDTH * dpiScale);
        computedH = Math.max(computedH, MIN_PANEL_HEIGHT * dpiScale);

        if (!autoSize) setSize(computedW, computedH);

        applyAnchorPosition(x, y);
    }

    private Vector2f computeMinimalSize() {
        float contentMinW = 0f;
        float contentMinH = 0f;

        int n = children.size();
        if (n == 0) {
            contentMinW = 0f;
            contentMinH = 0f;
        } else {
            if (layout == LayoutDirection.VERTICAL) {
                float maxChildW = 0f;
                float sumChildH = 0f;
                for (UIComponent c : children) {
                    float cw = c.getWidth();
                    float ch = c.getHeight();
                    maxChildW = Math.max(maxChildW, cw);
                    sumChildH += ch;
                }
                float totalSpacing = Math.max(0, n - 1) * spacing;
                contentMinW = maxChildW;
                contentMinH = sumChildH + totalSpacing;
            } else {
                float sumChildW = 0f;
                float maxChildH = 0f;
                for (UIComponent c : children) {
                    float cw = c.getWidth();
                    float ch = c.getHeight();
                    sumChildW += cw;
                    maxChildH = Math.max(maxChildH, ch);
                }
                float totalSpacing = Math.max(0, n - 1) * spacing;
                contentMinW = sumChildW + totalSpacing;
                contentMinH = maxChildH;
            }
        }

        float minW = padding * 2f + contentMinW;
        float minH = padding * 2f + contentMinH;

        return new Vector2f(minW * dpiScale, minH * dpiScale);
    }

    private void applyAnchorPosition(float x, float y) {
        Camera cam = app.getGuiViewPort().getCamera();
        float sw = cam.getWidth();
        float sh = cam.getHeight();

        float finalX = switch (anchor) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT, LEFT_CENTER -> 0;
            case TOP_CENTER, CENTER, BOTTOM_CENTER, CENTER_TOP, CENTER_BOTTOM -> (sw - width) / 2f;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT, RIGHT_CENTER -> sw - width;
            default -> 0;
        };

        float finalY = switch (anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER_TOP -> sh - height;
            case CENTER_LEFT, CENTER, CENTER_RIGHT, LEFT_CENTER, RIGHT_CENTER -> (sh - height) / 2f;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT, CENTER_BOTTOM -> 0;
            default -> 0;
        };

        node.setLocalTranslation(finalX + x, finalY + y, 0);
    }

    private float parseRelative(String val, float total) {
        val = val.trim();
        if (val.endsWith("%")) {
            try {
                float pct = Float.parseFloat(val.substring(0, val.length() - 1));
                return total * pct / 100f;
            } catch (NumberFormatException e) {
                LOGGER.error("Invalid percentage '{}'", val);
                return 0;
            }
        }
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid number '{}'", val);
            return 0;
        }
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        background.setMesh(new RoundedQuad(width, height, style.cornerRadius, 64));
        background.setLocalTranslation(width / 2f, height / 2f, -1f);
    }

    /**
     * Основной метод релэйаута и применения clipping.
     * Для компонентов, реализующих Panel.Clippable, мы передаём прямоугольник отсечения
     * в локальных координатах компонента.
     */
    private void relayoutAndClip() {
        float contentW = Math.max(0f, width - padding * 2f);
        float contentH = Math.max(0f, height - padding * 2f);

        // content origin in panel-local coords: bottom-left corner of content area
        float contentLeft = padding;
        float contentBottom = padding;
        float contentRight = contentLeft + contentW;
        float contentTop = contentBottom + contentH;

        float posX = contentLeft;
        float posYTop = contentTop; // Y coordinate from top for stacking

        float maxX = contentLeft;
        float maxY = contentBottom;

        for (UIComponent child : children) {
            float cw = layout == LayoutDirection.VERTICAL ? contentW : child.getWidth();
            float ch = child.getHeight();
            child.setSize(cw, ch);

            // For consistency: we'll place child's local translation as bottom-left coordinates.
            float childLocalX = posX;
            float childLocalBottomY;

            if (layout == LayoutDirection.VERTICAL) {
                // stack top -> bottom
                childLocalBottomY = posYTop - ch;
                child.getNode().setLocalTranslation(childLocalX, childLocalBottomY, 0);
                posYTop = childLocalBottomY - spacing;
            } else { // HORIZONTAL layout: left -> right
                float childY;
                switch (alignment) {
                    case START -> childY = contentTop - ch; // top inside content
                    case CENTER -> childY = contentBottom + (contentH - ch) / 2f; // center vertically
                    case END -> childY = contentBottom; // bottom inside content
                    default -> childY = contentBottom;
                }
                childLocalBottomY = childY;
                child.getNode().setLocalTranslation(childLocalX, childLocalBottomY, 0);
                posX += cw + spacing;
            }

            // Now compute child's rect in panel-local coords (bottom-left based)
            float childRectLeft = childLocalX;
            float childRectRight = childLocalX + cw;
            float childRectBottom = childLocalBottomY;
            float childRectTop = childLocalBottomY + ch;

            // Compute overlap
            float overlapLeft = Math.max(contentLeft, childRectLeft);
            float overlapRight = Math.min(contentRight, childRectRight);
            float overlapBottom = Math.max(contentBottom, childRectBottom);
            float overlapTop = Math.min(contentTop, childRectTop);

            float overlapW = overlapRight - overlapLeft;
            float overlapH = overlapTop - overlapBottom;

            Spatial childSpatial = child.getNode();

            if (overlapW <= 0f || overlapH <= 0f) {
                // полностью вне области контента — скрываем (для производительности)
                childSpatial.setCullHint(CullHint.Always);

                if (child instanceof Clippable) {
                    ((Clippable) child).clearClip();
                }
            } else {
                // частично или полностью внутри — показываем
                childSpatial.setCullHint(CullHint.Inherit);

                if (child instanceof Clippable) {
                    // overlap coordinates are in panel-local; convert to child's local coords:
                    float clipXInChild = overlapLeft - childRectLeft;   // >= 0
                    float clipYInChild = overlapBottom - childRectBottom; // >= 0
                    ((Clippable) child).setClipRect(clipXInChild, clipYInChild, overlapW, overlapH);
                }
            }

            maxX = Math.max(maxX, childRectRight + spacing);
            maxY = Math.max(maxY, childRectTop + spacing);
        }

        // авторазмер (если включён)
        if (autoSize) {
            if (layout == LayoutDirection.VERTICAL) {
                float usedHeight = (contentTop - posYTop) + padding;
                float maxChildW = 0f;
                for (UIComponent c : children) maxChildW = Math.max(maxChildW, c.getWidth());
                float newWidth = padding * 2f + maxChildW;
                float newHeight = Math.max(usedHeight, MIN_PANEL_HEIGHT * dpiScale);
                setSize(Math.max(newWidth, MIN_PANEL_WIDTH * dpiScale), newHeight);
            } else {
                float newWidth = maxX + padding;
                float maxChildH = 0f;
                for (UIComponent c : children) maxChildH = Math.max(maxChildH, c.getHeight());
                float newHeight = padding * 2f + maxChildH;
                setSize(Math.max(newWidth, MIN_PANEL_WIDTH * dpiScale), Math.max(newHeight, MIN_PANEL_HEIGHT * dpiScale));
            }
        }
    }

    // ==== INTERACTIVE METHODS ====
    @Override public boolean intersects(Vector2f pos) {
        Vector2f world = new Vector2f(node.getWorldTranslation().x, node.getWorldTranslation().y);
        float wx = world.x;
        float wy = world.y;
        return pos.x >= wx && pos.x <= wx + width && pos.y >= wy && pos.y <= wy + height;
    }
    @Override public void setHovered(boolean hovered) {}
    @Override public void handleMousePress(Vector2f cursor) {}
    @Override public void handleMouseDrag(Vector2f cursor) {}
    @Override public void handleMouseRelease() {}
    @Override public void setActive(boolean active) {}

    // ==== GETTERS ====
    @Override public float getWidth() { return width; }
    @Override public float getHeight() { return height; }

    // ==== STYLE RECORD ====
    public record Style(ColorRGBA backgroundColor, float cornerRadius) {
        public static Style getDefaultStyle() {
            return new Style(new ColorRGBA(0.05f, 0.05f, 0.15f, 0.7f), 20f);
        }
    }

    // ==== CLIPPING PROTOCOL ====
    /**
     * Интерфейс, который могут реализовать компоненты, умеющие самостоятельно обрезаться по прямоугольнику.
     * Panel будет вызывать setClipRect(clipX, clipY, clipW, clipH) — координаты даны в локальных координатах компонента.
     */
    public interface Clippable {
        /**
         * Установить область видимости (в локальных координатах компонента).
         * @param x левый нижний угол в локальных координатах компонента
         * @param y нижний угол в локальных координатах компонента
         * @param w ширина области
         * @param h высота области
         */
        void setClipRect(float x, float y, float w, float h);

        /**
         * Очистить (убрать) область обрезки — вернуть поведение по умолчанию.
         */
        void clearClip();
    }
}