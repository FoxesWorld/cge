package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.ui.Picture;
import org.foxesworld.cge.core.io.TTFrenderer;
import org.foxesworld.cge.core.utils.ColorUtils;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.RoundedQuad;
import org.foxesworld.cge.tmp.menu.components.utils.SoundComponent;

import java.util.Objects;

/**
 * ViceButton — стильная "эпичная" кнопка с подсветкой, тенью и эффектами при ховере/нажатии.
 * Добавлена опция textAlign (LEFT/CENTER/RIGHT) и padding в Style.
 * Исправлен nudge: теперь плавно смещает содержимое при наведении и при нажатии.
 */
public final class ViceButton extends UIComponent implements InteractiveComponent, SoundComponent {

    public enum TextAlign { LEFT, CENTER, RIGHT }

    private final AssetManager assetManager;
    private final TTFrenderer ttfRenderer;
    private Geometry background;

    private final Runnable action;
    private final Style style;

    private final Node contentNode;
    private final Picture icon;

    private final Vector2f position = new Vector2f();
    private final Vector2f currentNudge = new Vector2f();
    private boolean isSelected = false;
    private boolean isHovered = false;
    private boolean isActive = true;
    private boolean pressed = false;
    private boolean pressedInside = false;

    private final ColorRGBA currentLabelColor = new ColorRGBA();
    private final ColorRGBA currentBackgroundColor = new ColorRGBA();
    private float time = 0f;

    private float glowScale = 1f;
    private float glowAlpha = 0f;
    private float flashTimer = 0f;

    public ViceButton(String id, AssetManager assetManager, String text, Style style, Runnable action, String iconPath, float iconSize) {
        super(id);
        this.assetManager = Objects.requireNonNull(assetManager, "AssetManager");
        this.action = action;
        this.style = Objects.requireNonNull(style, "Style");

        setName("ViceButton: " + (text == null ? "" : text));
        this.contentNode = new Node("ButtonContent");
        attachChild(contentNode);

        ttfRenderer = new TTFrenderer(assetManager);
        String fontPath = (style.fontPath == null || style.fontPath.isBlank()) ? "assets/Interface/fonts/FSElliotPro.ttf" : style.fontPath;
        ttfRenderer.generateFont(fontPath, com.atr.jme.font.util.Style.Plain, Math.max(12, (int) (style.baseFontSize())));
        ttfRenderer.generateText(style.defaultColor(), text == null ? "" : text);

        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        ColorRGBA bgColor = ColorUtils.fromHexString(style.backgroundColor());
        bgMat.setColor("Color", bgColor);
        bgMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        background = new Geometry("ButtonBackground", new RoundedQuad(1f, 1f, style.cornerRadius(), 16));
        background.setMaterial(bgMat);
        background.setLocalTranslation(0, 0, -1f);
        attachChild(background);
        currentBackgroundColor.set(bgColor);

        this.icon = new Picture("icon");
        if (iconPath != null && !iconPath.isBlank()) {
            try {
                icon.setImage(assetManager, iconPath, true);
                icon.setWidth(Math.max(0.1f, iconSize));
                icon.setHeight(Math.max(0.1f, iconSize));
                contentNode.attachChild(icon);
            } catch (Exception ignored) {}
        }

        currentLabelColor.set(style.defaultColor());
        contentNode.attachChild(ttfRenderer.getTextGeometry());
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }

    @Override
    public void update(float tpf) {
        time += tpf;
        float smooth = FastMath.clamp(tpf * style.animationSpeed(), 0f, 1f);

        float targetGlowScale = (isHovered && isActive) ? style.hoverGlowScale() : 1f;
        glowScale = FastMath.interpolateLinear(smooth, glowScale, targetGlowScale);
        float targetGlowAlpha = (isHovered && isActive) ? style.maxGlowAlpha() : 0f;
        glowAlpha = FastMath.interpolateLinear(smooth, glowAlpha, targetGlowAlpha);

        ColorRGBA targetBg = (isHovered || pressed) ? ColorUtils.fromHexString(style.hoverBackgroundColor()) : ColorUtils.fromHexString(style.backgroundColor());
        currentBackgroundColor.interpolateLocal(targetBg, smooth);
        try { background.getMaterial().setColor("Color", currentBackgroundColor); } catch (Exception ignored) {}

        ColorRGBA targetLabel = (isHovered || pressed) ? style.selectedColor() : style.defaultColor();
        currentLabelColor.interpolateLocal(targetLabel, smooth);
        ttfRenderer.setColor(currentLabelColor);

        float targetScale = pressed ? style.pressedScale() : 1f;
        float curScale = FastMath.interpolateLinear(smooth, getLocalScale().x, targetScale);
        setLocalScale(curScale, curScale, 1f);

        // --- FIXED: nudge handling (smooth interpolation) ---
        Vector2f targetNudge = pressed ? style.hoverNudge : (isHovered ? style.hoverNudge() : Vector2f.ZERO);
        currentNudge.x = FastMath.interpolateLinear(smooth, currentNudge.x, targetNudge.x);
        currentNudge.y = FastMath.interpolateLinear(smooth, currentNudge.y, targetNudge.y);
        contentNode.setLocalTranslation(currentNudge.x, currentNudge.y, 0f);

        formatContent();
        ttfRenderer.update(tpf);
    }

    public void setSize(float width, float height) {
        setWidth(width);
        setHeight(height);

        background.setMesh(new RoundedQuad(width, height, style.cornerRadius(), 16));
        background.setLocalTranslation(width / 2f, height / 2f, -1f);

        formatContent();
    }

    public void setPosition(float x, float y) {
        position.set(x, y);
        setLocalTranslation(x, y, 0);
    }

    private void formatContent() {
        float padding = Math.max(0f, style.padding());
        float iconW = (icon != null && icon.getWidth() > 0) ? icon.getWidth() : 0f;
        float gap = iconW > 0 ? style.iconGap() : 0f;
        float textW = ttfRenderer.getTextGeometry().getWidth();
        float totalW = iconW + gap + textW;

        // ensure totalW doesn't exceed available width
        float availableW = Math.max(0f, getWidth() - 2f * padding);
        float clampedTotalW = Math.min(totalW, availableW);
        float startX;
        TextAlign align = style.textAlign();

        startX = switch (align) {
            case LEFT -> padding;
            case RIGHT -> getWidth() - clampedTotalW - padding;
            default -> (getWidth() - clampedTotalW) / 2f;
        };

        // if totalW was clamped, shrink text width allocation (we don't change mesh, but shift to avoid overflow)
        float iconOffset = Math.min(iconW, clampedTotalW);
        float textOffset = clampedTotalW - iconOffset - gap;
        if (textOffset < 0f) textOffset = 0f;

        if (iconW > 0 && icon != null) {
            icon.setLocalTranslation(startX, (getHeight() - icon.getHeight()) / 2f, 0.5f);
        }

        float textX = startX + iconOffset + (iconW > 0 ? gap : 0f);
        // apply currentNudge when positioning text so layout and nudge align visually
        ttfRenderer.getTextGeometry().setLocalTranslation(textX + currentNudge.x, (getHeight() - ttfRenderer.getTextGeometry().getHeight()) / 2f + currentNudge.y, 1f);
    }

    public void executeAction() { if (isActive && action != null) action.run(); }

    @Override
    public boolean intersects(Vector2f pos) {
        if (!isActive) return false;
        Vector2f wp = new Vector2f(getWorldTranslation().x, getWorldTranslation().y);
        return pos.x >= wp.x && pos.x <= wp.x + getWidth()
                && pos.y >= wp.y && pos.y <= wp.y + getHeight();
    }

    @Override
    public void setHovered(boolean hovered) {
        if (!isActive) { this.isHovered = false; return; }
        this.isHovered = hovered;
    }

    public void handleMouseMove(Vector2f c) {
        boolean contains = intersects(c);
        if (!isActive) contains = false;
        setHovered(contains);
    }

    @Override
    public void handleMousePress(Vector2f c) {
        if (!isActive) return;
        pressed = true;
        pressedInside = intersects(c);
        ttfRenderer.setMasterSize(14);
    }

    @Override
    public void handleMouseDrag(Vector2f c) {
        if (!isActive) return;
        if (pressed) pressedInside = intersects(c);
    }

    @Override
    public void handleMouseRelease() {
        if (!isActive) return;
        boolean wasPressed = pressed;
        boolean doTrigger = pressedInside && wasPressed;
        pressed = false;
        pressedInside = false;
    }

    @Override
    public void setActive(boolean active) {
        this.isActive = active;
        if (!active) {
            isHovered = false;
            pressed = false;
            isSelected = false;
            pressedInside = false;
            currentNudge.set(0f, 0f);
            contentNode.setLocalTranslation(0f, 0f, 0f);
        }
    }

    public static final class Style {
        public final ColorRGBA defaultColor;
        public final ColorRGBA selectedColor;
        private final String backgroundColor;
        private final String hoverBackgroundColor;
        public final String fontPath;
        private float cornerRadius;
        public final Vector2f hoverNudge;
        public final float animationSpeed;
        private float iconGap;
        public final float hoverFontScale;
        public final float fontAnimationSpeed;

        // alignment & padding
        private TextAlign textAlign = TextAlign.CENTER;
        private float padding = 12f;

        // epic parameters
        private float hoverGlowScale = 1.08f;
        private float maxGlowAlpha = 0.8f;
        private float flashDuration = 0.12f;
        private float flashPeakAlpha = 0.9f;
        private float flashScale = 1.35f;
        private float pressedScale = 0.96f;
        private float shadowOffset = 6f;
        private float baseFontSize = 18f;

        public Style(ColorRGBA defaultColor,
                     ColorRGBA selectedColor,
                     String backgroundColor,
                     String hoverBackgroundColor,
                     String fontPath,
                     float cornerRadius,
                     Vector2f hoverNudge,
                     float animationSpeed,
                     float iconGap,
                     float hoverFontScale,
                     float fontAnimationSpeed) {
            this.defaultColor = defaultColor;
            this.selectedColor = selectedColor;
            this.backgroundColor = backgroundColor;
            this.hoverBackgroundColor = hoverBackgroundColor;
            this.fontPath = fontPath;
            this.cornerRadius = cornerRadius;
            this.hoverNudge = hoverNudge == null ? new Vector2f(0f, 0f) : hoverNudge;
            this.animationSpeed = animationSpeed;
            this.iconGap = iconGap;
            this.hoverFontScale = hoverFontScale;
            this.fontAnimationSpeed = fontAnimationSpeed;
        }

        public ColorRGBA defaultColor() { return defaultColor; }
        public ColorRGBA selectedColor() { return selectedColor; }
        public String backgroundColor() { return backgroundColor; }
        public String hoverBackgroundColor() { return hoverBackgroundColor; }
        public String fontPath() { return fontPath; }
        public float cornerRadius() { return cornerRadius; }
        public Vector2f hoverNudge() { return hoverNudge; }
        public float animationSpeed() { return animationSpeed; }
        public float iconGap() { return iconGap; }
        public float hoverFontScale() { return hoverFontScale; }
        public float fontAnimationSpeed() { return fontAnimationSpeed; }

        // alignment getters/setters
        public TextAlign textAlign() { return textAlign; }
        public void setTextAlign(TextAlign a) { this.textAlign = a == null ? TextAlign.CENTER : a; }

        public float padding() { return padding; }
        public void setPadding(float p) { this.padding = Math.max(0f, p); }

        // epic getters
        public float hoverGlowScale() { return hoverGlowScale; }
        public float maxGlowAlpha() { return maxGlowAlpha; }
        public float flashDuration() { return flashDuration; }
        public float flashPeakAlpha() { return flashPeakAlpha; }
        public float flashScale() { return flashScale; }
        public float pressedScale() { return pressedScale; }
        public float shadowOffset() { return shadowOffset; }
        public float baseFontSize() { return baseFontSize; }

        public void setCornerRadius(float cornerRadius) { this.cornerRadius = cornerRadius; }
        public void setIconGap(float iconGap) { this.iconGap = iconGap; }

        public static Style getViceStyle() {
            String font = "assets/Interface/fonts/FSElliotPro.ttf";
            ColorRGBA base = new ColorRGBA(1f, 0.25f, 0.7f, 1f);
            Style s = new Style(
                    ColorRGBA.White.clone(),
                    base,
                    "#232425",
                    "#2f3240cc",
                    font,
                    18f,
                    new Vector2f(8f, 0f),
                    12f,
                    8f,
                    1.12f,
                    8f
            );
            s.hoverGlowScale = 1.08f;
            s.textAlign = TextAlign.LEFT;
            s.padding = 12f;
            return s;
        }

        // tuning setters
        public void setHoverGlowScale(float s) { this.hoverGlowScale = s; }
        public void setFlashParams(float duration, float peakAlpha, float scale) { this.flashDuration = duration; this.flashPeakAlpha = peakAlpha; this.flashScale = scale; }
        public void setPressedScale(float s) { this.pressedScale = s; }
        public void setShadowOffset(float o) { this.shadowOffset = o; }
        public void setBaseFontSize(float s) { this.baseFontSize = s; }
    }

    public TTFrenderer getTtfRenderer() { return ttfRenderer; }
    public boolean isActive() { return isActive; }

    @Override
    public String getHoverSound() { return "ui.hover"; }

    @Override
    public String getClickSound() { return "ui.press"; }

    public void setLabel(String text) {
        if (text == null) text = "";
        ttfRenderer.generateText(style.defaultColor(), text);
        ttfRenderer.setColor(currentLabelColor);
        formatContent();
    }
}
