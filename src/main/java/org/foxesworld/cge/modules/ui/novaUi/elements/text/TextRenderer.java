package org.foxesworld.cge.modules.ui.novaUi.elements.text;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;

/**
 * TextRenderer ensures the color state is preserved across font changes.
 * The text color will NOT become darker or accumulate alpha on repeated updates.
 * Now supports padding and margin for text layout.
 */
public class TextRenderer {
    private final AssetManager assetManager;
    private BitmapText bitmapText;
    private String fontPath;
    private float fontSize;
    private ColorRGBA color = ColorRGBA.White.clone();

    // Padding and margin (in pixels or same units as fontSize)
    private float paddingLeft = 0f;
    private float paddingRight = 0f;
    private float paddingTop = 0f;
    private float paddingBottom = 0f;

    private float marginLeft = 0f;
    private float marginRight = 0f;
    private float marginTop = 0f;
    private float marginBottom = 0f;

    public TextRenderer(AssetManager assetManager, String fontPath, float fontSize) {
        this.assetManager = assetManager;
        this.fontPath = fontPath;
        this.fontSize = fontSize;
        initBitmapText();
    }

    private void initBitmapText() {
        BitmapFont font = assetManager.loadFont(fontPath);
        bitmapText = new BitmapText(font, false);
        bitmapText.setSize(fontSize);
        bitmapText.setColor(color);
        bitmapText.setText("");
        updateLocalTranslation();
    }

    public void setFont(String path) {
        this.fontPath = path;
        ColorRGBA currentColor = color.clone();
        initBitmapText();
        setColor(currentColor);
    }

    public void setFontSize(float size) {
        this.fontSize = size;
        bitmapText.setSize(size);
        updateLocalTranslation();
        bitmapText.setColor(color);
    }

    public void setColor(ColorRGBA color) {
        this.color = color.clone();
        bitmapText.setColor(this.color);
    }

    public void setPadding(float left, float right, float top, float bottom) {
        this.paddingLeft = left;
        this.paddingRight = right;
        this.paddingTop = top;
        this.paddingBottom = bottom;
        updateLocalTranslation();
    }

    public void setMargin(float left, float right, float top, float bottom) {
        this.marginLeft = left;
        this.marginRight = right;
        this.marginTop = top;
        this.marginBottom = bottom;
        updateLocalTranslation();
    }

    // Optionally, you can set uniform padding/margin
    public void setPadding(float padding) {
        setPadding(padding, padding, padding, padding);
    }
    public void setMargin(float margin) {
        setMargin(margin, margin, margin, margin);
    }

    /**
     * Updates the local translation of BitmapText to account for padding and margin.
     * Padding shifts the text inside its box; margin is used for layout (external spacing).
     */
    private void updateLocalTranslation() {
        // By default, BitmapText's anchor is baseline left.
        // We account for top padding by shifting Y up.
        float x = paddingLeft + marginLeft;
        float y = bitmapText.getLineHeight() + paddingTop + marginTop;
        bitmapText.setLocalTranslation(x, y, 0f);
    }

    public BitmapText getBitmapText() {
        return bitmapText;
    }

    public float getWidth() {
        return bitmapText.getLineWidth() + paddingLeft + paddingRight + marginLeft + marginRight;
    }

    public float getHeight() {
        return bitmapText.getLineHeight() + paddingTop + paddingBottom + marginTop + marginBottom;
    }

    public float getX() {
        return bitmapText.getLocalTranslation().x;
    }

    public float getY() {
        return bitmapText.getLocalTranslation().y;
    }

    // Optionally, getters for padding/margin for use in layouts:
    public float getPaddingLeft() { return paddingLeft; }
    public float getPaddingRight() { return paddingRight; }
    public float getPaddingTop() { return paddingTop; }
    public float getPaddingBottom() { return paddingBottom; }

    public float getMarginLeft() { return marginLeft; }
    public float getMarginRight() { return marginRight; }
    public float getMarginTop() { return marginTop; }
    public float getMarginBottom() { return marginBottom; }
}