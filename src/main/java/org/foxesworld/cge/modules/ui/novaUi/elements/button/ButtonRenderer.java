package org.foxesworld.cge.modules.ui.novaUi.elements.button;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.Geometry;

/**
 * ButtonRenderer — отвечает за отрисовку кнопки (фон + текст) и работу с их свойствами.
 */
public class ButtonRenderer {

    private final Node node = new Node("ButtonRenderer");
    private final Geometry bgGeometry;
    private final BitmapText bitmapText;

    private float paddingLeft = 0, paddingRight = 0, paddingTop = 0, paddingBottom = 0;
    private float marginLeft = 0, marginRight = 0, marginTop = 0, marginBottom = 0;

    private float width = 100, height = 40; // базовые размеры
    private boolean enabled = true;

    protected ColorRGBA bgColor = ColorRGBA.Gray.clone();
    protected ColorRGBA textColor = ColorRGBA.White.clone();

    private final AssetManager assetManager;

    public ButtonRenderer(AssetManager assetManager, String fontPath, float fontSize) {
        this.assetManager = assetManager;

        // Фон
        Quad bgQuad = new Quad(width, height);
        bgGeometry = new Geometry("ButtonBG", bgQuad);
        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", bgColor);
        bgGeometry.setMaterial(bgMat);
        node.attachChild(bgGeometry);

        // Текст
        BitmapFont font = assetManager.loadFont(fontPath);
        bitmapText = new BitmapText(font, false);
        bitmapText.setSize(fontSize);
        bitmapText.setColor(textColor);
        bitmapText.setText("Button");
        bitmapText.setLocalTranslation(10, height / 2 + fontSize / 2, 0); // базовые координаты

        node.attachChild(bitmapText);

        updateLayout();
    }

    public void setText(String text) {
        bitmapText.setText(text);
        updateLayout();
    }

    public void setFont(String fontPath) {
        BitmapFont font = assetManager.loadFont(fontPath);
        // bitmapText.setFont(font); // Раскомментируйте, если поддерживается
        updateLayout();
    }

    public void setFontSize(float fontSize) {
        bitmapText.setSize(fontSize);
        updateLayout();
    }

    public void setTextColor(ColorRGBA color) {
        this.textColor = color;
        bitmapText.setColor(color);
    }

    public void setBackgroundColor(ColorRGBA color) {
        this.bgColor = color;
        Material mat = bgGeometry.getMaterial();
        if (mat != null)
            mat.setColor("Color", bgColor);
    }

    public ColorRGBA getBackgroundColor() {
        return bgColor.clone();
    }

    public void setPadding(float all) {
        setPadding(all, all, all, all);
    }

    public void setPadding(float left, float right, float top, float bottom) {
        this.paddingLeft = left;
        this.paddingRight = right;
        this.paddingTop = top;
        this.paddingBottom = bottom;
        updateLayout();
    }

    public void setMargin(float all) {
        setMargin(all, all, all, all);
    }

    public void setMargin(float left, float right, float top, float bottom) {
        this.marginLeft = left;
        this.marginRight = right;
        this.marginTop = top;
        this.marginBottom = bottom;
        updateLayout();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            setBackgroundColor(ColorRGBA.DarkGray.mult(0.5f));
            setTextColor(ColorRGBA.Gray);
        } else {
            setBackgroundColor(bgColor);
            setTextColor(textColor);
        }
    }

    private void updateLayout() {
        // Размер по тексту + паддинги
        float textWidth = bitmapText.getLineWidth();
        float textHeight = bitmapText.getHeight();

        width = textWidth + paddingLeft + paddingRight;
        height = textHeight + paddingTop + paddingBottom;

        // Обновить фон
        ((Quad) bgGeometry.getMesh()).updateGeometry(width, height);

        // Текст: выравнивание по центру
        float textX = paddingLeft;
        float textY = height - paddingTop;
        bitmapText.setLocalTranslation(textX, textY, 0);

        // Сдвиг всей ноды (маргины)
        node.setLocalTranslation(marginLeft, -marginTop, 0);
    }

    public float getWidth() { return width + marginLeft + marginRight; }
    public float getHeight() { return height + marginTop + marginBottom; }

    public float getX() { return node.getLocalTranslation().x; }
    public float getY() { return node.getLocalTranslation().y + height; }

    public Node getNode() { return node; }
}