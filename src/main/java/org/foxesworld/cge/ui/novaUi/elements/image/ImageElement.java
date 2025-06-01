package org.foxesworld.cge.ui.novaUi.elements.image;

import com.jme3.asset.AssetManager;
import com.jme3.material.RenderState;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.novaUi.AbstractUIElement;
import org.foxesworld.cge.ui.novaUi.elements.PanelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ImageElement — элемент, который рисует картинку (GUI-квадрат с текстурой).
 * В XML:
 *   <Element id="logo" type="ImageElement" imagePath="Textures/logo.png" posX="50" posY="50" width="128" height="128" onClick="onLogoClick"/>
 */
public class ImageElement extends AbstractUIElement {
    private static final Logger logger = LoggerFactory.getLogger(ImageElement.class);

    private Geometry geom;
    private float rawPosX = 0f;
    private float rawPosY = 0f;
    private float elemWidth = 0f;
    private float elemHeight = 0f;

    private AssetManager assetManager;

    public ImageElement(CalistaGameEngine engine, String id, PanelElement parent) {
        this.id = id;
        this.parentPanel = parent;
        this.assetManager = engine.getAssetManager();
        this.node.setName("Image_" + id);
        logger.debug("ImageElement '{}' created", id);
    }

    @Override
    public boolean hasOwnAlign() {
        return ownAlign != null;
    }

    @Override
    public String getOwnAlign() {
        return ownAlign;
    }

    public float getRawPosX() {
        return rawPosX;
    }

    public float getRawPosY() {
        return rawPosY;
    }

    public float getWidth() {
        return elemWidth;
    }

    public float getHeight() {
        return elemHeight;
    }

    @Override
    public void setProperty(String key, String value) {
        logger.debug("ImageElement '{}' setProperty: '{}'='{}'", id, key, value);
        switch (key) {
            case "imagePath":
                loadImage(value);
                break;
            case "posX":
                rawPosX = Float.parseFloat(value);
                break;
            case "posY":
                rawPosY = Float.parseFloat(value);
                break;
            case "width":
                elemWidth = Float.parseFloat(value);
                resizeQuad();
                break;
            case "height":
                elemHeight = Float.parseFloat(value);
                resizeQuad();
                break;
            case "color":
                ColorRGBA c = parseColor(value);
                if (geom != null && geom.getMaterial() != null) {
                    geom.getMaterial().setColor("Color", c);
                }
                break;
            case "align":
                ownAlign = value;
                break;
            default:
                logger.warn("ImageElement '{}' unknown property '{}'", id, key);
                break;
        }
    }

    @Override
    public void setOnClickHandler(String methodName, Object eventHandlerTarget) {
        super.setOnClickHandler(methodName, eventHandlerTarget);
        // Для ImageElement можно настроить MouseInputListener аналогично TextElement,
        // проверяя попадание по прямоугольнику quad.
        // (оставляем как “заглушку”)
        logger.debug("ImageElement '{}' onClick bound to '{}'", id, methodName);
    }

    /** Загрузка текстуры и создание геометрии quad */
    private void loadImage(String imagePath) {
        com.jme3.texture.Texture tex = assetManager.loadTexture(imagePath);
        Quad quad = new Quad(elemWidth > 0 ? elemWidth : tex.getImage().getWidth(),
                elemHeight > 0 ? elemHeight : tex.getImage().getHeight());
        geom = new Geometry("Img_" + id, quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Gui/Gui.j3md");
        mat.setTexture("Texture", tex);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);
        node.attachChild(geom);
        logger.debug("ImageElement '{}' loaded image '{}', size {}x{}", id, imagePath, quad.getWidth(), quad.getHeight());

        // Если размер не задан руками, подставим из текстуры:
        if (elemWidth <= 0)  elemWidth = quad.getWidth();
        if (elemHeight <= 0) elemHeight = quad.getHeight();
    }

    /**
     * Если мы уже создали quad, но поменяли width/height через setProperty,
     * пересоздаём mesh, сохраняя текущее изображение.
     */
    private void resizeQuad() {
        if (geom != null) {
            Quad quad = new Quad(elemWidth, elemHeight);
            geom.setMesh(quad);
            logger.debug("ImageElement '{}' resized quad to {}x{}", id, elemWidth, elemHeight);
        }
    }

    /** AABB-попадание курсора можно реализовать в слушателе мыши (по аналогии с TextElement). */
    // ...

    /** Разбор “r,g,b,a” → ColorRGBA */
    private ColorRGBA parseColor(String s) {
        String[] parts = s.split(",");
        try {
            float r = Float.parseFloat(parts[0].trim());
            float g = Float.parseFloat(parts[1].trim());
            float b = Float.parseFloat(parts[2].trim());
            float a = Float.parseFloat(parts[3].trim());
            return new ColorRGBA(r, g, b, a);
        } catch (Exception e) {
            logger.warn("ImageElement '{}' failed to parse color '{}'", id, s);
            return ColorRGBA.White.clone();
        }
    }
}
