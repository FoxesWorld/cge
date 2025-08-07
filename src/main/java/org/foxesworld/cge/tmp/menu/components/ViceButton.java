package org.foxesworld.cge.tmp.menu.components;

import com.atr.jme.font.TrueTypeFont;
import com.atr.jme.font.asset.TrueTypeKeyMesh;
import com.atr.jme.font.shape.TrueTypeContainer;
import com.atr.jme.font.util.StringContainer;
import com.atr.jme.font.util.Style;
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

public final class ViceButton extends UIComponent implements InteractiveComponent, MenuComponent, SoundComponent {

    private final AssetManager assetManager;
    private TTFrenderer ttfRenderer;
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

    public ViceButton(String id, AssetManager assetManager, String text, Style style, Runnable action, String iconPath, float iconSize) {
        super(id);
        this.assetManager = assetManager;
        this.action = action;
        this.style = style;

        this.node.setName("ViceButton: " + text);
        this.contentNode = new Node("ButtonContent");
        node.attachChild(contentNode);

        ttfRenderer = new TTFrenderer(assetManager);
        ttfRenderer.generateFont("assets/Interface/fonts/FSElliotPro.ttf", com.atr.jme.font.util.Style.Plain, 16);

        // --- Background ---
        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        ColorRGBA bgColor = ColorUtils.fromHexString(style.backgroundColor());
        bgMat.setColor("Color", bgColor);
        bgMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        this.background = new Geometry("ButtonBackground");
        this.background.setMaterial(bgMat);
        node.attachChild(background);
        currentBackgroundColor.set(bgColor);

        // --- Content ---
        this.icon = new Picture("icon");
        if (iconPath != null) {
            icon.setImage(assetManager, iconPath, true);
            icon.setWidth(iconSize);
            icon.setHeight(iconSize);
            contentNode.attachChild(icon);
        }

        ttfRenderer.generateText(style.defaultColor(), text);
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

        ColorRGBA targetLabelColor = glow ? style.selectedColor() : style.defaultColor();
        currentLabelColor.interpolateLocal(targetLabelColor, lerp);
        ttfRenderer.setColor(currentLabelColor);

        ColorRGBA targetBackgroundColor = glow ? ColorUtils.fromHexString(style.hoverBackgroundColor()) : ColorUtils.fromHexString(style.backgroundColor());
        currentBackgroundColor.interpolateLocal(targetBackgroundColor, lerp);
        background.getMaterial().setColor("Color", currentBackgroundColor);

        Vector2f targetNudge = glow ? style.hoverNudge() : Vector2f.ZERO;
        currentNudge.interpolateLocal(targetNudge, lerp);
        contentNode.setLocalTranslation(currentNudge.x, currentNudge.y, 0);
    }

    @Override
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;

        background.setMesh(new RoundedQuad(width, height, style.cornerRadius(), 16));
        background.setLocalTranslation(width / 2f, height / 2f, -1f);
        centerContent();
    }

    public void setPosition(float x, float y) {
        position.set(x, y);
        node.setLocalTranslation(x, y, 0);
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
        Vector2f wp = new Vector2f(node.getWorldTranslation().x, node.getWorldTranslation().y);
        return pos.x >= wp.x && pos.x <= wp.x + width
                && pos.y >= wp.y && pos.y <= wp.y + height;
    }

    @Override
    public Node getNode() {
        return node;
    }

    @Override
    public void setHovered(boolean hovered) {
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

    public record Style(
            ColorRGBA defaultColor,
            ColorRGBA selectedColor,
            ColorRGBA glowColor,
            String backgroundColor,
            String hoverBackgroundColor,
            String fontPath,
            float cornerRadius,
            Vector2f hoverNudge,
            float animationSpeed,
            float iconGap
    ) {
        public static Style getViceStyle() {
            String font = "Interface/Fonts/Default.fnt";
            ColorRGBA base = new ColorRGBA(1f, 0.2f, 0.6f, 1f);
            return new Style(
                    ColorRGBA.White.clone(),
                    base,
                    base.mult(2.5f),
                    "#232425",
                    "#f2f5f7a8",
                    font,
                    15f,
                    new Vector2f(15f, 0f),
                    15f,
                    8f
            );
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
