package org.foxesworld.cge.modules.ui.novaUi.elements.image;

import com.jme3.math.Vector2f;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.PropertyParser;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A UI element that displays a 2D image with intelligent sizing capabilities.
 * Supports "auto" sizing to fit the texture dimensions and maintains aspect ratio.
 *
 * Supported Properties (in addition to AbstractUIElement):
 * <ul>
 *   <li><b>imagePath:</b> Path to the texture file.</li>
 *   <li><b>width, height:</b> Desired dimensions. Can be a number or "auto".</li>
 *   <li><b>color:</b> Tint color in "r,g,b,a" or HEX format.</li>
 *   <li><b>translate, translateX, translateY:</b> Local position offset.</li>
 * </ul>
 */
public class ImageElement extends AbstractUIElement {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageElement.class);
    private static final float AUTO_SIZE = -1f; // Маркер для автоматического размера

    private final ImageRenderer renderer;

    // Запрашиваемые пользователем размеры (могут быть AUTO_SIZE)
    private float requestedWidth = AUTO_SIZE;
    private float requestedHeight = AUTO_SIZE;

    // Финальные, рассчитанные размеры
    private float finalWidth = 0f;
    private float finalHeight = 0f;

    public ImageElement(CalistaGameEngine engine, String id, PanelElement parent) {
        super(engine, id, parent);
        this.node.setName("Image_" + id);

        this.renderer = new ImageRenderer(engine.getAssetManager());
        this.node.attachChild(renderer.getNode());
    }

    @Override
    public void setProperty(String key, String value) {
        boolean sizeChanged = false;
        switch (key.toLowerCase()) {
            case "imagepath" -> {
                renderer.setImage(value);
                // Если размеры не заданы явно, они автоматически подстроятся под текстуру
                if (requestedWidth == AUTO_SIZE || requestedHeight == AUTO_SIZE) {
                    sizeChanged = true;
                }
            }
            case "width" -> {
                this.requestedWidth = "auto".equalsIgnoreCase(value) ? AUTO_SIZE : PropertyParser.tryParseFloat(value, 0f);
                sizeChanged = true;
            }
            case "height" -> {
                this.requestedHeight = "auto".equalsIgnoreCase(value) ? AUTO_SIZE : PropertyParser.tryParseFloat(value, 0f);
                sizeChanged = true;
            }
            case "color" -> renderer.setColor(PropertyParser.parseColorRGBA(value));
            case "translatex" -> renderer.setTranslateX(PropertyParser.tryParseFloat(value, 0f));
            case "translatey" -> renderer.setTranslateY(PropertyParser.tryParseFloat(value, 0f));
            case "translate" -> {
                float[] xy = PropertyParser.parseEdgeValues(value);
                if (xy.length >= 2) {
                    renderer.setTranslation(xy[0], xy[1]);
                }
            }
            default -> {
                super.setProperty(key, value);
                if (key.equalsIgnoreCase("margin")) {
                    sizeChanged = true;
                }
            }
        }

        if (sizeChanged) {
            recalculateFinalSize();
        }
    }

    /**
     * Recalculates the final dimensions based on requested size, original image size, and aspect ratio.
     */
    private void recalculateFinalSize() {
        Vector2f originalSize = renderer.getOriginalImageSize();

        float newWidth = this.requestedWidth;
        float newHeight = this.requestedHeight;

        // Если нет текстуры, размер 0
        if (originalSize.x <= 0 || originalSize.y <= 0) {
            newWidth = (newWidth == AUTO_SIZE) ? 0 : newWidth;
            newHeight = (newHeight == AUTO_SIZE) ? 0 : newHeight;
        } else {
            float aspectRatio = originalSize.x / originalSize.y;

            if (newWidth == AUTO_SIZE && newHeight == AUTO_SIZE) {
                // Полностью автоматический размер: берем размер текстуры
                newWidth = originalSize.x;
                newHeight = originalSize.y;
            } else if (newWidth == AUTO_SIZE) {
                // Ширина авто, высота задана: вычисляем ширину по пропорции
                newWidth = newHeight * aspectRatio;
            } else if (newHeight == AUTO_SIZE) {
                // Высота авто, ширина задана: вычисляем высоту по пропорции
                newHeight = newWidth / aspectRatio;
            }
        }

        // Если финальный размер изменился, обновляем рендерер и уведомляем родителя
        if (this.finalWidth != newWidth || this.finalHeight != newHeight) {
            this.finalWidth = newWidth;
            this.finalHeight = newHeight;

            renderer.setSize(this.finalWidth, this.finalHeight);

            if (getParentPanel() != null) {
                getParentPanel().markLayoutDirty();
            }
        }
    }

    @Override
    public float getWidth() {
        return this.finalWidth;
    }

    @Override
    public float getHeight() {
        return this.finalHeight;
    }
}