package org.foxesworld.cge.tmp.menu.components;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.MenuComponent;
import org.foxesworld.cge.tmp.menu.components.utils.RoundedQuad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptive UI Panel with relative sizing, DPI scaling, anchor positioning and layout control.
 * Наследуется от UIComponent, расширяет базовые возможности.
 */
public class Panel extends UIComponent implements MenuComponent, InteractiveComponent {

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
        BOTTOM_RIGHT
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
    private final List<MenuComponent> children = new ArrayList<>();

    private float width, height;
    private float padding, spacing;
    private LayoutDirection layout = LayoutDirection.VERTICAL;
    private Alignment alignment = Alignment.START;
    private boolean autoSize = false;

    // Relative positioning strings (e.g. "50%", "200")
    private String relX, relY, relW, relH;
    private Anchor anchor = Anchor.TOP_LEFT;

    private int lastScreenW = -1, lastScreenH = -1;
    private boolean dirtyLayout = true;

    // ==== CONSTRUCTOR ====

    public Panel(String id, SimpleApplication app, Style style, float padding, float spacing) {
        super(id);
        this.app = app;
        this.style = style;
        this.padding = padding;
        this.spacing = spacing;

        Material bgMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", style.backgroundColor);
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

    public void addComponent(MenuComponent component) {
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
            relayout();
            dirtyLayout = false;
        }

        for (MenuComponent c : children) {
            c.update(tpf);
        }
    }

    // ==== LAYOUT & POSITIONING ====

    private void applyBounds(int sw, int sh) {
        float x = relX != null ? parseRelative(relX, sw) : 0;
        float y = relY != null ? parseRelative(relY, sh) : 0;
        float w = relW != null ? parseRelative(relW, sw) : sw;
        float h = relH != null ? parseRelative(relH, sh) : sh;

        w *= dpiScale;
        h *= dpiScale;

        if (!autoSize) setSize(w, h);

        applyAnchorPosition(x, y);
    }


    private void applyAnchorPosition(float x, float y) {
        Camera cam = app.getGuiViewPort().getCamera(); // Лучше использовать actual resolution GUI ViewPort'а
        float sw = cam.getWidth();
        float sh = cam.getHeight();

        float finalX = switch (anchor) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT      -> 0;
            case TOP_CENTER, CENTER, BOTTOM_CENTER       -> (sw - width) / 2f;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT   -> sw - width;
        };

        float finalY = switch (anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT         -> sh - height;
            case CENTER_LEFT, CENTER, CENTER_RIGHT       -> (sh - height) / 2f;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT-> 0;
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

    private void relayout() {
        float contentWidth = width - padding * 2;
        float contentHeight = height - padding * 2;

        float posX = padding;
        float posY = height - padding;

        float maxX = padding;
        float maxY = padding;

        for (MenuComponent child : children) {
            float cw = layout == LayoutDirection.VERTICAL ? contentWidth : child.getWidth();
            float ch = child.getHeight();

            child.setSize(cw, ch);

            if (layout == LayoutDirection.VERTICAL) {
                posY -= ch;
                child.getNode().setLocalTranslation(posX, posY, 0);
                posY -= spacing;
            } else {
                child.getNode().setLocalTranslation(posX, posY - ch, 0);
                posX += cw + spacing;
            }

            maxX = Math.max(maxX, posX);
            maxY = Math.max(maxY, height - posY + padding);
        }

        if (autoSize) {
            float newWidth = layout == LayoutDirection.VERTICAL ? width : maxX + padding;
            float newHeight = layout == LayoutDirection.VERTICAL ? height - posY + padding : height;
            setSize(newWidth, newHeight);
        }
    }

    // ==== INTERACTIVE METHODS ====

    @Override public boolean intersects(Vector2f pos) { return false; }
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
}