package org.foxesworld.cge.modules.ui.novaUi.elements.image;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ImageRenderer — отвечает за создание и обновление геометрии и материала изображения.
 */
public class ImageRenderer extends AbstractRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ImageRenderer.class);

    private final AssetManager assetManager;
    private final Node node = new Node("ImageRenderer");
    private Geometry geom = null;
    private float width = 32f;
    private float height = 32f;
    private String imagePath = null;
    private ColorRGBA color = ColorRGBA.White.clone();

    public ImageRenderer(AssetManager assetManager) {
        this.assetManager = assetManager;
        updateQuad();
    }

    @Override
    public Node getNode() {
        return node;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
        updateQuad();
    }

    public void setWidth(float width) {
        this.width = width;
        updateQuad();
    }

    public void setHeight(float height) {
        this.height = height;
        updateQuad();
    }

    public void setColor(ColorRGBA color) {
        this.color = color != null ? color : ColorRGBA.White.clone();
        updateMaterialColor();
    }

    /**
     * Создаёт или обновляет quad с текстурой или fallback-квадратом.
     */
    private void updateQuad() {
        node.detachAllChildren();
        boolean fallback = false;
        float w = width > 0 ? width : 32f;
        float h = height > 0 ? height : 32f;

        if (imagePath != null && !imagePath.isEmpty()) {
            try {
                Texture tex = assetManager.loadTexture(imagePath);
                // Если удалось загрузить текстуру, используем ее размеры, если явно не заданы
                w = width > 0 ? width : tex.getImage().getWidth();
                h = height > 0 ? height : tex.getImage().getHeight();
                Quad quad = new Quad(w, h);
                geom = new Geometry("ImgRenderer", quad);

                // Для иконок и UI используем Unshaded, чтобы избежать влияния света
                Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                mat.setTexture("ColorMap", tex);

                // Если нужен tint, используем цвет (только если не белый)
                if (!color.equals(ColorRGBA.White)) {
                    mat.setColor("Color", color);
                }

                mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                geom.setMaterial(mat);
                node.attachChild(geom);

                logger.debug("ImageRenderer: image '{}' {}x{}, color={}", imagePath, w, h, color);
                return;
            } catch (AssetNotFoundException e) {
                logger.warn("ImageRenderer: image '{}' NOT FOUND: {}. Fallback to color quad.", imagePath, e.getMessage());
                fallback = true;
            } catch (Exception e) {
                logger.error("ImageRenderer: failed to load image '{}': {}", imagePath, e.toString());
                fallback = true;
            }
        } else {
            fallback = true;
        }

        // Если не удалось загрузить текстуру — fallback: просто цветной квадрат
        Quad quad = new Quad(w, h);
        geom = new Geometry("ImgRenderer", quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(
                color.a < 0.99f ? RenderState.BlendMode.Alpha : RenderState.BlendMode.Off
        );
        geom.setMaterial(mat);
        node.attachChild(geom);
        if (fallback) {
            logger.debug("ImageRenderer: fallback color quad {}x{} color={}", w, h, color);
        }
    }

    /**
     * Обновляет только цвет материала для tint (альфа, фильтр), если материал поддерживает цвет.
     */
    private void updateMaterialColor() {
        if (geom != null && geom.getMaterial() != null) {
            Material mat = geom.getMaterial();
            if (mat.getMaterialDef().getMaterialParam("Color") != null) {
                mat.setColor("Color", color);
            }
            mat.getAdditionalRenderState().setBlendMode(
                    color.a < 0.99f ? RenderState.BlendMode.Alpha : RenderState.BlendMode.Off
            );
        }
    }
}