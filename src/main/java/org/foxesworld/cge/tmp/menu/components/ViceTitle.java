package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;

public class ViceTitle implements MenuComponent {
    private final Node titleNode;
    private final BitmapText label;

    public ViceTitle(AssetManager assetManager, String text, String fontPath) {
        this.titleNode = new Node("ViceTitle: " + text);
        this.label = new BitmapText(assetManager.loadFont(fontPath));
        label.setText(text);
        label.setColor(ColorRGBA.White);
        titleNode.attachChild(label);
    }

    public void setSize(float size) {
        label.setSize(size);
    }

    public void setPosition(float x, float y) {
        float textWidth = label.getLineWidth();
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
}