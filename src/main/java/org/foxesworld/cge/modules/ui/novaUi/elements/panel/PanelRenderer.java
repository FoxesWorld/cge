package org.foxesworld.cge.modules.ui.novaUi.elements.panel;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractRenderer;

/**
 * PanelRenderer is responsible for rendering the visual background of a {@link PanelElement}.
 * <p>
 * It manages the geometry, color, and sizing of the panel's background in the scene graph.
 * The renderer ensures the background is always synchronized with the panel's size and color.
 * <p>
 * Fix: The alpha channel (transparency) is now preserved on panel updates.
 * Hard fix: The color is never "applied over" previous color, alpha is never accumulated.
 * <p>
 * Inherits from {@link AbstractRenderer} for UI integration and color management.
 */
public class PanelRenderer extends AbstractRenderer {
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
     * Returns the main node for this renderer (background geometry).
     * @return the background geometry as a Node.
     */
    @Override
    public Node getNode() {
        // Optionally, return a Node for future extensibility.
        if (bgGeom == null) {
            createBackground(currentWidth > 0 ? currentWidth : 1f, currentHeight > 0 ? currentHeight : 1f);
        }
        Node root = new Node("PanelRendererNode_" + panel.getId());
        root.attachChild(bgGeom);
        return root;
    }

    /**
     * Updates or sets the background geometry to the specified width and height.
     * If the background does not exist, it will be created.
     * @param width The new width.
     * @param height The new height.
     */
    public void updateGeometry(float width, float height) {
        setSize(width, height);
    }

    /**
     * Sets the size of the background geometry.
     * If the background does not exist, it will be created.
     * @param width The new width.
     * @param height The new height.
     */
    @Override
    public void setSize(float width, float height) {
        currentWidth = width;
        currentHeight = height;
        if (bgGeom == null) {
            createBackground(width, height);
        } else {
            bgGeom.setMesh(new Quad(width, height));
        }
        bgGeom.setLocalTranslation(0f, 0f, 0f);
        applyPanelBgColor();
    }

    /**
     * Gets the current width of the panel's background.
     * @return The width.
     */
    @Override
    public float getWidth() {
        return currentWidth;
    }

    /**
     * Gets the current height of the panel's background.
     * @return The height.
     */
    @Override
    public float getHeight() {
        return currentHeight;
    }

    /**
     * Sets the background color of the panel.
     * If the background geometry exists, its material color will be updated.
     * @param color The new background color.
     */
    @Override
    public void setColor(ColorRGBA color) {
        setBgColor(color);
    }

    /**
     * Gets the current background color of the panel.
     * If the background geometry does not exist, returns panel's own color.
     * @return The background color, or panel's color if not available.
     */
    @Override
    public ColorRGBA getColor() {
        return getBgColor();
    }

    /**
     * Sets the background color of the panel.
     * If the background geometry exists, its material color will be updated.
     * @param color The new background color.
     */
    public void setBgColor(ColorRGBA color) {
        if (bgGeom != null) {
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