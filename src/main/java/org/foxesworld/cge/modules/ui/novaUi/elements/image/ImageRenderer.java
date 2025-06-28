package org.foxesworld.cge.modules.ui.novaUi.elements.image;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the visual rendering of the ImageElement.
 * It manages the Geometry, Material, and Texture for the image quad.
 */
public class ImageRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageRenderer.class);

    private final AssetManager assetManager;
    private final Node node = new Node("ImageRenderer");
    private final Geometry quadGeom;
    private final Material material;

    public ImageRenderer(AssetManager assetManager) {
        this.assetManager = assetManager;

        // Create the material that will display the image
        this.material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        // Create the quad geometry
        this.quadGeom = new Geometry("ImageQuad", new Quad(1, 1));
        quadGeom.setMaterial(material);

        node.attachChild(quadGeom);
    }

    /**
     * Sets the image to be displayed from a texture path.
     * @param imagePath The path to the texture asset.
     */
    public void setImage(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            material.clearParam("ColorMap");
            return;
        }
        try {
            TextureKey key = new TextureKey(imagePath, false); // false = don't flip texture
            Texture tex = assetManager.loadTexture(key);
            material.setTexture("ColorMap", tex);
        } catch (Exception e) {
            LOGGER.error("Failed to load image texture from path: {}", imagePath, e);
        }
    }

    /**
     * Sets the tint color of the image. White means no tint.
     * @param color The RGBA color to apply.
     */
    public void setColor(ColorRGBA color) {
        material.setColor("Color", color);
    }

    /**
     * Updates the size of the quad that displays the image.
     * @param width The new width.
     * @param height The new height.
     */
    public void setSize(float width, float height) {
        ((Quad) quadGeom.getMesh()).updateGeometry(width, height);
        //quadGeom.setLocalBound(null); // Force bound recalculation
    }

    public Node getNode() {
        return node;
    }
}