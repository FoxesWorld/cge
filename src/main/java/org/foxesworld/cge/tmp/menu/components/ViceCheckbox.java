package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.*;
import com.jme3.scene.Node;
import com.jme3.ui.Picture;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.MenuComponent;
import org.foxesworld.cge.tmp.menu.components.utils.SoundComponent;
import org.foxesworld.cge.tmp.menu.xml.CheckboxXml;

public final class ViceCheckbox extends UIComponent implements InteractiveComponent, MenuComponent, SoundComponent {

    private static final float ANIM_SPEED = 20f;
    private static final float LABEL_GAP_MULTIPLIER = 0.45f;
    private static final float CHECK_BOUNCE_MULTIPLIER = 1.15f;
    private static final float CHECK_ROTATION_DEGREES = -20f;

    private static final ColorRGBA COLOR_FRAME = new ColorRGBA(0.8f, 0.8f, 0.8f, 0.9f);
    private static final ColorRGBA COLOR_HOVER = ColorRGBA.White.clone();
    private static final ColorRGBA COLOR_CHECK = new ColorRGBA(0.3f, 1f, 0.3f, 1f);
    private static final ColorRGBA COLOR_DISABLED_FRAME = new ColorRGBA(0.5f, 0.5f, 0.5f, 0.5f);
    private static final ColorRGBA COLOR_DISABLED_LABEL = new ColorRGBA(0.6f, 0.6f, 0.6f, 0.7f);

    private static final String ICON_FRAME_PATH = "assets/Interface/Icons/checkbox-frame.png";
    private static final String ICON_CHECK_PATH = "assets/Interface/Icons/check-mark.png";

    private final Picture framePicture;
    private final Picture checkPicture;
    private final BitmapText labelText;
    private final Material checkMaterial;

    private final String bind;
    private boolean isChecked;
    private boolean isActive = true;
    private boolean isHovered;

    private final ColorRGBA currentFrameColor = COLOR_FRAME.clone();
    private float currentCheckSize;
    private float currentCheckRotation;
    private float currentLabelScale = 1f;

    private float size;
    private float baseLabelSize;

    public ViceCheckbox(AssetManager assets, String fontPath, CheckboxXml checkboxXml) {
        super(checkboxXml.id);
        this.bind = checkboxXml.bind;
        this.isChecked = checkboxXml.checked;

        node.setName("ViceCheckbox:" + id);

        framePicture = new Picture("frame");
        framePicture.setImage(assets, ICON_FRAME_PATH, true);
        framePicture.getMaterial().getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        checkPicture = new Picture("check");
        checkPicture.setImage(assets, ICON_CHECK_PATH, true);
        checkMaterial = checkPicture.getMaterial();
        checkMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        checkMaterial.setColor("Color", COLOR_CHECK);

        BitmapFont font = assets.loadFont("Interface/Fonts/Default.fnt");
        labelText = new BitmapText(font);
        labelText.setText(checkboxXml.text);
        labelText.setColor(ColorRGBA.White);
        labelText.setBox(null); // not forcing a box

        node.attachChild(framePicture);
        node.attachChild(checkPicture);
        node.attachChild(labelText);

        // sensible default size
        setSize(24f);
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

        checkPicture.setLocalRotation(new Quaternion().fromAngleAxis(currentCheckRotation * FastMath.DEG_TO_RAD, new Vector3f(0, 0, 1)));

        float alpha = FastMath.clamp(size > 0f ? currentCheckSize / size : 0f, 0f, 1f);
        checkMaterial.setColor("Color", new ColorRGBA(COLOR_CHECK.r, COLOR_CHECK.g, COLOR_CHECK.b, alpha));
    }

    private void updateLabelVisuals(float lerp) {
        float targetScale = (isActive && isHovered) ? 1.05f : 1f;
        currentLabelScale = FastMath.interpolateLinear(lerp, currentLabelScale, targetScale);
        labelText.setSize(baseLabelSize * currentLabelScale);
        labelText.setColor(isActive ? ColorRGBA.White : COLOR_DISABLED_LABEL);
        // reposition after size change
        repositionLabel();
    }

    @Override
    public void setSize(float width, float height) {
        // keep API compatible: set by height primarily
        setSize(height);
    }

    public void setSize(float newSize) {
        if (newSize <= 0f) newSize = 1f;
        this.size = newSize;

        // label size chosen relative to checkbox size; slightly smaller than box
        this.baseLabelSize = newSize * 0.72f;

        framePicture.setWidth(size);
        framePicture.setHeight(size);

        this.currentCheckSize = isChecked ? size : 0f;
        this.currentCheckRotation = isChecked ? 0f : CHECK_ROTATION_DEGREES;
        applyCheckTransform();

        labelText.setSize(baseLabelSize * currentLabelScale);

        repositionLabel();
    }

    private void repositionLabel() {
        float gap = size * LABEL_GAP_MULTIPLIER;
        float labelX = size + gap;

        // ensure BitmapText metrics are up-to-date by using its current size
        float textWidth = labelText.getLineWidth();
        float textHeight = labelText.getLineHeight();

        // BitmapText baseline coordinate: setLocalTranslation(x, y), y is the baseline position.
        // To vertically center text inside box of height 'size', set baseline at (boxTop + textHeight/2 + (size - textHeight)/2)
        // Simpler and reliable: baseline = size - (size - textHeight)/2
        float baselineY = size - (size - textHeight) / 2f;

        labelText.setLocalTranslation(labelX, baselineY, 1f);
    }

    public void toggle() {
        if (isActive) isChecked = !isChecked;
    }

    @Override
    public Node getNode() {
        return node;
    }

    @Override
    public float getWidth() {
        float gap = size * LABEL_GAP_MULTIPLIER;
        float textWidth = labelText.getLineWidth();
        return size + gap + textWidth;
    }

    @Override
    public float getHeight() {
        float textH = labelText.getLineHeight();
        return Math.max(size, textH);
    }

    @Override
    public void setActive(boolean active) {
        this.isActive = active;
        if (!active) isHovered = false;
    }

    @Override
    public void setHovered(boolean hovered) {
        if (isActive) this.isHovered = hovered;
    }

    @Override
    public boolean intersects(Vector2f cursor) {
        Vector3f worldPos = node.getWorldTranslation();
        return cursor.x >= worldPos.x && cursor.x <= worldPos.x + getWidth() &&
                cursor.y >= worldPos.y && cursor.y <= worldPos.y + getHeight();
    }

    public void setPosition(float x, float y) {
        node.setLocalTranslation(x, y, 0);
    }

    public boolean isChecked() {
        return isChecked;
    }

    public String getBind() {
        return bind;
    }

    @Override public void handleMousePress(Vector2f cursor) {}
    @Override public void handleMouseDrag(Vector2f cursor) {}
    @Override public void handleMouseRelease() {}
}
