package org.foxesworld.cge.modules.ui.novaUi.elements.image;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.PropertyParser;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A UI element that displays a 2D image.
 * Its appearance is configured through properties, and it delegates rendering to ImageRenderer.
 */
public class ImageElement extends AbstractUIElement {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageElement.class);

    private final ImageRenderer renderer;
    private float width = 32f;  // Default width
    private float height = 32f; // Default height

    public ImageElement(CalistaGameEngine engine, String id, PanelElement parent) {
        super(engine, id, parent);
        this.node.setName("Image_" + id);

        this.renderer = new ImageRenderer(engine.getAssetManager());
        this.node.attachChild(renderer.getNode());

        // Apply default size initially
        renderer.setSize(this.width, this.height);
    }

    @Override
    public void setProperty(String key, String value) {
        switch (key.toLowerCase()) {
            case "imagepath":
                renderer.setImage(value);
                break;
            case "width":
                this.width = Float.parseFloat(value);
                renderer.setSize(this.width, this.height);
                if (getParentPanel() != null) getParentPanel().markLayoutDirty();
                break;
            case "height":
                this.height = Float.parseFloat(value);
                renderer.setSize(this.width, this.height);
                if (getParentPanel() != null) getParentPanel().markLayoutDirty();
                break;
            case "color":
                renderer.setColor(PropertyParser.parseColorRGBA(value));
                break;
            case "translatex":
                renderer.setTranslateX(PropertyParser.tryParseFloat(value, 0f));
                break;
            case "translatey":
                renderer.setTranslateY(PropertyParser.tryParseFloat(value, 0f));
                break;
            case "translate": // Для установки X и Y одновременно, например "10,-5"
                float[] xy = PropertyParser.parseEdgeValues(value);
                if (xy.length >= 2) {
                    renderer.setTranslation(xy[0], xy[1]);
                }
                break;
            default:
                // For align, margin, onClick, etc.
                super.setProperty(key, value);
                // If a property from the superclass could affect layout, mark parent as dirty
                if (key.equalsIgnoreCase("margin")) {
                    if (getParentPanel() != null) getParentPanel().markLayoutDirty();
                }
                break;
        }
    }

    @Override
    public float getWidth() {
        return this.width;
    }

    @Override
    public float getHeight() {
        return this.height;
    }
}