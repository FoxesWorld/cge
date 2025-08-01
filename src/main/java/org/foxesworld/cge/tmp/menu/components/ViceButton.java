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
import com.jme3.ui.Picture;

public final class ViceButton implements InteractiveComponent, MenuComponent {

    private final Node buttonNode;
    private final Picture icon;
    private final BitmapText label;
    private final BitmapText glitchLabel;
    private final Geometry underline;
    private final Runnable action;
    private final Style style;

    private final Vector2f position = new Vector2f();
    private float width, height;
    private boolean isSelected = false, isHovered = false, isUnderlineVisible = true, isActive = true;

    private final ColorRGBA currentLabelColor = new ColorRGBA();
    private float currentUnderlineScaleX = 0f, time = 0f;

    public ViceButton(AssetManager assetManager, String text, Style style, Runnable action) {
        this(assetManager, text, style, action, null, 0);
    }

    public ViceButton(AssetManager assetManager, String text, Style style, Runnable action, String iconPath, float iconSize) {
        this.action = action;
        this.style = style;
        this.buttonNode = new Node("ViceButton: " + text);

        this.icon = new Picture("icon");
        if (iconPath != null) {
            icon.setImage(assetManager, iconPath, true);
            icon.setWidth(iconSize);
            icon.setHeight(iconSize);
            buttonNode.attachChild(icon);
        }

        this.label = new BitmapText(assetManager.loadFont(style.fontPath()));
        label.setText(text.toUpperCase());
        label.setColor(style.defaultColor());
        currentLabelColor.set(style.defaultColor());
        buttonNode.attachChild(label);

        this.glitchLabel = label.clone();
        glitchLabel.setColor(style.glowColor().mult(new ColorRGBA(1, 1, 1, 0.7f)));
        glitchLabel.setAlpha(0f);
        buttonNode.attachChild(glitchLabel);

        Material underlineMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        underlineMat.setColor("Color", style.glowColor());
        underlineMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        this.underline = new Geometry("ButtonUnderline", new Quad(1, style.underlineHeight()));
        underline.setMaterial(underlineMat);
        underline.setLocalScale(0f, 1f, 1f);
        buttonNode.attachChild(underline);
    }

    public void setIcon(String path, AssetManager assets, float size) {
        if (path == null) return;
        icon.setImage(assets, path, true);
        icon.setWidth(size);
        icon.setHeight(size);
        centerElements();
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }

    @Override public void update(float tpf) {
        time += tpf;
        float lerp = FastMath.clamp(tpf * style.animationSpeed(), 0, 1);
        boolean glow = isSelected || isHovered;
        ColorRGBA target = glow ? style.selectedColor() : style.defaultColor();
        currentLabelColor.interpolateLocal(target, lerp);
        label.setColor(currentLabelColor);
        float tgtScale = (glow && isUnderlineVisible) ? 1f : 0f;
        currentUnderlineScaleX = FastMath.interpolateLinear(lerp, currentUnderlineScaleX, tgtScale);
        underline.setLocalScale(width * currentUnderlineScaleX, 1f, 1f);
        underline.setLocalTranslation((width - width*currentUnderlineScaleX)/2f, style.underlineYOffset(), 0);
        float currAlpha = glitchLabel.getAlpha();
        float tgtAlpha = isHovered ? 1f : 0f;
        glitchLabel.setAlpha(FastMath.interpolateLinear(lerp*1.5f, currAlpha, tgtAlpha));
        if (currAlpha > 0.01f) {
            float off = (FastMath.sin(time*50f) + FastMath.sin(time*27f))
                    * 0.5f * style.glitchIntensity();
            glitchLabel.setLocalTranslation(label.getLocalTranslation().x + off,
                    label.getLocalTranslation().y, 0.5f);
        }
    }

    public void setSize(float width, float height) {
        this.width = width; this.height = height; centerElements();
    }

    public void setPosition(float x, float y) {
        position.set(x, y);
        buttonNode.setLocalTranslation(x, y, 0);
    }

    public void setLabelSize(float size) {
        label.setSize(size);
        glitchLabel.setSize(size);
        centerElements();
    }

    public void setUnderlineVisible(boolean visible) {
        isUnderlineVisible = visible;
    }

    private void centerElements() {
        float iconW = icon.getWidth();
        float gap = iconW > 0 ? style.iconGap() : 0;
        float textW = label.getLineWidth();
        float totalW = iconW + gap + textW;
        float startX = (width - totalW)/2f;
        float y = (height + label.getLineHeight())/2f;
        if (iconW > 0) icon.setLocalTranslation(startX, y - icon.getHeight()/2f, 0);
        label.setLocalTranslation(startX + iconW + gap, y, 1);
        glitchLabel.setLocalTranslation(startX + iconW + gap, y, 0.5f);
        underline.setLocalTranslation((width - width*currentUnderlineScaleX)/2f,
                style.underlineYOffset(), 0);
    }

    public void executeAction() {
        if(isActive && action!=null) action.run();
    }

    @Override public boolean intersects(Vector2f pos) {
        if (!isActive) return false;
        Vector2f wp = new Vector2f(buttonNode.getWorldTranslation().x,
                buttonNode.getWorldTranslation().y);
        return pos.x>=wp.x && pos.x<=wp.x+width
                && pos.y>=wp.y && pos.y<=wp.y+height;
    }

    @Override public Node getNode() { return buttonNode; }
    @Override public void setHovered(boolean hovered) { this.isHovered = hovered; }
    @Override public void handleMousePress(Vector2f c) {}
    @Override public void handleMouseDrag(Vector2f c) {}
    @Override public void handleMouseRelease() {}
    @Override public void setActive(boolean active) { this.isActive = active; if(!active) isHovered=false; }

    public Vector2f getPosition() { return position; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }

    public record Style(
            ColorRGBA defaultColor,
            ColorRGBA selectedColor,
            ColorRGBA glowColor,
            String fontPath,
            float animationSpeed,
            float underlineHeight,
            float underlineYOffset,
            float glitchIntensity,
            float iconGap
    ) {
        public static Style getViceStyle() {
            String font = "Interface/Fonts/Default.fnt";
            ColorRGBA base = new ColorRGBA(1f,0.2f,0.6f,1f);
            return new Style(
                    ColorRGBA.White.clone(),
                    base,
                    base.mult(2.5f),
                    font,
                    15f,
                    2.5f,
                    -5f,
                    2.0f,
                    8f
            );
        }
    }
}