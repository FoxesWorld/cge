package org.foxesworld.cge.modules.ui.novaUi.elements.image;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the visual rendering and positioning of the ImageElement.
 * It manages the Geometry, Material, Texture, and local translation for the image quad.
 */
public class ImageRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageRenderer.class);

    private final AssetManager assetManager;
    private final Node node = new Node("ImageRenderer");
    private final Geometry quadGeom;
    private final Material material;
    private final Vector3f translation = new Vector3f();
    private final Vector2f originalImageSize = new Vector2f();

    public ImageRenderer(AssetManager assetManager) {
        this.assetManager = assetManager;

        this.material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        // Создаем геометрию Quad
        this.quadGeom = new Geometry("ImageQuad", new Quad(1, 1));
        quadGeom.setMaterial(material);

        node.attachChild(quadGeom);
    }

    /**
     * Sets the image and returns its original dimensions.
     * @param imagePath The path to the texture asset.
     * @return A Vector2f containing the width and height of the loaded texture, or null on failure.
     */
    public Vector2f setImage(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            material.clearParam("ColorMap");
            originalImageSize.set(0, 0);
            return null;
        }
        try {
            TextureKey key = new TextureKey(imagePath, true);
            Texture tex = assetManager.loadTexture(key);
            material.setTexture("ColorMap", tex);

            originalImageSize.set(tex.getImage().getWidth(), tex.getImage().getHeight());
            return originalImageSize;

        } catch (Exception e) {
            LOGGER.error("Failed to load image texture from path: {}", imagePath, e);
            originalImageSize.set(0, 0);
            return null;
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
    }

    // --- НОВЫЕ МЕТОДЫ для управления смещением ---

    /**
     * Sets the vertical translation (Y-axis) of the image quad, relative to its
     * position determined by the layout.
     * @param y The vertical offset.
     */
    public void setTranslateY(float y) {
        this.translation.setY(y);
        this.quadGeom.setLocalTranslation(this.translation);
    }

    /**
     * Sets the horizontal translation (X-axis) of the image quad, relative to its
     * position determined by the layout.
     * @param x The horizontal offset.
     */
    public void setTranslateX(float x) {
        this.translation.setX(x);
        this.quadGeom.setLocalTranslation(this.translation);
    }

    public Vector2f getOriginalImageSize() {
        return originalImageSize;
    }
    /**
     * Sets the full 2D translation of the image quad, relative to its
     * position determined by the layout.
     * @param x The horizontal offset.
     * @param y The vertical offset.
     */
    public void setTranslation(float x, float y) {
        this.translation.set(x, y, this.translation.z); // Сохраняем z на случай, если он используется
        this.quadGeom.setLocalTranslation(this.translation);
    }

    public Node getNode() {
        return node;
    }
}