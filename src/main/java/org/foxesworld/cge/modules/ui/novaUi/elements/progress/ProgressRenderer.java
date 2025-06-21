package org.foxesworld.cge.modules.ui.novaUi.elements.progress;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProgressRenderer is an optimized progress bar rendering manager.
 * Minimizes object recreation, improves stability, performance, and flexibility.
 * Fully supports alpha channel for all geometries.
 */
public class ProgressRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ProgressRenderer.class);

    private final CalistaGameEngine engine;

    private Geometry borderGeom, bgGeom, fgGeom;
    private Material borderMat, bgMat, fgMat;
    private float prevBorderW = -1, prevBorderH = -1, prevBgW = -1, prevBgH = -1, prevFgW = -1, prevFgH = -1;

    public ProgressRenderer(CalistaGameEngine engine) {
        this.engine = engine;
    }

    public Geometry getBorderGeom() { return borderGeom; }
    public Geometry getBgGeom()     { return bgGeom; }
    public Geometry getFgGeom()     { return fgGeom; }

    // ----------- Helper methods for flexibility and reuse -----------

    private static void applyMaterialSettings(Material mat, ColorRGBA color) {
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
    }

    private static void applyGeometrySettings(Geometry geom, Material mat, float tx, float ty, float z, boolean isGui) {
        geom.setMaterial(mat);
        if (isGui) geom.setQueueBucket(RenderQueue.Bucket.Gui);
        geom.setLocalTranslation(tx, ty, z);
    }

    // --------- Universal build/update for geometries ---------

    private Geometry buildOrUpdateGeom(Geometry geom, float w, float h, Material mat,
                                       ColorRGBA color, String name,
                                       float tx, float ty, float z, boolean isGui) {
        boolean recreateMesh = geom == null || !hasSameQuad(geom, w, h);
        if (geom == null) {
            geom = new Geometry(name, new Quad(w, h));
            if (mat == null) {
                mat = new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
            }
            applyMaterialSettings(mat, color);
            applyGeometrySettings(geom, mat, tx, ty, z, isGui);
        } else {
            if (recreateMesh) geom.setMesh(new Quad(w, h));
            // Preserve alpha on update
            if (mat.getParam("Color") != null) {
                ColorRGBA prev = ((ColorRGBA) mat.getParam("Color").getValue());
                ColorRGBA updated = color.clone();
                updated.a = prev.a; // Preserve previous alpha
                applyMaterialSettings(mat, updated);
            } else {
                applyMaterialSettings(mat, color);
            }
            geom.setLocalTranslation(tx, ty, z);
        }
        return geom;
    }

    private boolean hasSameQuad(Geometry geom, float w, float h) {
        if (geom == null || !(geom.getMesh() instanceof Quad)) return false;
        Quad quad = (Quad) geom.getMesh();
        return Math.abs(quad.getWidth() - w) < 0.0001f && Math.abs(quad.getHeight() - h) < 0.0001f;
    }

    // --------- Public interface methods ---------

    /**
     * Builds or updates the border geometry.
     * Preserves alpha channel when updating.
     */
    public Geometry buildOrUpdateBorder(float w, float h, ColorRGBA borderColor, float zIndex) {
        borderMat = (borderMat != null) ? borderMat : new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        borderGeom = buildOrUpdateGeom(borderGeom, w, h, borderMat, borderColor, "ProgressBorder", 0f, 0f, zIndex, true);
        prevBorderW = w;
        prevBorderH = h;
        return borderGeom;
    }

    /**
     * Builds or updates the background geometry.
     * Preserves alpha channel when updating.
     */
    public Geometry buildOrUpdateBackground(float innerW, float innerH, ColorRGBA backgroundColor,
                                            float borderThickness, float padding, float zIndex) {
        float tx = borderThickness + padding, ty = borderThickness + padding;
        bgMat = (bgMat != null) ? bgMat : new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        bgGeom = buildOrUpdateGeom(bgGeom, innerW, innerH, bgMat, backgroundColor, "ProgressBackground", tx, ty, zIndex, true);
        prevBgW = innerW;
        prevBgH = innerH;
        return bgGeom;
    }

    /**
     * Builds or updates the fill geometry.
     * Preserves alpha channel when updating.
     */
    public Geometry buildOrUpdateFill(float fillW, float fillH, ColorRGBA fillColor,
                                      float borderThickness, float padding, float zIndex) {
        float tx = borderThickness + padding, ty = borderThickness + padding;
        fgMat = (fgMat != null) ? fgMat : new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        fgGeom = buildOrUpdateGeom(fgGeom, fillW, fillH, fgMat, fillColor, "ProgressFill", tx, ty, zIndex, true);
        prevFgW = fillW;
        prevFgH = fillH;
        return fgGeom;
    }

    /**
     * Updates only the fill geometry (fgGeom) without recreating the material.
     * Preserves alpha channel when updating.
     */
    public void updateFill(float fillW, float fillH, ColorRGBA fillColor,
                           float borderThickness, float padding, float zIndex) {
        if (fgGeom == null || fgMat == null) {
            buildOrUpdateFill(fillW, fillH, fillColor, borderThickness, padding, zIndex);
            return;
        }
        if (!hasSameQuad(fgGeom, fillW, fillH)) {
            fgGeom.setMesh(new Quad(fillW, fillH));
        }
        // Preserve alpha when updating
        if (fgMat.getParam("Color") != null) {
            ColorRGBA prev = ((ColorRGBA) fgMat.getParam("Color").getValue());
            ColorRGBA updated = fillColor.clone();
            updated.a = prev.a;
            fgMat.setColor("Color", updated);
        } else {
            fgMat.setColor("Color", fillColor);
        }
        fgGeom.setLocalTranslation(borderThickness + padding, borderThickness + padding, zIndex);
        prevFgW = fillW; prevFgH = fillH;
    }

    /**
     * Universal method to rebuild all parts of the progress bar with minimum operations.
     */
    public void rebuildAll(float totalW, float totalH,
                           ColorRGBA borderColor, ColorRGBA backgroundColor, ColorRGBA fillColor,
                           float borderThickness, float padding) {
        float zBorder = 0f, zBg = 1f, zFill = 2f;
        buildOrUpdateBorder(totalW, totalH, borderColor, zBorder);

        float innerW = totalW - 2f * borderThickness, innerH = totalH - 2f * borderThickness;
        buildOrUpdateBackground(innerW, innerH, backgroundColor, borderThickness, padding, zBg);

        float contentW = innerW - 2f * padding, contentH = innerH - 2f * padding;
        buildOrUpdateFill(contentW, contentH, fillColor, borderThickness, padding, zFill);
    }

    /**
     * Sets the alpha channel for all parts of the progress bar.
     */
    public void setAlpha(float alpha) {
        if (borderMat != null) setAlphaOnMat(borderMat, alpha);
        if (bgMat != null) setAlphaOnMat(bgMat, alpha);
        if (fgMat != null) setAlphaOnMat(fgMat, alpha);
    }

    private void setAlphaOnMat(Material mat, float alpha) {
        ColorRGBA c = mat.getParam("Color") != null
                ? ((ColorRGBA) mat.getParam("Color").getValue()).clone()
                : ColorRGBA.White.clone();
        c.a = alpha;
        mat.setColor("Color", c);
    }

    // --------- Resource cleanup for flexibility/safety ---------

    public void dispose() {
        if (borderGeom != null) borderGeom.removeFromParent();
        if (bgGeom != null) bgGeom.removeFromParent();
        if (fgGeom != null) fgGeom.removeFromParent();
        borderGeom = bgGeom = fgGeom = null;
        borderMat = bgMat = fgMat = null;
        prevBorderW = prevBorderH = prevBgW = prevBgH = prevFgW = prevFgH = -1;
        logger.debug("ProgressRenderer disposed and cleaned up.");
    }
}