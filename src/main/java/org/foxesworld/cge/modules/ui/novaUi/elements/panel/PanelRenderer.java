package org.foxesworld.cge.modules.ui.novaUi.elements.panel;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

/**
 * Handles the visual representation (rendering) of a PanelElement.
 * It is responsible only for drawing the background quad.
 */
public class PanelRenderer {
    private final Node node = new Node("PanelRenderer");
    private final Geometry backgroundGeom;
    private final Material backgroundMat;

    private float width = 0f;
    private float height = 0f;

    public PanelRenderer(AssetManager assetManager) {
        backgroundMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        backgroundMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        backgroundMat.setColor("Color", new ColorRGBA(0, 0, 0, 0)); // Default to transparent

        backgroundGeom = new Geometry("PanelBackground", new Quad(1, 1));
        backgroundGeom.setMaterial(backgroundMat);
        node.attachChild(backgroundGeom);
    }

    public void updateGeometry(float newWidth, float newHeight) {
        if (this.width != newWidth || this.height != newHeight) {
            this.width = newWidth;
            this.height = newHeight;
            ((Quad) backgroundGeom.getMesh()).updateGeometry(newWidth, newHeight);
            //backgroundGeom.setLocalBound(null); // Force bound recalculation
        }
    }

    public void setBackgroundColor(ColorRGBA color) {
        backgroundMat.setColor("Color", color);
    }

    public Node getNode() {
        return node;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }
}