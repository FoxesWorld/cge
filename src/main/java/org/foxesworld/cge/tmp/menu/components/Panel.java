package org.foxesworld.cge.tmp.menu.components;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * UI Panel with relative sizing, anchor positioning, dpi scaling, and vertical layout.
 */
public class Panel implements MenuComponent, InteractiveComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(Panel.class);

    private final String id;
    private final Node panelNode = new Node();
    private final Geometry background;
    private final Style style;
    private final List<MenuComponent> children = new ArrayList<>();
    private final SimpleApplication app;

    // Layout
    private float width, height;
    private float padding, spacing;
    private float dpiScale = 1f;
    private float nextY = 0;

    // Relative bounds
    private String relX = null, relY = null, relW = null, relH = null;
    private Anchor anchor = Anchor.TOP_LEFT;

    private int lastScreenW = -1, lastScreenH = -1;
    private boolean dirtyLayout = true;

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


    public Panel(String id, SimpleApplication app, Style style, float padding, float spacing) {
        this.id = id;
        this.app = app;
        this.style = style;
        this.padding = padding;
        this.spacing = spacing;

        Material bgMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", style.backgroundColor);
        bgMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

        this.background = new Geometry("PanelBackground");
        this.background.setMaterial(bgMat);
        panelNode.attachChild(background);

        app.getGuiNode().attachChild(panelNode);

        LOGGER.debug("Panel '{}' created", id);
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

    public void setDpiScale(float scale) {
        this.dpiScale = scale;
        dirtyLayout = true;
    }

    public void addComponent(MenuComponent component) {
        if (component == null) return;
        children.add(component);
        panelNode.attachChild(component.getNode());
        dirtyLayout = true;
    }

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

        for (MenuComponent c : children) c.update(tpf);
    }

    private void applyBounds(int sw, int sh) {
        float x = relX != null ? parseRelative(relX, sw) : 0;
        float y = relY != null ? parseRelative(relY, sh) : 0;
        float w = relW != null ? parseRelative(relW, sw) : sw;
        float h = relH != null ? parseRelative(relH, sh) : sh;

        w *= dpiScale;
        h *= dpiScale;

        setSize(w, h);
        applyAnchorPosition(x, y);
    }

    private void applyAnchorPosition(float x, float y) {
        Vector2f anchorOffset = getAnchorOffset(anchor, width, height);
        float posX = x + anchorOffset.x;
        float posY = y + anchorOffset.y;
        panelNode.setLocalTranslation(posX, posY, 0);
    }

    private Vector2f getAnchorOffset(Anchor anchor, float w, float h) {
        float xMul = 0f;
        float yMul = 0f;

        switch (anchor) {
            case TOP_LEFT       -> { xMul = 0f;   yMul = 0f; }
            case TOP_CENTER     -> { xMul = -0.5f; yMul = 0f; }
            case TOP_RIGHT      -> { xMul = -1f;  yMul = 0f; }

            case CENTER_LEFT    -> { xMul = 0f;   yMul = -0.5f; }
            case CENTER         -> { xMul = -0.5f; yMul = -0.5f; }
            case CENTER_RIGHT   -> { xMul = -1f;  yMul = -0.5f; }

            case BOTTOM_LEFT    -> { xMul = 0f;   yMul = -1f; }
            case BOTTOM_CENTER  -> { xMul = -0.5f; yMul = -1f; }
            case BOTTOM_RIGHT   -> { xMul = -1f;  yMul = -1f; }
        }

        return new Vector2f(w * xMul, h * yMul);
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
        this.nextY = height - padding;

        background.setMesh(new RoundedQuad(width, height, style.cornerRadius, 64));
        background.setLocalTranslation(width / 2f, height / 2f, -1f);
    }

    private void relayout() {
        nextY = height - padding;
        for (MenuComponent child : children) {
            float cw = width - padding * 2;
            float ch = child.getHeight(); // предполагается, что уже масштабировано один раз
            child.setSize(cw, ch); // НЕ умножаем повторно!
            child.getNode().setLocalTranslation(padding, nextY - ch, 0);
            nextY -= (ch + spacing);
        }
    }



    @Override public Node getNode() { return panelNode; }
    @Override public float getWidth() { return width; }
    @Override public float getHeight() { return height; }
    @Override public boolean intersects(Vector2f pos) { return false; }
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
}
