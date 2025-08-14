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

public final class ViceButton extends UIComponent implements InteractiveComponent, SoundComponent {

    private final AssetManager assetManager;
    private final TTFrenderer ttfRenderer;
    private final Geometry background;
    private final Runnable action;
    private final Style style;

    private final Node contentNode; // для иконки + текста
    private final Picture icon;

    private final Vector2f position = new Vector2f();
    private float width, height;
    private boolean isSelected = false, isHovered = false, isActive = true;

    // --- Анимационные состояния ---
    private final ColorRGBA currentLabelColor = new ColorRGBA();
    private final ColorRGBA currentBackgroundColor = new ColorRGBA();
    private final Vector2f currentNudge = new Vector2f();
    private float time = 0f;

    /**
     * Замечание: com.atr.jme.font.util.Style — это enum шрифта (Bold/Plain/...).
     * Чтобы не путаться, внутренний стиль компонента назван StyleRecord.
     */
    public ViceButton(String id, AssetManager assetManager, String text, Style style, Runnable action, String iconPath, float iconSize) {
        super(id);
        this.assetManager = assetManager;
        this.action = action;
        this.style = style;

        setName("ViceButton: " + text);
        this.contentNode = new Node("ButtonContent");
        attachChild(contentNode);

        // TTFrenderer: генерируем шрифт и текст
        ttfRenderer = new TTFrenderer(assetManager);
        // используем путь из стиля, или запасной путь, если пустой
        String fontPath = (style.fontPath == null || style.fontPath.isBlank())
                ? "assets/Interface/fonts/FSElliotPro.ttf" : style.fontPath;
        // masterSize 16 — базовый атласный размер; масштаб управляется через animateScaleTo/setScaleInstant
        ttfRenderer.generateFont(fontPath, com.atr.jme.font.util.Style.Plain, 16);
        ttfRenderer.generateText(style.defaultColor(), text);

        // --- Background ---
        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        ColorRGBA bgColor = ColorUtils.fromHexString(style.backgroundColor());
        bgMat.setColor("Color", bgColor);
        bgMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        this.background = new Geometry("ButtonBackground");
        this.background.setMaterial(bgMat);
        attachChild(background);
        currentBackgroundColor.set(bgColor);

        // --- Content (icon + text geometry) ---
        this.icon = new Picture("icon");
        if (iconPath != null) {
            icon.setImage(assetManager, iconPath, true);
            icon.setWidth(iconSize);
            icon.setHeight(iconSize);
            contentNode.attachChild(icon);
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
        float lerp = FastMath.clamp(tpf * style.animationSpeed(), 0, 1);
        boolean glow = isSelected || isHovered;

        // Цвет метки
        ColorRGBA targetLabelColor = glow ? style.selectedColor() : style.defaultColor();
        currentLabelColor.interpolateLocal(targetLabelColor, lerp);
        ttfRenderer.setColor(currentLabelColor);

        // Фон
        ColorRGBA targetBackgroundColor = glow ? ColorUtils.fromHexString(style.hoverBackgroundColor()) : ColorUtils.fromHexString(style.backgroundColor());
        currentBackgroundColor.interpolateLocal(targetBackgroundColor, lerp);
        background.getMaterial().setColor("Color", currentBackgroundColor);

        // Nudge (сдвиг)
        Vector2f targetNudge = glow ? style.hoverNudge() : Vector2f.ZERO;
        currentNudge.interpolateLocal(targetNudge, lerp);
        contentNode.setLocalTranslation(currentNudge.x, currentNudge.y, 0);

        // --- Управление масштабом шрифта: если ховер — анимируем к scaleHover, иначе к 1.0 ---
        float hoverScale = style.hoverFontScale();
        float animSpeed = style.fontAnimationSpeed();
        if (isHovered) {
            ttfRenderer.animateScaleTo(hoverScale, animSpeed);
        } else {
            ttfRenderer.animateScaleTo(1f, animSpeed);
        }

        // обновляем рендерер шрифта (анимация масштаба применяется здесь)
        ttfRenderer.update(tpf);

        // центрируем контент заново, чтобы учесть изменение размеров текста при анимации
        centerContent();
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;

        background.setMesh(new RoundedQuad(width, height, style.cornerRadius(), 16));
        background.setLocalTranslation(width / 2f, height / 2f, -1f);
        centerContent();
    }

    public void setPosition(float x, float y) {
        position.set(x, y);
        setLocalTranslation(x, y, 0);
    }

    private void centerContent() {
        float iconW = icon.getWidth();
        float gap = iconW > 0 ? style.iconGap() : 0;
        float textW = ttfRenderer.getTextGeometry().getWidth();
        float totalW = iconW + gap + textW;

        float startX = (width - totalW) / 10f; // можно подправить, если хочется точнее центрировать

        float iconY = (height - icon.getHeight()) / 2f;
        float textY = (height - ttfRenderer.getTextGeometry().getHeight()) / 2f;

        if (iconW > 0) {
            icon.setLocalTranslation(startX, iconY, 0);
        }

        ttfRenderer.getTextGeometry().setLocalTranslation(startX + iconW + gap, textY, 1);
    }
    public void executeAction() {
        if (isActive && action != null) action.run();
    }

    @Override
    public boolean intersects(Vector2f pos) {
        if (!isActive) return false;
        Vector2f wp = new Vector2f(getWorldTranslation().x, getWorldTranslation().y);
        return pos.x >= wp.x && pos.x <= wp.x + width
                && pos.y >= wp.y && pos.y <= wp.y + height;
    }


    @Override
    public void setHovered(boolean hovered) {
        // при изменении состояния ховера будем переключать флаг,
        // фактическая анимация масштаба выполняется в update(tpf)
        this.isHovered = hovered;
    }

    @Override
    public void handleMousePress(Vector2f c) {}

    @Override
    public void handleMouseDrag(Vector2f c) {}

    @Override
    public void handleMouseRelease() {}

    @Override
    public void setActive(boolean active) {
        this.isActive = active;
        if (!active) isHovered = false;
    }

    // Компонентный стиль — теперь расширен параметрами для управления шрифтом
    public static final class Style {
        public final ColorRGBA defaultColor;
        public final ColorRGBA selectedColor;
        public final ColorRGBA glowColor;
        private String backgroundColor;
        private String hoverBackgroundColor;
        public final String fontPath;
        private float cornerRadius;
        public final Vector2f hoverNudge;
        public final float animationSpeed;
        public final float iconGap;

        // font scaling params
        public final float hoverFontScale;      // например 1.12f
        public final float fontAnimationSpeed;  // скорость анимации масштаба (units/sec-ish)

        public Style(ColorRGBA defaultColor,
                     ColorRGBA selectedColor,
                     ColorRGBA glowColor,
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
            this.glowColor = glowColor;
            this.backgroundColor = backgroundColor;
            this.hoverBackgroundColor = hoverBackgroundColor;
            this.fontPath = fontPath;
            this.cornerRadius = cornerRadius;
            this.hoverNudge = hoverNudge;
            this.animationSpeed = animationSpeed;
            this.iconGap = iconGap;
            this.hoverFontScale = hoverFontScale;
            this.fontAnimationSpeed = fontAnimationSpeed;
        }

        public ColorRGBA defaultColor() { return defaultColor; }
        public ColorRGBA selectedColor() { return selectedColor; }
        public ColorRGBA glowColor() { return glowColor; }
        public String backgroundColor() { return backgroundColor; }
        public String hoverBackgroundColor() { return hoverBackgroundColor; }
        public String fontPath() { return fontPath; }
        public float cornerRadius() { return cornerRadius; }
        public Vector2f hoverNudge() { return hoverNudge; }
        public float animationSpeed() { return animationSpeed; }
        public float iconGap() { return iconGap; }
        public float hoverFontScale() { return hoverFontScale; }
        public float fontAnimationSpeed() { return fontAnimationSpeed; }

        public static Style getViceStyle() {
            String font = "assets/Interface/fonts/FSElliotPro.ttf";
            ColorRGBA base = new ColorRGBA(1f, 0.2f, 0.6f, 1f);
            return new Style(
                    ColorRGBA.White.clone(),
                    base,
                    base.mult(2.5f),
                    "#232425",
                    "#2f3240cc",
                    font,
                    15f,
                    new Vector2f(20f, 0f),
                    15f,
                    8f,
                    1.2f,   // hover font scale
                    8f       // font animation speed
            );
        }

        public void setCornerRadius(float cornerRadius) {
            this.cornerRadius = cornerRadius;
        }

        public void setBackgroundColor(String backgroundColor) {
            this.backgroundColor = backgroundColor;
        }

        public void setHoverBackgroundColor(String hoverBackgroundColor) {
            this.hoverBackgroundColor = hoverBackgroundColor;
        }
    }

    public TTFrenderer getTtfRenderer() {
        return ttfRenderer;
    }

    @Override
    public float getWidth() {
        return width;
    }

    @Override
    public float getHeight() {
        return height;
    }
}
