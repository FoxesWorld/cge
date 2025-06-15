package org.foxesworld.cge.modules.ui.novaUi.elements.panel;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;

public class PanelRenderer {
    private final CalistaGameEngine engine;
    private final PanelElement panel;
    private Geometry bgGeom;
    private float currentWidth;
    private float currentHeight;

    public PanelRenderer(CalistaGameEngine engine, PanelElement panel) {
        this.engine = engine;
        this.panel = panel;
    }

    public void updateGeometry(float width, float height) {
        if (bgGeom == null) {
            createBackground(width, height);
        } else {
            bgGeom.setMesh(new Quad(width, height));
        }
        bgGeom.setLocalTranslation(0f, 0f, 0f);
        currentWidth = width;
        currentHeight = height;
    }

    public void setSize(float width, float height) {
        currentWidth = width;
        currentHeight = height;
        if (bgGeom == null) {
            createBackground(width, height);
        } else {
            bgGeom.setMesh(new Quad(width, height));
        }
        bgGeom.setLocalTranslation(0f, 0f, 0f);
    }

    public float getWidth() {
        return currentWidth;
    }

    public float getHeight() {
        return currentHeight;
    }

    public void setBgColor(ColorRGBA color) {
        if (bgGeom != null) {
            bgGeom.getMaterial().setColor("Color", color);
        }
    }

    void createBackground(float width, float height) {
        Quad quad = new Quad(width, height);
        bgGeom = new Geometry("BG_" + panel.getId(), quad);
        Material mat = new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        mat.setColor("Color", panel.getBgColor());
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        bgGeom.setMaterial(mat);
        panel.getNode().attachChild(bgGeom);
    }
}
