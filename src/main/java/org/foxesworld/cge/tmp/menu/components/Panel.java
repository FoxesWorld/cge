package org.foxesworld.cge.tmp.menu.components;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.RoundedQuad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptive UI Panel with relative sizing, DPI scaling, anchor positioning and layout control.
 * Supports clipping for content outside bounds.
 */
public class Panel extends UIComponent implements InteractiveComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(Panel.class);

    public enum Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    public enum LayoutDirection { VERTICAL, HORIZONTAL }
    public enum Alignment { START, CENTER, END }

    private final SimpleApplication app;
    private final Geometry background;
    private final Style style;
    private final List<UIComponent> children = new ArrayList<>();

    private float padding, spacing;
    private LayoutDirection layout = LayoutDirection.VERTICAL;
    private Alignment alignment = Alignment.START;
    private boolean autoSize = false;

    // Relative positioning
    private String relX, relY, relW, relH;
    private Anchor anchor = Anchor.TOP_LEFT;
    private int zIndex = 0;

    private int lastScreenW = -1, lastScreenH = -1;
    private boolean dirtyLayout = true;

    private static final float MIN_W = 32f;
    private static final float MIN_H = 32f;

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
        attachChild(background);

        app.getGuiNode().attachChild(this);
    }

    // === Config ===
    public void setLayoutDirection(LayoutDirection layout) { this.layout = layout; markDirty(); }
    public void setAlignment(Alignment alignment) { this.alignment = alignment; markDirty(); }
    public void setAutoSize(boolean autoSize) { this.autoSize = autoSize; markDirty(); }
    public void setAnchor(Anchor anchor) { this.anchor = anchor; markDirty(); }

    public void setRelativeBounds(String x, String y, String w, String h, int zIndex) {
        this.relX = x; this.relY = y;
        this.relW = w; this.relH = h;
        this.zIndex = zIndex;
        markDirty();
    }

    public void addComponent(UIComponent component) {
        if (component != null) {
            children.add(component);
            attachChild(component.getNode());
            markDirty();
        }
    }

    private void markDirty() { dirtyLayout = true; }

    @Override
    public void update(float tpf) {
        Camera cam = app.getGuiViewPort().getCamera();
        if (cam.getWidth() != lastScreenW || cam.getHeight() != lastScreenH) {
            lastScreenW = cam.getWidth();
            lastScreenH = cam.getHeight();
            markDirty();
        }
        if (dirtyLayout) {
            applyBounds(lastScreenW, lastScreenH);
            relayoutAndClip();
            dirtyLayout = false;
        }
        children.forEach(c -> c.update(tpf));
    }

    // === Layout ===
    private void applyBounds(int sw, int sh) {
        float x = parseRelative(relX, sw);
        float y = parseRelative(relY, sh);

        float w = (relW != null ? parseRelative(relW, sw) * dpiScale : -1f);
        float h = (relH != null ? parseRelative(relH, sh) * dpiScale : -1f);

        if (w <= 0 || h <= 0) {
            Vector2f minSize = computeMinimalSize();
            if (w <= 0) w = minSize.x;
            if (h <= 0) h = minSize.y;
        }

        w = Math.max(w, MIN_W * dpiScale);
        h = Math.max(h, MIN_H * dpiScale);

        if (!autoSize) setSize(w, h);
        applyAnchorPosition(x, y);
    }

    private Vector2f computeMinimalSize() {
        float contentW = 0, contentH = 0;
        if (layout == LayoutDirection.VERTICAL) {
            float maxW = 0, sumH = 0;
            for (UIComponent c : children) {
                maxW = Math.max(maxW, c.getWidth());
                sumH += c.getHeight();
            }
            contentW = maxW;
            contentH = sumH + spacing * (Math.max(0, children.size() - 1));
        } else {
            float sumW = 0, maxH = 0;
            for (UIComponent c : children) {
                sumW += c.getWidth();
                maxH = Math.max(maxH, c.getHeight());
            }
            contentW = sumW + spacing * (Math.max(0, children.size() - 1));
            contentH = maxH;
        }
        return new Vector2f((contentW + padding * 2) * dpiScale, (contentH + padding * 2) * dpiScale);
    }

    private void applyAnchorPosition(float offsetX, float offsetY) {
        Camera cam = app.getGuiViewPort().getCamera();
        float sw = cam.getWidth(), sh = cam.getHeight();

        float baseX = switch (anchor) {
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> (sw - getWidth()) / 2f;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> sw - getWidth();
            default -> 0;
        };
        float baseY = switch (anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> sh - getHeight();
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> (sh - getHeight()) / 2f;
            default -> 0;
        };

        setLocalTranslation(baseX + offsetX, baseY + offsetY, zIndex);
    }

    private float parseRelative(String val, float total) {
        if (val == null) return 0;
        val = val.trim();
        try {
            if (val.endsWith("%")) return total * Float.parseFloat(val.replace("%", "")) / 100f;
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid value '{}'", val);
            return 0;
        }
    }

    public void setSize(float w, float h) {
        setWidth(w);
        setHeight(h);
        background.setMesh(new RoundedQuad(w, h, style.cornerRadius, 64));
        background.setLocalTranslation(w / 2f, h / 2f, -1f);
    }

    private void relayoutAndClip() {
        float contentW = getWidth() - padding * 2;
        float contentH = getHeight() - padding * 2;

        float posX = padding;
        float posY = getHeight() - padding;

        for (UIComponent child : children) {
            float cw = (layout == LayoutDirection.VERTICAL) ? contentW : child.getWidth();
            float ch = child.getHeight();
            child.setSize(cw, ch);

            if (layout == LayoutDirection.VERTICAL) {
                posY -= ch;
                child.getNode().setLocalTranslation(posX, posY, 0);
                posY -= spacing;
            } else {
                float childY = switch (alignment) {
                    case CENTER -> padding + (contentH - ch) / 2f;
                    case END -> padding;
                    default -> getHeight() - padding - ch;
                };
                child.getNode().setLocalTranslation(posX, childY, 0);
                posX += cw + spacing;
            }

            if (child instanceof Clippable clip) {
                clip.setClipRect(0, 0, Math.max(0, cw), Math.max(0, ch));
            }
        }
    }

    @Override public boolean intersects(Vector2f pos) {
        Vector2f wp = new Vector2f(getWorldTranslation().x, getWorldTranslation().y);
        return pos.x >= wp.x && pos.x <= wp.x + getWidth() &&
                pos.y >= wp.y && pos.y <= wp.y + getHeight();
    }
    @Override public void setHovered(boolean hovered) {}
    @Override public void handleMousePress(Vector2f cursor) {}
    @Override public void handleMouseDrag(Vector2f cursor) {}
    @Override public void handleMouseRelease() {}
    @Override public void setActive(boolean active) {}

    public record Style(ColorRGBA backgroundColor, float cornerRadius) {
        public static Style getDefaultStyle() {
            return new Style(new ColorRGBA(0.05f, 0.05f, 0.15f, 0.7f), 20f);
        }
    }

    public interface Clippable {
        void setClipRect(float x, float y, float w, float h);
        void clearClip();
    }
}
