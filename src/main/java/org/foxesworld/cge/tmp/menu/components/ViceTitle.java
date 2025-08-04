package org.foxesworld.cge.tmp.menu.components;

import com.atr.jme.font.shape.TrueTypeContainer;
import com.atr.jme.font.util.Style;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.core.io.TTFrenderer;
import org.foxesworld.cge.core.utils.ColorUtils;

public class ViceTitle implements InteractiveComponent, MenuComponent {
    private final Node titleNode;
    private final TrueTypeContainer ttc;
    private final TTFrenderer ttfRenderer;

    public ViceTitle(AssetManager assetManager, String text, int fontSize, String color, String fontPath) {
        this.titleNode = new Node("ViceTitle: " + text);
        ttfRenderer = new TTFrenderer(assetManager);
        ttfRenderer.genTTF("assets/Interface/fonts/Docker One.ttf", Style.Plain, fontSize);
        ttfRenderer.genTTC(ColorUtils.fromHexString(color), text);
        ttc = ttfRenderer.getTtc();
        titleNode.attachChild(ttc);
    }


    public void setPosition(float x, float y) {
        float textWidth = ttc.getWidth();
        titleNode.setLocalTranslation(x - textWidth / 2f, y, 0);
    }

    public Node getNode() {
        return titleNode;
    }

    @Override
    public void update(float tpf) {

    }

    @Override
    public boolean intersects(Vector2f cursor) {
        return false;
    }

    @Override
    public void setActive(boolean active) {
    }

    @Override
    public void setHovered(boolean hovered) {

    }

    @Override
    public void handleMousePress(Vector2f cursor) {

    }

    @Override
    public void handleMouseDrag(Vector2f cursor) {

    }

    @Override
    public void handleMouseRelease() {

    }

    @Override
    public float getHeight() {
        return ttfRenderer.getTtc().getTextHeight();
    }

    @Override
    public void setSize(float width, float height) {

    }

    @Override
    public float getWidth() {
        return ttfRenderer.getTtc().getTextWidth();
    }
}