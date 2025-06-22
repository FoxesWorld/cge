package org.foxesworld.cge.modules.ui.novaUi.elements.text;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractRenderer;

/**
 * TextRenderer is responsible for rendering text in the UI, maintaining consistent color state,
 * and supporting padding and margin for layout.
 * <p>
 * The class ensures the text color does not become darker or accumulate alpha on repeated updates,
 * even when the font is changed. Padding and margin can be configured for advanced layout control.
 * </p>
 *
 * <ul>
 *   <li>{@link #getNode()} provides access to the main scene node for integration.</li>
 *   <li>Color and sizing methods allow dynamic appearance changes.</li>
 *   <li>Padding methods control spacing inside the text's bounding box; margin methods control spacing outside.</li>
 * </ul>
 */
public class TextRenderer extends AbstractRenderer {
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

    /**
     * Constructs a new TextRenderer.
     *
     * @param assetManager the AssetManager for loading fonts (must not be null)
     * @param fontPath the path to the font asset
     * @param fontSize the initial font size
     */
    public TextRenderer(AssetManager assetManager, String fontPath, float fontSize) {
        this.assetManager = assetManager;
        this.fontPath = fontPath;
        this.fontSize = fontSize;
        initBitmapText();
    }

    /**
     * Initializes the BitmapText object with the current font and color.
     */
    private void initBitmapText() {
        BitmapFont font = assetManager.loadFont(fontPath);
        bitmapText = new BitmapText(font, false);
        bitmapText.setSize(fontSize);
        bitmapText.setColor(color);
        bitmapText.setText("");
        updateLocalTranslation();
    }

    /**
     * Sets the font by path, preserving the current color.
     *
     * @param path the new font asset path
     */
    public void setFont(String path) {
        this.fontPath = path;
        ColorRGBA currentColor = color.clone();
        initBitmapText();
        setColor(currentColor);
    }

    /**
     * Sets the font size.
     *
     * @param size the new font size
     */
    public void setFontSize(float size) {
        this.fontSize = size;
        bitmapText.setSize(size);
        updateLocalTranslation();
        bitmapText.setColor(color);
    }

    /**
     * Sets the text color.
     *
     * @param color the color to set (never darkens or accumulates alpha)
     */
    @Override
    public void setColor(ColorRGBA color) {
        this.color = color.clone();
        bitmapText.setColor(this.color);
    }

    /**
     * Gets the current text color.
     *
     * @return the current color
     */
    @Override
    public ColorRGBA getColor() {
        return color;
    }

    /**
     * Sets padding for each side.
     *
     * @param left   padding on the left
     * @param right  padding on the right
     * @param top    padding on the top
     * @param bottom padding on the bottom
     */
    public void setPadding(float left, float right, float top, float bottom) {
        this.paddingLeft = left;
        this.paddingRight = right;
        this.paddingTop = top;
        this.paddingBottom = bottom;
        updateLocalTranslation();
    }

    /**
     * Sets margin for each side.
     *
     * @param left   margin on the left
     * @param right  margin on the right
     * @param top    margin on the top
     * @param bottom margin on the bottom
     */
    public void setMargin(float left, float right, float top, float bottom) {
        this.marginLeft = left;
        this.marginRight = right;
        this.marginTop = top;
        this.marginBottom = bottom;
        updateLocalTranslation();
    }

    /**
     * Sets uniform padding for all sides.
     *
     * @param padding the padding value for all sides
     */
    public void setPadding(float padding) {
        setPadding(padding, padding, padding, padding);
    }

    /**
     * Sets uniform margin for all sides.
     *
     * @param margin the margin value for all sides
     */
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

    /**
     * Returns the main node for this text renderer.
     *
     * @return the BitmapText instance for scene integration
     */
    @Override
    public Node getNode() {
        return bitmapText;
    }

    /**
     * Returns the total width including padding and margin.
     *
     * @return the width in pixels (or font units)
     */
    @Override
    public float getWidth() {
        return bitmapText.getLineWidth() + paddingLeft + paddingRight + marginLeft + marginRight;
    }

    /**
     * Returns the total height including padding and margin.
     *
     * @return the height in pixels (or font units)
     */
    @Override
    public float getHeight() {
        return bitmapText.getLineHeight() + paddingTop + paddingBottom + marginTop + marginBottom;
    }

    /**
     * Returns the X position of the text (with current translation applied).
     *
     * @return the X coordinate
     */
    public float getX() {
        return bitmapText.getLocalTranslation().x;
    }

    /**
     * Returns the Y position of the text (with current translation applied).
     *
     * @return the Y coordinate
     */
    public float getY() {
        return bitmapText.getLocalTranslation().y;
    }

    // Optionally, getters for padding/margin for use in layouts:

    /**
     * @return left padding value
     */
    public float getPaddingLeft() { return paddingLeft; }
    /**
     * @return right padding value
     */
    public float getPaddingRight() { return paddingRight; }
    /**
     * @return top padding value
     */
    public float getPaddingTop() { return paddingTop; }
    /**
     * @return bottom padding value
     */
    public float getPaddingBottom() { return paddingBottom; }

    /**
     * @return left margin value
     */
    public float getMarginLeft() { return marginLeft; }
    /**
     * @return right margin value
     */
    public float getMarginRight() { return marginRight; }
    /**
     * @return top margin value
     */
    public float getMarginTop() { return marginTop; }
    /**
     * @return bottom margin value
     */
    public float getMarginBottom() { return marginBottom; }

    public BitmapText getBitmapText() {
        return bitmapText;
    }
}