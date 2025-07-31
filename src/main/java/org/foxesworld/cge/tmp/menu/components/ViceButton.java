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
 * A highly stylized UI button with advanced visual effects like animated underlines and a "glitch" hover effect.
 * This component is fully interactive and integrates with a centralized input handling system.
 */
public final class ViceButton implements InteractiveComponent, MenuComponent {

    private final Node buttonNode;
    private final BitmapText label;
    private final BitmapText glitchLabel;
    private final Geometry underline;
    private final Runnable action;
    private final Style style;

    private final Vector2f position = new Vector2f();
    private float width, height;
    private boolean isSelected = false; // Persistent state (e.g., active tab)
    private boolean isHovered = false;  // Transient state (cursor over element)
    private boolean isUnderlineVisible = true;

    // Animation state
    private final ColorRGBA currentLabelColor = new ColorRGBA();
    private float currentUnderlineScaleX = 0f;
    private float time = 0f;

    public ViceButton(AssetManager assetManager, String text, Style style, Runnable action) {
        this.action = action;
        this.style = style;
        this.buttonNode = new Node("ViceButton: " + text);

        this.label = new BitmapText(assetManager.loadFont(style.fontPath()));
        label.setText(text.toUpperCase());
        label.setColor(style.defaultColor());
        currentLabelColor.set(style.defaultColor());

        this.glitchLabel = (BitmapText) label.clone();
        glitchLabel.setColor(style.glowColor().mult(new ColorRGBA(1, 1, 1, 0.7f)));
        glitchLabel.setAlpha(0);

        Material underlineMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        underlineMat.setColor("Color", style.glowColor());
        underlineMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        this.underline = new Geometry("ButtonUnderline", new Quad(1, style.underlineHeight()));
        underline.setMaterial(underlineMat);
        underline.setLocalScale(0f, 1f, 1f);

        buttonNode.attachChild(label);
        buttonNode.attachChild(glitchLabel);
        buttonNode.attachChild(underline);
    }

    /**
     * Sets the persistent selection state of the button (e.g., for an active tab).
     * This is different from the transient hover state.
     */
    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }

    @Override
    public void update(float tpf) {
        time += tpf;
        float lerpFactor = FastMath.clamp(tpf * style.animationSpeed(), 0, 1);
        boolean shouldGlow = isSelected || isHovered;

        // Animate label color
        ColorRGBA targetColor = shouldGlow ? style.selectedColor() : style.defaultColor();
        currentLabelColor.interpolateLocal(targetColor, lerpFactor);
        label.setColor(currentLabelColor);

        // Animate underline
        float targetUnderlineScale = (shouldGlow && isUnderlineVisible) ? 1.0f : 0.0f;
        currentUnderlineScaleX = FastMath.interpolateLinear(lerpFactor, currentUnderlineScaleX, targetUnderlineScale);
        underline.setLocalScale(width * currentUnderlineScaleX, 1f, 1f);
        float underlineX = (width - (width * currentUnderlineScaleX)) / 2f;
        underline.setLocalTranslation(underlineX, style.underlineYOffset(), 0);

        // Animate glitch effect
        float targetGlitchAlpha = isHovered ? 1.0f : 0.0f;
        float currentGlitchAlpha = glitchLabel.getAlpha();
        glitchLabel.setAlpha(FastMath.interpolateLinear(lerpFactor * 1.5f, currentGlitchAlpha, targetGlitchAlpha));
        if (currentGlitchAlpha > 0.01f) {
            float glitchOffset = (FastMath.sin(time * 50f) + FastMath.sin(time * 27f)) * 0.5f * style.glitchIntensity();
            glitchLabel.setLocalTranslation(label.getLocalTranslation().x + glitchOffset, label.getLocalTranslation().y, 0.5f);
        }
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        centerLabel();
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
        this.buttonNode.setLocalTranslation(x, y, 0);
    }

    public void setLabelSize(float size) {
        label.setSize(size);
        glitchLabel.setSize(size);
        centerLabel();
    }

    public void setUnderlineVisible(boolean visible) {
        this.isUnderlineVisible = visible;
    }

    private void centerLabel() {
        if (width == 0 || height == 0) return;
        float textWidth = label.getLineWidth();
        float textHeight = label.getLineHeight();
        float textX = (width - textWidth) / 2f;
        float textY = (height + textHeight) / 2f;
        label.setLocalTranslation(textX, textY, 1);
        glitchLabel.setLocalTranslation(textX, textY, 0.5f);
    }

    public void executeAction() {
        if (!isActive || action == null) return;
        action.run();
    }

    @Override
    public boolean intersects(Vector2f globalCursorPos) {
        if (!isActive) return false;
        Vector2f worldPos = new Vector2f(buttonNode.getWorldTranslation().x, buttonNode.getWorldTranslation().y);
        return globalCursorPos.x >= worldPos.x && globalCursorPos.x <= worldPos.x + this.width &&
                globalCursorPos.y >= worldPos.y && globalCursorPos.y <= worldPos.y + this.height;
    }

    @Override
    public Node getNode() {
        return buttonNode;
    }

    // --- Implementation of InteractiveComponent ---

    @Override
    public void setHovered(boolean hovered) {
        this.isHovered = hovered;
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        // Simple buttons execute their action on release, handled by the InputHandler.
    }

    @Override
    public void handleMouseDrag(Vector2f cursor) {
        // Not applicable for a simple button.
    }

    @Override
    public void handleMouseRelease() {
        // Not applicable for a simple button.
    }

    private boolean isActive = true; // Новое поле

    @Override
    public void setActive(boolean active) {
        this.isActive = active;
        if (!active) {
            setHovered(false); // Сбрасываем наведение, если компонент стал неактивным
        }
    }

    // --- Getters for layout calculations ---

    public Vector2f getPosition() { return position; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }

    /**
     * A record that encapsulates all visual and animation properties for a {@link ViceButton}.
     */
    public record Style(
            ColorRGBA defaultColor,
            ColorRGBA selectedColor,
            ColorRGBA glowColor,
            String fontPath,
            float animationSpeed,
            float underlineHeight,
            float underlineYOffset,
            float glitchIntensity
    ) {
        public static Style getViceStyle() {
            String font = "Interface/Fonts/Default.fnt";
            return new Style(
                    ColorRGBA.White.clone(),
                    new ColorRGBA(1f, 0.2f, 0.6f, 1f), // Vibrant Pink
                    new ColorRGBA(1f, 0.2f, 0.6f, 1f).mult(2.5f), // Glowing Pink for Bloom
                    font,
                    15f,
                    2.5f,
                    -5f,
                    2.0f
            );
        }
    }
}