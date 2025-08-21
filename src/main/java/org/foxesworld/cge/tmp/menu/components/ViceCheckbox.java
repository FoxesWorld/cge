package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.*;
import com.jme3.scene.Node;
import com.jme3.ui.Picture;
import org.foxesworld.cge.core.io.TTFrenderer;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.SoundComponent;
import org.foxesworld.cge.tmp.menu.xml.CheckboxXml;

/**
 * ViceCheckbox — атмосферный чекбокс в стиле игрового UI.
 * Hover-эффект: additive glow + мягкое увеличение рамки и текста.
 * Если чекбокс включен — он масштабируется вместе с галочкой.
 * Использует TTFrenderer для текста (устраняет ClassCastException при загрузке шрифтов).
 */
public final class ViceCheckbox extends UIComponent implements InteractiveComponent, SoundComponent {

    // animation tuning
    private static final float DEFAULT_ANIM_SPEED = 18f;
    private static final float LABEL_EASE_SPEED = 10f;
    private static final float CHECK_SETTLE_SPEED = 12f;

    // resources
    private static final String ICON_FRAME_PATH = "assets/Interface/ui/icons/checkbox/checkbox-frame.png";
    private static final String ICON_CHECK_PATH = "assets/Interface/ui/icons/checkbox/check-mark.png";
    private static final String DEFAULT_FONT_PATH = "assets/Interface/fonts/FSElliotPro.ttf";

    // visuals
    private final Picture framePicture;
    private final Picture glowPicture;      // additive glow overlay
    private final Picture checkPicture;
    private final Picture shadowPicture;
    private final Material checkMaterial;

    // text renderer (replaces BitmapText)
    private final TTFrenderer ttfRenderer;

    // state
    private final String bind;
    private boolean isChecked = false;
    private boolean isActive = true;
    private boolean isHovered = false;

    // animation state
    private final Quaternion rotTmp = new Quaternion();
    private final ColorRGBA checkTintTemp = new ColorRGBA();
    private final ColorRGBA glowColor = new ColorRGBA(0.55f, 0.9f, 1f, 0f);

    private float size = 24f;
    private float baseLabelSize = 16f;
    private float currentCheckSize = 0f;
    private float currentCheckRotation = -20f;
    private float labelScale = 1f;
    private float labelGap = 0f;

    // hover dynamics
    private float hoverAlpha = 0f;   // 0..1
    private float hoverScale = 1f;   // 1..1.06

    // checked dynamics (new) — scales when checked
    private float checkedScale = 1f; // 1..1.06 (or tunable)
    private float checkedScaleTarget = 1.06f; // target scale when checked

    public ViceCheckbox(AssetManager assets, String fontPath, CheckboxXml checkboxXml) {
        super(checkboxXml.id);
        this.bind = checkboxXml.bind;
        setName("ViceCheckbox:" + id);

        // shadow (subtle)
        shadowPicture = new Picture("checkbox-shadow");
        shadowPicture.setImage(assets, ICON_FRAME_PATH, true);
        Material shadowMat = shadowPicture.getMaterial();
        shadowMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        shadowMat.setColor("Color", new ColorRGBA(0f, 0f, 0f, 0.28f));
        attachChild(shadowPicture);

        // frame
        framePicture = new Picture("checkbox-frame");
        framePicture.setImage(assets, ICON_FRAME_PATH, true);
        Material frameMat = framePicture.getMaterial();
        frameMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        frameMat.setColor("Color", new ColorRGBA(0.82f, 0.82f, 0.82f, 0.98f));
        attachChild(framePicture);

        // glow overlay (additive)
        glowPicture = new Picture("checkbox-glow");
        glowPicture.setImage(assets, ICON_FRAME_PATH, true);
        Material glowMat = glowPicture.getMaterial();
        glowMat.getAdditionalRenderState().setBlendMode(BlendMode.Additive);
        glowMat.setColor("Color", glowColor.clone());
        attachChild(glowPicture);

        // check
        checkPicture = new Picture("checkbox-check");
        checkPicture.setImage(assets, ICON_CHECK_PATH, true);
        checkMaterial = checkPicture.getMaterial();
        checkMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        checkMaterial.setColor("Color", new ColorRGBA(0.28f, 1f, 0.28f, 1f));
        attachChild(checkPicture);

        // text via TTFrenderer (avoids AssetManager.loadFont issues)
        ttfRenderer = new TTFrenderer(assets);
        String fp = (fontPath == null || fontPath.isBlank()) ? DEFAULT_FONT_PATH : fontPath;
        ttfRenderer.generateFont(fp, com.atr.jme.font.util.Style.Plain, Math.max(10, (int) (baseLabelSize)));
        ttfRenderer.generateText(com.jme3.math.ColorRGBA.White, checkboxXml.text == null ? "" : checkboxXml.text);
        attachChild(ttfRenderer.getTextGeometry());

        // initialize sizes/positions
        setSize(24f);
        applyCheckTransform(1.8f);
        updateLabelPosition(1.7f);
    }

    @Override
    public void update(float tpf) {
        if (size <= 0f) return;

        float frameLerp = 1f - FastMath.pow(1f - 0.12f, tpf * DEFAULT_ANIM_SPEED);
        float labelLerp = 1f - FastMath.pow(1f - 0.12f, tpf * LABEL_EASE_SPEED);
        float checkLerp = 1f - FastMath.pow(1f - 0.12f, tpf * CHECK_SETTLE_SPEED);

        // frame tint (hover / disabled)
        ColorRGBA frameTarget = isActive ? (isHovered ? ColorRGBA.White.clone() : new ColorRGBA(0.82f, 0.82f, 0.82f, 0.98f))
                : new ColorRGBA(0.52f, 0.52f, 0.52f, 0.55f);
        ColorRGBA currentFrameColor = (ColorRGBA) framePicture.getMaterial().getParam("Color").getValue();
        currentFrameColor.r = FastMath.interpolateLinear(frameLerp, currentFrameColor.r, frameTarget.r);
        currentFrameColor.g = FastMath.interpolateLinear(frameLerp, currentFrameColor.g, frameTarget.g);
        currentFrameColor.b = FastMath.interpolateLinear(frameLerp, currentFrameColor.b, frameTarget.b);
        currentFrameColor.a = FastMath.interpolateLinear(frameLerp, currentFrameColor.a, frameTarget.a);
        framePicture.getMaterial().setColor("Color", currentFrameColor);

        // hover alpha & scale interpolation
        float targetHover = (isActive && isHovered) ? 1f : 0f;
        hoverAlpha = FastMath.interpolateLinear(frameLerp, hoverAlpha, targetHover);
        float targetHoverScale = (isActive && isHovered) ? 1.06f : 1f;
        hoverScale = FastMath.interpolateLinear(frameLerp, hoverScale, targetHoverScale);

        // checked scale interpolation
        float targetChecked = (isActive && isChecked) ? checkedScaleTarget : 1f;
        checkedScale = FastMath.interpolateLinear(frameLerp, checkedScale, targetChecked);

        // effective size accounts for base size, hover and checked multipliers
        float effectiveSize = size * hoverScale * checkedScale;

        // apply glow alpha & size (glow slightly larger than effective frame)
        ColorRGBA gc = glowColor.clone();
        gc.a = hoverAlpha * 0.9f;
        glowPicture.getMaterial().setColor("Color", gc);
        float glowSize = effectiveSize * (1f + 0.12f * hoverAlpha);
        glowPicture.setWidth(glowSize);
        glowPicture.setHeight(glowSize);
        float glowOffset = (glowSize - effectiveSize) * 0.5f;
        glowPicture.setLocalTranslation(-glowOffset, glowOffset, 0.02f);

        // set frame to effective size and position (frame Picture uses top-left origin)
        framePicture.setWidth(effectiveSize);
        framePicture.setHeight(effectiveSize);
        // keep frame visually centered on its origin (so checkbox origin remains stable)
        float frameOffset = (size - effectiveSize) * 0.5f;
        framePicture.setLocalTranslation(frameOffset, -frameOffset, 0f);

        // label scale on hover
        float targetLabelScale = (isActive && isHovered) ? 1.06f : 1f;
        labelScale = FastMath.interpolateLinear(labelLerp, labelScale, targetLabelScale);
        ttfRenderer.generateText(isActive ? ColorRGBA.White : new ColorRGBA(0.62f, 0.62f, 0.62f, 0.78f), ttfRenderer.getTextGeometry().getText());
        ttfRenderer.getTextGeometry().setLocalTranslation(ttfRenderer.getTextGeometry().getLocalTranslation().x,
                (getHeight() + ttfRenderer.getTextGeometry().getHeight()) / 2f - 1f + (labelScale - 1f) * 2f, 1f);

        // check animation: target is relative to effective size so check grows with checked+hover scale
        float targetCheckSize = (isActive && isChecked) ? effectiveSize * 1.18f : 0f;
        currentCheckSize = FastMath.interpolateLinear(checkLerp, currentCheckSize, targetCheckSize);

        float finalDesired = (isActive && isChecked) ? effectiveSize : 0f;
        if (isChecked && Math.abs(currentCheckSize - targetCheckSize) < 0.5f) {
            currentCheckSize = FastMath.interpolateLinear(1f - FastMath.pow(1f - 0.2f, tpf * (CHECK_SETTLE_SPEED + 6f)), currentCheckSize, finalDesired);
        } else if (!isChecked && currentCheckSize < 0.5f) {
            currentCheckSize = 0f;
        }

        float targetRotation = (isActive && isChecked) ? 0f : -20f;
        currentCheckRotation = FastMath.interpolateLinear(checkLerp, currentCheckRotation, targetRotation);

        applyCheckTransform(effectiveSize);

        // shadow sized relative to effectiveSize
        shadowPicture.setWidth(effectiveSize * 0.98f);
        shadowPicture.setHeight(effectiveSize * 0.98f);
        shadowPicture.setLocalTranslation(2f, -2f, 0f);

        updateLabelPosition(effectiveSize);
        ttfRenderer.update(tpf);
    }

    private void applyCheckTransform(float effectiveSize) {
        float cs = Math.max(0f, currentCheckSize);
        checkPicture.setWidth(cs);
        checkPicture.setHeight(cs);

        // center check inside effective frame
        float offset = (effectiveSize - cs) * 0.5f;
        checkPicture.setLocalTranslation(offset, offset, 1f);

        rotTmp.fromAngleAxis(currentCheckRotation * FastMath.DEG_TO_RAD, Vector3f.UNIT_Z);
        checkPicture.setLocalRotation(rotTmp);

        float alpha = effectiveSize > 0f ? FastMath.clamp(currentCheckSize / effectiveSize, 0f, 1f) : 0f;
        checkTintTemp.set(0.28f, 1f, 0.28f, alpha);
        checkMaterial.setColor("Color", checkTintTemp);
    }

    private void updateLabelPosition(float effectiveSize) {
        labelGap = effectiveSize * 0.5f;
        float labelX = effectiveSize + labelGap;
        float textH = ttfRenderer.getTextGeometry().getHeight();
        float y = (effectiveSize + textH) / 2f - 1f;
        ttfRenderer.getTextGeometry().setLocalTranslation(labelX, y, 1f);
    }

    // API
    @Override
    public void setSize(float width, float height) {
        setSize(height);
    }

    public void setSize(float newSize) {
        if (newSize <= 0f) newSize = 1f;
        this.size = newSize;
        this.baseLabelSize = Math.max(6f, newSize * 0.72f);

        // initial effective values (no hover/checked yet)
        float effectiveSize = size * hoverScale * checkedScale;
        framePicture.setWidth(effectiveSize);
        framePicture.setHeight(effectiveSize);

        glowPicture.setWidth(effectiveSize);
        glowPicture.setHeight(effectiveSize);

        shadowPicture.setWidth(effectiveSize * 0.98f);
        shadowPicture.setHeight(effectiveSize * 0.98f);

        // regenerate font with new base size for TTFrenderer (optional)
        try {
            ttfRenderer.generateFont(ttfRenderer.getFontPath(), com.atr.jme.font.util.Style.Plain, Math.max(10, (int) baseLabelSize));
            ttfRenderer.generateText(ColorRGBA.White, ttfRenderer.getTextGeometry().getText());
        } catch (Exception ignored) {}

        currentCheckSize = isChecked ? effectiveSize : 0f;
        currentCheckRotation = isChecked ? 0f : -20f;
        applyCheckTransform(effectiveSize);
        updateLabelPosition(effectiveSize);
    }

    public void toggle() {
        if (!isActive) return;
        setChecked(!isChecked, true);
    }

    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    public void setChecked(boolean checked, boolean withBounce) {
        if (!isActive) {
            this.isChecked = checked;
            return;
        }
        if (this.isChecked == checked) return;
        this.isChecked = checked;
        if (withBounce && checked) {
            // start bounce relative to current combined scale
            this.currentCheckSize = size * checkedScale * 0.6f;
        }
    }

    // InteractiveComponent impl
    @Override public float getWidth() { return size * hoverScale * checkedScale + labelGap + ttfRenderer.getTextGeometry().getWidth(); }
    @Override public float getHeight() { return Math.max(size * hoverScale * checkedScale, ttfRenderer.getTextGeometry().getHeight()); }

    @Override public void setActive(boolean active) { this.isActive = active; if (!active) isHovered = false; }
    @Override public void setHovered(boolean hovered) { if (isActive) this.isHovered = hovered; }

    @Override
    public boolean intersects(Vector2f cursor) {
        Vector3f world = getWorldTranslation();
        float left = world.x;
        float bottom = world.y;
        return cursor.x >= left && cursor.x <= left + getWidth() && cursor.y >= bottom && cursor.y <= bottom + getHeight();
    }

    @Override public void handleMousePress(Vector2f cursor) {}
    @Override public void handleMouseDrag(Vector2f cursor) {}
    @Override public void handleMouseRelease() {}

    public void setPosition(float x, float y) { setLocalTranslation(x, y, 0f); }
    public boolean isChecked() { return isChecked; }
    public String getBind() { return bind; }

    @Override public String getHoverSound() { return "ui.hover"; }
    @Override public String getClickSound() { return "ui.checkbox.toggle"; }
}
