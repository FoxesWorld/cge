package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

public class ViceSlider implements MenuComponent {
    private final Node sliderNode;
    private final Geometry barBg, barFill;
    private float width, height = 8f;
    private float value; // 0.0 to 1.0
    private final String bind;

    public ViceSlider(AssetManager assetManager, String text, String fontPath, float initialValue, String bind) {
        this.value = initialValue;
        this.bind = bind;
        this.sliderNode = new Node("ViceSlider: " + text);

        BitmapText label = new BitmapText(assetManager.loadFont(fontPath));
        label.setText(text.toUpperCase());
        label.setColor(ColorRGBA.White);
        label.setSize(24f);
        label.setLocalTranslation(0, height + 28, 0);

        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", new ColorRGBA(0.1f, 0.1f, 0.1f, 0.7f));
        bgMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        this.barBg = new Geometry("SliderBg", new Quad(1, height));
        barBg.setMaterial(bgMat);

        Material fillMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        fillMat.setColor("Color", ColorRGBA.White);
        this.barFill = new Geometry("SliderFill", new Quad(1, height));
        barFill.setMaterial(fillMat);

        sliderNode.attachChild(label);
        sliderNode.attachChild(barBg);
        sliderNode.attachChild(barFill);
    }

    public void setSize(float width) { this.width = width; barBg.setLocalScale(width, 1, 1); updateFill(); }
    public void setPosition(float x, float y) { sliderNode.setLocalTranslation(x, y, 0); }

    private void updateFill() {
        barFill.setLocalScale(width * value, 1, 1);
        barFill.setLocalTranslation(0,0, 0.1f);
    }

    public void setValue(float newValue) {
        this.value = Math.max(0f, Math.min(1f, newValue));
        updateFill();
        System.out.println("Setting '" + bind + "' changed to: " + this.value);
    }

    public boolean intersects(Vector2f point) {
        Vector2f pos = new Vector2f(sliderNode.getLocalTranslation().x, sliderNode.getLocalTranslation().y);
        return point.x >= pos.x && point.x <= pos.x + width && point.y >= pos.y && point.y <= pos.y + height;
    }

    public void handleDrag(Vector2f cursorPosition) {
        float relativeX = cursorPosition.x - sliderNode.getLocalTranslation().x;
        setValue(relativeX / width);
    }

    public Node getNode() { return sliderNode; }

    @Override
    public void update(float tpf) {

    }
}