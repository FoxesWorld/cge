package org.foxesworld.cge.modules.ui.novaUi.elements.panel;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;

/**
 * PanelRenderer is responsible for rendering the visual background of a {@link PanelElement}.
 * <p>
 * It manages the geometry, color, and sizing of the panel's background in the scene graph.
 * The renderer ensures the background is always synchronized with the panel's size and color.
 * <p>
 * Fix: The alpha channel (transparency) is now preserved on panel updates.
 * Hard fix: The color is never "applied over" previous color, alpha is never accumulated.
 */
public class PanelRenderer {
    /** Reference to the game engine for asset and scene management. */
    private final CalistaGameEngine engine;
    /** The panel this renderer is associated with. */
    private final PanelElement panel;
    /** Geometry node for the background quad. */
    private Geometry bgGeom;
    /** Current width of the panel's background. */
    private float currentWidth;
    /** Current height of the panel's background. */
    private float currentHeight;

    /**
     * Constructs a new PanelRenderer.
     * @param engine The game engine instance.
     * @param panel The panel to render.
     */
    public PanelRenderer(CalistaGameEngine engine, PanelElement panel) {
        this.engine = engine;
        this.panel = panel;
    }

    /**
     * Updates the background geometry to the specified width and height.
     * If the background does not exist, it will be created.
     * @param width The new width.
     * @param height The new height.
     */
    public void updateGeometry(float width, float height) {
        if (bgGeom == null) {
            createBackground(width, height);
        } else {
            bgGeom.setMesh(new Quad(width, height));
        }
        bgGeom.setLocalTranslation(0f, 0f, 0f);
        currentWidth = width;
        currentHeight = height;
        // Always set color directly from panel with correct alpha (never chain/accumulate)
        applyPanelBgColor();
    }

    /**
     * Sets the size of the background geometry.
     * If the background does not exist, it will be created.
     * @param width The new width.
     * @param height The new height.
     */
    public void setSize(float width, float height) {
        currentWidth = width;
        currentHeight = height;
        if (bgGeom == null) {
            createBackground(width, height);
        } else {
            bgGeom.setMesh(new Quad(width, height));
        }
        bgGeom.setLocalTranslation(0f, 0f, 0f);
        // Always set color directly from panel with correct alpha (never chain/accumulate)
        applyPanelBgColor();
    }

    /**
     * Gets the current width of the panel's background.
     * @return The width.
     */
    public float getWidth() {
        return currentWidth;
    }

    /**
     * Gets the current height of the panel's background.
     * @return The height.
     */
    public float getHeight() {
        return currentHeight;
    }

    /**
     * Sets the background color of the panel.
     * If the background geometry exists, its material color will be updated.
     * @param color The new background color.
     */
    public void setBgColor(ColorRGBA color) {
        if (bgGeom != null) {
            // Always set the color as-is, do not preserve/accumulate previous alpha!
            bgGeom.getMaterial().setColor("Color", color.clone());
        }
    }

    /**
     * Gets the current background color of the panel.
     * If the background geometry does not exist, returns panel's own color.
     * @return The background color, or panel's color if not available.
     */
    public ColorRGBA getBgColor() {
        if (bgGeom != null && bgGeom.getMaterial() != null) {
            var matParam = bgGeom.getMaterial().getParam("Color");
            if (matParam != null) {
                Object value = matParam.getValue();
                if (value instanceof ColorRGBA) return (ColorRGBA) value;
            }
        }
        // fallback: return panel's bgColor (might be default)
        return panel.getBgColor();
    }

    /**
     * Creates the background geometry for the panel with the specified size.
     * The geometry will use a transparent material for alpha blending.
     * @param width The width of the panel.
     * @param height The height of the panel.
     */
    void createBackground(float width, float height) {
        Quad quad = new Quad(width, height);
        bgGeom = new Geometry("BG_" + panel.getId(), quad);
        Material mat = new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        ColorRGBA baseColor = panel.getBgColor() != null ? panel.getBgColor().clone() : ColorRGBA.White.clone();
        mat.setColor("Color", baseColor);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        bgGeom.setMaterial(mat);
        panel.getNode().attachChild(bgGeom);
    }

    /**
     * Always sets the background color directly from the panel's color, preserving its alpha,
     * and never accumulating alpha from any previous material color.
     */
    private void applyPanelBgColor() {
        if (bgGeom != null && bgGeom.getMaterial() != null) {
            ColorRGBA panelColor = panel.getBgColor();
            if (panelColor == null) panelColor = ColorRGBA.White.clone();
            bgGeom.getMaterial().setColor("Color", panelColor.clone());
        }
    }
}