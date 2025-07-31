package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

/**
 * A highly stylized UI button inspired by modern, minimalist game menus with neon aesthetics.
 * <p>
 * This component encapsulates its own state and animation logic. It reacts to selection
 * changes by smoothly interpolating text color and animating a glowing underline.
 * The visual appearance is defined by a {@link Style} object, making it easily themeable.
 * </p>
 * <p>
 * To use, simply create an instance, add its node to the GUI, and call
 * {@link #setSelected(boolean)} and {@link #update(float)} in your menu's main loop.
 * </p>
 */
public final class ViceButton implements MenuComponent {

    private final Node buttonNode;
    private final BitmapText label;
    private final Geometry underline;
    private final Runnable action;
    private final Style style;

    private float x, y, width, height;
    private boolean isSelected = false;

    // Animation state
    private final ColorRGBA currentLabelColor = new ColorRGBA();
    private float currentUnderlineScaleX = 0f;

    /**
     * Constructs a new ViceButton with a specific style.
     *
     * @param assetManager The application's AssetManager.
     * @param text         The text to display on the button.
     * @param style        The {@link Style} object defining the button's appearance and animations.
     * @param action       The Runnable to execute when the button is clicked.
     */
    public ViceButton(AssetManager assetManager, String text, Style style, Runnable action) {
        this.action = action;
        this.style = style;
        this.buttonNode = new Node("ViceButton: " + text);

        this.label = new BitmapText(assetManager.loadFont(style.fontPath()));
        label.setText(text.toUpperCase());
        label.setColor(style.defaultColor());
        currentLabelColor.set(style.defaultColor());

        Material underlineMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        underlineMat.setColor("Color", style.glowColor());
        underlineMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        this.underline = new Geometry("ButtonUnderline", new Quad(1, style.underlineHeight()));
        underline.setMaterial(underlineMat);
        underline.setLocalScale(0f, 1f, 1f); // Initially hidden

        buttonNode.attachChild(label);
        buttonNode.attachChild(underline);
    }

    /**
     * Sets the selection state of the button, which triggers the hover/unhover animations.
     *
     * @param selected True if the button is currently hovered or selected by the user.
     */
    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }

    /**
     * Called every frame from the main application loop to update the button's animations.
     *
     * @param tpf Time per frame, used for smooth interpolation.
     */
    public void update(float tpf) {
        float lerpFactor = FastMath.clamp(tpf * style.animationSpeed(), 0, 1);

        // Interpolate label color for a smooth transition.
        ColorRGBA targetColor = isSelected ? style.selectedColor() : style.defaultColor();
        currentLabelColor.interpolateLocal(targetColor, lerpFactor);
        label.setColor(currentLabelColor);

        // Interpolate underline scale for an animated "reveal" effect.
        float targetScaleX = isSelected ? 1.0f : 0.0f;
        currentUnderlineScaleX = FastMath.interpolateLinear(lerpFactor, currentUnderlineScaleX, targetScaleX);
        underline.setLocalScale(width * currentUnderlineScaleX, 1f, 1f);

        // Keep the underline centered as it scales in or out.
        float underlineX = (width - (width * currentUnderlineScaleX)) / 2f;
        underline.setLocalTranslation(underlineX, style.underlineYOffset(), 0);
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        centerLabel();
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        this.buttonNode.setLocalTranslation(x, y, 0);
    }

    public void setLabelSize(float size) {
        label.setSize(size);
        centerLabel();
    }

    public Vector2f getPosition() {
        return new Vector2f(x, y);
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public BitmapText getLabel() {
        return label;
    }

    private void centerLabel() {
        if (width == 0 || height == 0) return;
        float textWidth = label.getLineWidth();
        float textHeight = label.getLineHeight();
        float textX = (width - textWidth) / 2f;
        float textY = (height + textHeight) / 2f;
        label.setLocalTranslation(textX, textY, 1);
    }

    public void executeAction() {
        if (action != null) {
            action.run();
        }
    }

    public boolean intersects(Vector2f point) {
        return point.x >= this.x && point.x <= (this.x + this.width) &&
                point.y >= this.y && point.y <= (this.y + this.height);
    }

    public Node getNode() {
        return buttonNode;
    }

    /**
     * A record that encapsulates all visual and animation properties for a {@link ViceButton}.
     * This allows for easy theme creation and management.
     *
     * @param defaultColor     Color of the text when not selected.
     * @param selectedColor    Color of the text when hovered/selected.
     * @param glowColor        The color of the underline, typically a brighter version of selectedColor for bloom.
     * @param fontPath         Path to the .fnt font file.
     * @param animationSpeed   A multiplier for the interpolation speed of animations.
     * @param underlineHeight  The thickness of the underline in pixels.
     * @param underlineYOffset The vertical distance of the underline from the text baseline.
     */
    public record Style(
            ColorRGBA defaultColor,
            ColorRGBA selectedColor,
            ColorRGBA glowColor,
            String fontPath,
            float animationSpeed,
            float underlineHeight,
            float underlineYOffset
    ) {
        /**
         * @return A default style configuration inspired by the Vice City / GTA 6 aesthetic.
         */
        public static Style getViceStyle() {
            // Убедитесь, что этот путь к шрифту существует в вашем проекте.
            // Рекомендуется жирный, сжатый шрифт без засечек.
            String font = "Interface/Fonts/Default.fnt";

            return new Style(
                    ColorRGBA.White.clone(),
                    new ColorRGBA(1f, 0.2f, 0.6f, 1f), // Vibrant Pink
                    new ColorRGBA(1f, 0.2f, 0.6f, 1f).mult(2.5f), // Glowing Pink for Bloom
                    font,
                    12f, // Animation speed
                    2f,  // Underline height in pixels
                    -4f  // Underline offset below the text
            );
        }
    }
}