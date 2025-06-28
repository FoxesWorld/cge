package org.foxesworld.cge.modules.ui.novaUi.elements.progress;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

/**
 * Handles the visual rendering of the ProgressElement, including its border,
 * background, and fill bar.
 */
public class ProgressRenderer {

    private final Node node = new Node("ProgressRenderer");
    private final Geometry borderGeom;
    private final Geometry backgroundGeom;
    private final Geometry fillGeom;

    private float totalWidth, totalHeight, border, padding;

    public ProgressRenderer(AssetManager assetManager) {
        // Create materials once
        Material borderMat = createMaterial(assetManager);
        Material backgroundMat = createMaterial(assetManager);
        Material fillMat = createMaterial(assetManager);

        // Create geometries once
        this.borderGeom = new Geometry("Border", new Quad(1, 1));
        this.backgroundGeom = new Geometry("Background", new Quad(1, 1));
        this.fillGeom = new Geometry("Fill", new Quad(1, 1));

        borderGeom.setMaterial(borderMat);
        backgroundGeom.setMaterial(backgroundMat);
        fillGeom.setMaterial(fillMat);

        // Z-ordering
        borderGeom.setLocalTranslation(0, 0, 0f);
        backgroundGeom.setLocalTranslation(0, 0, 0.1f);
        fillGeom.setLocalTranslation(0, 0, 0.2f);

        node.attachChild(borderGeom);
        node.attachChild(backgroundGeom);
        node.attachChild(fillGeom);
    }

    private Material createMaterial(AssetManager assetManager) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return mat;
    }

    public void setBorderColor(ColorRGBA color) {
        borderGeom.getMaterial().setColor("Color", color);
    }

    public void setBackgroundColor(ColorRGBA color) {
        backgroundGeom.getMaterial().setColor("Color", color);
    }

    public void setFillColor(ColorRGBA color) {
        fillGeom.getMaterial().setColor("Color", color);
    }

    /**
     * Updates the size of all components. Called when width, height, or border changes.
     */
    public void updateSize(float totalWidth, float totalHeight, float border, float padding) {
        this.totalWidth = totalWidth;
        this.totalHeight = totalHeight;
        this.border = border;
        this.padding = padding;

        // Update border
        ((Quad) borderGeom.getMesh()).updateGeometry(totalWidth, totalHeight);

        // Update background
        float bgWidth = Math.max(0, totalWidth - border * 2);
        float bgHeight = Math.max(0, totalHeight - border * 2);
        ((Quad) backgroundGeom.getMesh()).updateGeometry(bgWidth, bgHeight);
        backgroundGeom.setLocalTranslation(border, border, 0.1f);
    }

    /**
     * Updates only the fill bar. Called every frame during animation.
     */
    public void updateFill(float progress) {
        float fillableWidth = Math.max(0, totalWidth - (border + padding) * 2);
        float fillableHeight = Math.max(0, totalHeight - (border + padding) * 2);
        float currentFillWidth = fillableWidth * progress;

        ((Quad) fillGeom.getMesh()).updateGeometry(currentFillWidth, fillableHeight);
        fillGeom.setLocalTranslation(border + padding, border + padding, 0.2f);
    }

    public Node getNode() {
        return node;
    }
}