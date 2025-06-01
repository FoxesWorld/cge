package org.foxesworld.cge.ui.novaUi.elements.text;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;

public class TextRenderer {
    private final AssetManager assetManager;
    private BitmapText bitmapText;
    private String fontPath;
    private float fontSize;
    private ColorRGBA color = ColorRGBA.White.clone();

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
        bitmapText.setLocalTranslation(0f, bitmapText.getLineHeight(), 0f);
    }

    public void setFont(String path) {
        this.fontPath = path;
        initBitmapText();
    }

    public void setFontSize(float size) {
        this.fontSize = size;
        bitmapText.setSize(size);
        bitmapText.setLocalTranslation(0, bitmapText.getLineHeight(), 0f);
    }

    public void setColor(ColorRGBA color) {
        this.color = color.clone();
        bitmapText.setColor(color);
    }

    public BitmapText getBitmapText() {
        return bitmapText;
    }

    public float getWidth() {
        return bitmapText.getLineWidth();
    }

    public float getHeight() {
        return bitmapText.getLineHeight();
    }

    public float getX() {
        return bitmapText.getLocalTranslation().x;
    }

    public float getY() {
        return bitmapText.getLocalTranslation().y;
    }
}
