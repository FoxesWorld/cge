package org.foxesworld.cge.modules.ui.novaUi.elements.button;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

/**
 * Handles the visual rendering of the ButtonElement.
 * It combines a background quad and a text label.
 */
public class ButtonRenderer {

    private final Node node = new Node("ButtonRenderer");
    private final Geometry backgroundGeom;
    private final Material backgroundMat;
    private final BitmapText labelText;

    public ButtonRenderer(AssetManager assetManager) {
        // Setup background
        this.backgroundMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        backgroundMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        this.backgroundGeom = new Geometry("ButtonBackground", new Quad(1, 1));
        backgroundGeom.setMaterial(backgroundMat);

        // Setup text
        this.labelText = new BitmapText(assetManager.loadFont("Interface/Fonts/Default.fnt"));
        // Position text slightly in front of the background
        labelText.setLocalTranslation(0, 0, 0.1f);

        node.attachChild(backgroundGeom);
        node.attachChild(labelText);
    }

    public void setText(String text) {
        labelText.setText(text);
    }

    public void setFont(BitmapFont font) {
        //labelText.setFont(font);
    }

    public void setFontSize(float size) {
        labelText.setSize(size);
    }

    public void setTextColor(ColorRGBA color) {
        labelText.setColor(color);
    }

    public void setBackgroundColor(ColorRGBA color) {
        backgroundMat.setColor("Color", color);
    }

    /**
     * Updates the size of the background quad and repositions the text to be centered.
     */
    public void updateSize(float width, float height) {
        ((Quad) backgroundGeom.getMesh()).updateGeometry(width, height);
        //backgroundGeom.setLocalBound(null);

        // Center the text on the button
        float textX = (width - labelText.getLineWidth()) / 2f;
        float textY = (height + labelText.getLineHeight()) / 2f;
        labelText.setLocalTranslation(textX, textY, 0.1f);
    }

    public float getTextWidth() {
        return labelText.getLineWidth();
    }

    public float getTextHeight() {
        return labelText.getHeight();
    }

    public BitmapText getLabelText() {
        return labelText;
    }

    public Material getBackgroundMat() {
        return backgroundMat;
    }

    public Node getNode() {
        return node;
    }
}