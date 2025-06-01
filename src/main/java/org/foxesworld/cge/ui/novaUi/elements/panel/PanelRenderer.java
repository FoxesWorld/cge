package org.foxesworld.cge.ui.novaUi.elements.panel;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;

/**
 * PanelRenderer — лишь создаёт и обновляет Geometry (Quad) для фона панели.
 * PanelElement вызывает метод setSize() при пересчёте, а затем layoutHelper
 * выставляет Quad нужного размера.
 */
public class PanelRenderer {
    private final CalistaGameEngine engine;
    private final PanelElement panel;
    private Geometry bgGeom;
    private float currentWidth  = 0f;
    private float currentHeight = 0f;

    public PanelRenderer(CalistaGameEngine engine, PanelElement panel) {
        this.engine = engine;
        this.panel = panel;
    }

    public void updateGeometry() {
        if (bgGeom == null) {
            buildBackgroundGeom();
        }
        // Quad уже создан в setSize(), здесь достаточно просто пересоздать mesh:
        bgGeom.setMesh(new Quad(currentWidth, currentHeight));
        bgGeom.setLocalTranslation(0f, 0f, 0f);
    }

    public void setSize(float w, float h) {
        this.currentWidth = w;
        this.currentHeight = h;
        if (bgGeom == null) {
            buildBackgroundGeom();
        } else {
            bgGeom.setMesh(new Quad(w, h));
        }
    }

    public float getWidth() {
        return currentWidth;
    }

    public float getHeight() {
        return currentHeight;
    }

    public void setBgColor(ColorRGBA color) {
        if (bgGeom != null) {
            Material mat = bgGeom.getMaterial();
            mat.setColor("Color", color);
        }
    }

    private void buildBackgroundGeom() {
        Quad quad = new Quad(currentWidth, currentHeight);
        bgGeom = new Geometry("BG_" + panel.getId(), quad);
        Material mat = new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        mat.setColor("Color", panel.getBgColor());
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        bgGeom.setMaterial(mat);
        panel.getNode().attachChild(bgGeom);
    }
}
