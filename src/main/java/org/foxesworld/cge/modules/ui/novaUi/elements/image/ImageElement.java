package org.foxesworld.cge.modules.ui.novaUi.elements.image;

import com.jme3.math.ColorRGBA;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageElement extends AbstractUIElement {
    private static final Logger logger = LoggerFactory.getLogger(ImageElement.class);

    private float rawPosX = 0f;
    private float rawPosY = 0f;
    private float elemWidth = 32f;
    private float elemHeight = 32f;

    private ColorRGBA color = ColorRGBA.White.clone();
    private String imagePath = null;

    private final ImageRenderer renderer;

    public ImageElement(CalistaGameEngine engine, String id, PanelElement parent) {
        this.id = id;
        this.parentPanel = parent;
        this.node.setName("Image_" + id);
        this.renderer = new ImageRenderer(engine.getAssetManager());
        this.node.attachChild(renderer.getNode());
        logger.debug("ImageElement '{}' created", id);
    }

    @Override
    public boolean hasOwnAlign() { return ownAlign != null; }

    @Override
    public String getOwnAlign() { return ownAlign; }

    public float getRawPosX() { return rawPosX; }
    public float getRawPosY() { return rawPosY; }
    public float getWidth() { return elemWidth; }
    public float getHeight() { return elemHeight; }

    @Override
    public void setProperty(String key, String value) {
        logger.debug("ImageElement '{}' setProperty: '{}'='{}'", id, key, value);
        switch (key) {
            case "imagePath":
                imagePath = parseImagePath(value);
                break;
            case "posX":
                rawPosX = Float.parseFloat(value);
                break;
            case "posY":
                rawPosY = Float.parseFloat(value);
                break;
            case "width":
                elemWidth = Float.parseFloat(value);
                break;
            case "height":
                elemHeight = Float.parseFloat(value);
                break;
            case "color":
                color = parseColor(value);
                break;
            case "align":
                ownAlign = value;
                break;
            default:
                logger.warn("ImageElement '{}' unknown property '{}'", id, key);
                break;
        }
        update();
    }

    public void update() {
        renderer.setWidth(elemWidth);
        renderer.setHeight(elemHeight);
        renderer.setColor(color);
        renderer.setImagePath(imagePath);
        logger.debug("ImageElement '{}': imagePath={}, width={}, height={}, color={}",
                id, imagePath, elemWidth, elemHeight, color);
    }

    @Override
    public void setOnClickHandler(String methodName, Object eventHandlerTarget) {
        super.setOnClickHandler(methodName, eventHandlerTarget);
        logger.debug("ImageElement '{}' onClick bound to '{}'", id, methodName);
    }

    private ColorRGBA parseColor(String s) {
        if (s == null || s.isEmpty())
            return ColorRGBA.White.clone();
        String[] parts = s.split(",");
        try {
            float r = parts.length > 0 ? Float.parseFloat(parts[0].trim()) : 1f;
            float g = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1f;
            float b = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1f;
            float a = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 1f;
            return new ColorRGBA(r, g, b, a);
        } catch (Exception e) {
            logger.warn("ImageElement '{}' failed to parse color '{}': {}", id, s, e.getMessage());
            return ColorRGBA.White.clone();
        }
    }

    private String parseImagePath(String path) {
        if (path == null) return null;
        String fixed = path.trim().replace('\\', '/');
        if (!fixed.isEmpty() && !fixed.matches(".*\\.(?i:png|jpg|jpeg|bmp|gif)$")) {
            fixed += ".png";
        }
        logger.debug("ImageElement '{}' parsed imagePath: '{}'", id, fixed);
        return fixed;
    }
}