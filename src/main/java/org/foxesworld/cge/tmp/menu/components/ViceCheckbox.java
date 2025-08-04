package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.ui.Picture;

/**
 * A modern, animated checkbox component for a jMonkeyEngine UI.
 * Features a smooth "bounce" animation for the checkmark, which is rendered
 * from a texture and correctly scales and rotates from the center of its frame.
 */
public final class ViceCheckbox implements InteractiveComponent, MenuComponent, SoundComponent {

    // --- Константы для настройки ---
    private static final float ANIM_SPEED = 20f;
    private static final float LABEL_GAP_MULTIPLIER = 0.4f;
    private static final float CHECK_BOUNCE_MULTIPLIER = 1.15f; // "Отскок" до 115% от размера рамки
    private static final float CHECK_ROTATION_DEGREES = -20f;

    // --- Цвета ---
    private static final ColorRGBA COLOR_FRAME = new ColorRGBA(0.8f, 0.8f, 0.8f, 0.9f);
    private static final ColorRGBA COLOR_HOVER = ColorRGBA.White.clone();
    private static final ColorRGBA COLOR_CHECK = new ColorRGBA(0.3f, 1f, 0.3f, 1f);
    private static final ColorRGBA COLOR_DISABLED_FRAME = new ColorRGBA(0.5f, 0.5f, 0.5f, 0.5f);
    private static final ColorRGBA COLOR_DISABLED_LABEL = new ColorRGBA(0.6f, 0.6f, 0.6f, 0.7f);

    // --- Ресурсы ---
    private static final String ICON_FRAME_PATH = "assets/Interface/Icons/checkbox-frame.png";
    private static final String ICON_CHECK_PATH = "assets/Interface/Icons/check-mark.png";

    // --- JME объекты ---
    private final Node rootNode = new Node("ViceCheckbox");
    private final Picture framePicture;
    private final Picture checkPicture;
    private final BitmapText labelText;
    private final Material checkMaterial;

    // --- Состояние ---
    private final String bind;
    private boolean isChecked;
    private boolean isActive = true;
    private boolean isHovered;

    // --- Анимация ---
    private final ColorRGBA currentFrameColor = COLOR_FRAME.clone();
    private float currentCheckSize;
    private float currentCheckRotation;
    private float currentLabelScale = 1f;

    private float size;
    private float baseLabelSize;

    public ViceCheckbox(AssetManager assets, String text, String fontPath, boolean initialChecked, String bind) {
        this.bind = bind;
        this.isChecked = initialChecked;

        framePicture = new Picture("frame");
        framePicture.setImage(assets, ICON_FRAME_PATH, true);
        framePicture.getMaterial().getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        checkPicture = new Picture("check");
        checkPicture.setImage(assets, ICON_CHECK_PATH, true);
        checkMaterial = checkPicture.getMaterial();
        checkMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        checkMaterial.setColor("Color", COLOR_CHECK);

        BitmapFont font = assets.loadFont(fontPath);
        labelText = new BitmapText(font);
        labelText.setText(text);
        labelText.setColor(ColorRGBA.White);

        rootNode.attachChild(framePicture);
        rootNode.attachChild(checkPicture);
        rootNode.attachChild(labelText);
    }

    @Override
    public void update(float tpf) {
        if (size == 0) return;

        float lerpFactor = FastMath.clamp(tpf * ANIM_SPEED, 0f, 1f);

        updateFrameVisuals(lerpFactor);
        updateCheckMarkVisuals(lerpFactor);
        updateLabelVisuals(lerpFactor);
    }

    private void updateFrameVisuals(float lerp) {
        ColorRGBA targetColor = isActive ? (isHovered ? COLOR_HOVER : COLOR_FRAME) : COLOR_DISABLED_FRAME;
        currentFrameColor.interpolateLocal(targetColor, lerp);
        framePicture.getMaterial().setColor("Color", currentFrameColor);
    }

    private void updateCheckMarkVisuals(float lerp) {
        float targetSize;
        if (isActive && isChecked) {
            float bounceSize = size * CHECK_BOUNCE_MULTIPLIER;
            targetSize = (currentCheckSize < (bounceSize * 0.98f) && currentCheckSize < size) ? bounceSize : size;
        } else {
            targetSize = 0f;
        }

        float targetRotation = (isActive && isChecked) ? 0f : CHECK_ROTATION_DEGREES;

        currentCheckSize = FastMath.interpolateLinear(lerp, currentCheckSize, targetSize);
        currentCheckRotation = FastMath.interpolateLinear(lerp, currentCheckRotation, targetRotation);

        applyCheckTransform();
    }

    private void applyCheckTransform() {
        checkPicture.setWidth(currentCheckSize);
        checkPicture.setHeight(currentCheckSize);

        float offset = (size - currentCheckSize) / 2f;
        checkPicture.setLocalTranslation(offset, offset, 1f);

        checkPicture.setLocalRotation(new Quaternion().fromAngleAxis(currentCheckRotation * FastMath.DEG_TO_RAD, Vector3f.UNIT_Z));

        float alpha = FastMath.clamp(currentCheckSize / size, 0f, 1f);
        checkMaterial.setColor("Color", new ColorRGBA(COLOR_CHECK.r, COLOR_CHECK.g, COLOR_CHECK.b, alpha));
    }

    private void updateLabelVisuals(float lerp) {
        float targetScale = (isActive && isHovered) ? 1.05f : 1f;
        currentLabelScale = FastMath.interpolateLinear(lerp, currentLabelScale, targetScale);
        labelText.setSize(baseLabelSize * currentLabelScale);
        labelText.setColor(isActive ? ColorRGBA.White : COLOR_DISABLED_LABEL);
    }

    @Override
    public void setSize(float width, float height) {
        setSize(height);
    }

    public void setSize(float newSize) {
        this.size = newSize;
        this.baseLabelSize = newSize * 0.75f;

        framePicture.setWidth(size);
        framePicture.setHeight(size);

        this.currentCheckSize = isChecked ? size : 0f;
        this.currentCheckRotation = 0f;
        applyCheckTransform();

        labelText.setSize(baseLabelSize);
        float labelX = size + (size * LABEL_GAP_MULTIPLIER);
        float labelY = (size - labelText.getLineHeight()) / 2f + 25f;
        labelText.setLocalTranslation(labelX, labelY, 1f);
    }

    public void toggle() {
        if (isActive) isChecked = !isChecked;
    }

    // --- Getters и методы интерфейсов ---

    /**
     * Returns the root node of this component, which can be attached to a scene graph.
     * This method fulfills the contract of the MenuComponent interface.
     * @return The root Node containing all visual elements of this checkbox.
     */
    @Override
    public Node getNode() {
        return rootNode;
    }

    @Override public float getWidth() { return size + (size * LABEL_GAP_MULTIPLIER) + labelText.getLineWidth(); }
    @Override public float getHeight() { return size; }
    @Override public void setActive(boolean active) { this.isActive = active; if (!active) isHovered = false; }
    @Override public void setHovered(boolean hovered) { if (isActive) this.isHovered = hovered; }
    @Override public boolean intersects(Vector2f cursor) {
        Vector3f worldPos = rootNode.getWorldTranslation();
        return cursor.x >= worldPos.x && cursor.x <= worldPos.x + getWidth() &&
                cursor.y >= worldPos.y && cursor.y <= worldPos.y + getHeight();
    }
    public void setPosition(float x, float y) { rootNode.setLocalTranslation(x, y, 0); }
    public boolean isChecked() { return isChecked; }
    public String getBind() { return bind; }
    @Override public void handleMousePress(Vector2f cursor) {}
    @Override public void handleMouseDrag(Vector2f cursor) {}
    @Override public void handleMouseRelease() {}
}