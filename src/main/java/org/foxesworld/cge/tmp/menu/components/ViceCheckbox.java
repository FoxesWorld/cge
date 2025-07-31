package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.jme3.ui.Picture;

/**
 * A highly stylized and animated checkbox component that is fully interactive
 * and integrates with a centralized input handling system.
 */
public final class ViceCheckbox implements InteractiveComponent, MenuComponent {

    // --- Style & Animation Constants ---
    private static final ColorRGBA COLOR_FRAME_DEFAULT = new ColorRGBA(0.7f, 0.7f, 0.7f, 0.8f);
    private static final ColorRGBA COLOR_FRAME_HOVER = ColorRGBA.White.clone();
    private static final ColorRGBA COLOR_TEXT = ColorRGBA.White.clone();
    private static final ColorRGBA COLOR_DISABLED_TINT = new ColorRGBA(0.6f, 0.6f, 0.6f, 0.5f);
    private static final float ANIMATION_SPEED = 15f;
    private static final float LABEL_GAP = 15f;
    private static final float LABEL_SCALE_HOVER = 1.05f;

    private final Node checkboxNode;
    private final BitmapText label;
    private final Picture frame;
    private final Picture checkMark;
    private final String bind;

    private boolean isChecked;
    private boolean isActive = true;
    private boolean isHovered = false; // Internal hover state
    private float size;
    private float baseLabelSize;

    // Animation state
    private final ColorRGBA currentFrameColor = new ColorRGBA();
    private float currentCheckScale = 0f;
    private float currentCheckAlpha = 0f;
    private float currentLabelScale = 1f;

    public ViceCheckbox(AssetManager assetManager, String text, String fontPath, boolean initialState, String bind) {
        this.isChecked = initialState;
        this.bind = bind;
        this.checkboxNode = new Node("ViceCheckbox: " + text);

        this.label = new BitmapText(assetManager.loadFont(fontPath));
        label.setText(text.toUpperCase());
        label.setColor(COLOR_TEXT);

        this.frame = new Picture("CheckboxFrame");
        frame.setImage(assetManager, "assets/Interface/Icons/checkbox-frame.png", true);
        Material frameMat = frame.getMaterial();
        frameMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        currentFrameColor.set(COLOR_FRAME_DEFAULT);

        this.checkMark = new Picture("CheckboxCheckMark");
        checkMark.setImage(assetManager, "assets/Interface/Icons/check-mark.png", true);
        Material checkMat = checkMark.getMaterial();
        checkMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        checkboxNode.attachChild(label);
        checkboxNode.attachChild(frame);
        checkboxNode.attachChild(checkMark);

        // Set initial state without animation
        currentCheckScale = isChecked ? 1.0f : 0.8f;
        currentCheckAlpha = isChecked ? 1.0f : 0.0f;
        update(1.0f); // Apply instantly
    }

    public void setSize(float size) {
        this.size = size;
        this.baseLabelSize = size * 0.8f;
        frame.setWidth(size);
        frame.setHeight(size);
        checkMark.setWidth(size);
        checkMark.setHeight(size);
        label.setSize(baseLabelSize);

        float labelY = (size - label.getLineHeight()) / 2f;
        label.setLocalTranslation(size + LABEL_GAP, size - labelY, 0);
    }

    public void setPosition(float x, float y) {
        checkboxNode.setLocalTranslation(x, y, 0);
    }

    public void toggle() {
        this.isChecked = !this.isChecked;
        System.out.println("Setting '" + bind + "' toggled to: " + this.isChecked);
    }

    @Override
    public void update(float tpf) {
        float lerpFactor = FastMath.clamp(tpf * ANIMATION_SPEED, 0, 1);

        boolean canInteract = isActive && isHovered;
        ColorRGBA targetFrameColor = canInteract ? COLOR_FRAME_HOVER : COLOR_FRAME_DEFAULT;
        float targetCheckScale = isChecked ? 1.0f : 0.8f;
        float targetCheckAlpha = isChecked ? 1.0f : 0.0f;
        float targetLabelScale = canInteract ? LABEL_SCALE_HOVER : 1.0f;

        ColorRGBA finalLabelColor = COLOR_TEXT;
        ColorRGBA finalCheckColor = ColorRGBA.White;

        if (!isActive) {
            targetFrameColor = targetFrameColor.mult(COLOR_DISABLED_TINT);
            finalLabelColor = COLOR_TEXT.mult(COLOR_DISABLED_TINT);
            finalCheckColor = ColorRGBA.White.mult(COLOR_DISABLED_TINT);
        }

        label.setColor(finalLabelColor);
        currentFrameColor.interpolateLocal(targetFrameColor, lerpFactor);
        frame.getMaterial().setColor("Color", currentFrameColor);

        currentCheckScale = FastMath.interpolateLinear(lerpFactor, currentCheckScale, targetCheckScale);
        currentCheckAlpha = FastMath.interpolateLinear(lerpFactor, currentCheckAlpha, targetCheckAlpha);
        checkMark.setLocalScale(currentCheckScale);
        float offset = (size - (size * currentCheckScale)) / 2f;
        checkMark.setLocalTranslation(offset, offset, 0.1f);
        finalCheckColor.a = currentCheckAlpha;
        checkMark.getMaterial().setColor("Color", finalCheckColor);

        currentLabelScale = FastMath.interpolateLinear(lerpFactor, currentLabelScale, targetLabelScale);
        label.setSize(baseLabelSize * currentLabelScale);
    }


    @Override
    public boolean intersects(Vector2f globalCursorPos) {
        if (!isActive) return false;
        Vector2f worldPos = new Vector2f(checkboxNode.getWorldTranslation().x, checkboxNode.getWorldTranslation().y);
        float totalWidth = size + LABEL_GAP + label.getLineWidth();
        return globalCursorPos.x >= worldPos.x && globalCursorPos.x <= worldPos.x + totalWidth &&
                globalCursorPos.y >= worldPos.y && globalCursorPos.y <= worldPos.y + size;
    }

    @Override
    public Node getNode() {
        return checkboxNode;
    }

    // --- Implementation of InteractiveComponent ---

    @Override
    public void setActive(boolean active) {
        this.isActive = active;
        if (!active) {
            setHovered(false);
        }
    }

    @Override
    public void setHovered(boolean hovered) {
        if (!isActive) {
            this.isHovered = false;
            return;
        }
        this.isHovered = hovered;
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        // The 'toggle' action is handled by the InputHandler upon a successful click (press and release on the same component).
    }

    @Override
    public void handleMouseDrag(Vector2f cursor) {
        // Not applicable for a checkbox.
    }

    @Override
    public void handleMouseRelease() {
        // Not applicable for a checkbox.
    }

    // --- Getters ---

    public boolean isChecked() {
        return isChecked;
    }

    public String getBind() {
        return bind;
    }
}