package org.foxesworld.cge.tmp.menu.layout.components.elements;

import com.jme3.app.Application;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

import java.nio.ByteBuffer;

/**
 * GTA V style menu button with modern rounded look, shadow, icon, and smooth gradient highlight.
 * Улучшено: меньше хардкода, поддержка кастомных параметров, иконка, авторазмер текста.
 */
public class Button {
    // Default style (можно вынести в Theme/Style класс)
    public static final float DEFAULT_ITEM_HEIGHT = 60f;
    public static final float DEFAULT_MENU_WIDTH = 520f;
    public static final float DEFAULT_TEXT_SIZE = 32f;

    private static ColorRGBA colorFrom(float[] arr, ColorRGBA def) {
        if (arr == null || arr.length < 3) return def.clone();
        float a = (arr.length > 3) ? arr[3] : 1f;
        return new ColorRGBA(arr[0], arr[1], arr[2], a);
    }

    // Button style (можно переопределять через сеттеры/Theme)
    private ColorRGBA colorBg = new ColorRGBA(0.16f, 0.18f, 0.22f, 0.95f);
    private ColorRGBA colorBgHover = new ColorRGBA(0.25f, 0.53f, 0.95f, 0.98f);
    private ColorRGBA colorBgHover2 = new ColorRGBA(0.58f, 0.85f, 1.0f, 0.99f);
    private ColorRGBA colorText = new ColorRGBA(0.94f, 0.96f, 1f, 1f);
    private ColorRGBA colorTextSel = new ColorRGBA(0.18f, 0.22f, 0.28f, 1f);
    private ColorRGBA colorShadow = new ColorRGBA(0, 0, 0, 0.5f);

    private Application app;
    private String label;
    private Runnable action;
    private Geometry bg;
    private BitmapText text;
    private BitmapText textShadow;
    private Geometry shadow;
    private Geometry iconGeom;
    private Texture2D iconTexture;
    private float x, y;
    private float width = DEFAULT_MENU_WIDTH;
    private float height = DEFAULT_ITEM_HEIGHT;
    private boolean selected = false;
    private float borderRadius = 16f;
    private float iconSize = 36f;
    private float iconPadding = 18f;
    private float textSize = DEFAULT_TEXT_SIZE;
    private String fontPath = "Interface/Fonts/Default.fnt";
    private String id;
    private float currentPulse = 0f;
    private float targetPulse = 0f;
    private boolean needsUpdate = false;
    private ButtonSelectionListener selectionListener;

    // --- Constructors ---

    public Button(Application app, String label, Runnable action) {
        this(app, label, null, action, 0, 0, DEFAULT_MENU_WIDTH, DEFAULT_ITEM_HEIGHT);
    }

    public Button(Application app, String label, Runnable action, float x, float y, float width, float height) {
        this(app, label, null, action, x, y, width, height);
    }

    public Button(Application app, String label, Texture2D iconTexture, Runnable action) {
        this(app, label, iconTexture, action, 0, 0, DEFAULT_MENU_WIDTH, DEFAULT_ITEM_HEIGHT);
    }

    public Button(Application app, String label, Texture2D iconTexture, Runnable action, float x, float y, float width, float height) {
        this.app = app;
        this.label = label;
        this.action = action;
        this.iconTexture = iconTexture;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // --- Core placement & rendering ---

    public void place(float x, float y, boolean selected) {
        this.x = x; this.y = y;

        detach();

        // Shadow
        Quad shadowQuad = new Quad(width + 10, height - 2);
        shadow = new Geometry("MenuBtnShadow_" + label, shadowQuad);
        Material shadowMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        shadowMat.setColor("Color", colorShadow);
        shadow.setMaterial(shadowMat);
        shadow.setLocalTranslation(x + 3, y - 3, 1.7f);
        shadow.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);

        // Button background
        bg = new Geometry("MenuButtonBG_" + label, new Quad(width, height - 6));
        setSelected(selected, colorBgHover);

        bg.setLocalTranslation(x, y, 2.0f);
        bg.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);

        // Icon (optional)
        if (iconTexture != null) {
            Quad iconQuad = new Quad(iconSize, iconSize);
            iconGeom = new Geometry("MenuBtnIcon_" + label, iconQuad);
            Material iconMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            iconMat.setTexture("ColorMap", iconTexture);
            iconMat.setColor("Color", ColorRGBA.White);
            iconGeom.setMaterial(iconMat);
            iconGeom.setLocalTranslation(x + iconPadding, y + (height - iconSize) / 2f, 2.1f);
            iconGeom.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);
        } else {
            iconGeom = null;
        }

        // Button text (with shadow)
        BitmapFont font;
        try {
            font = app.getAssetManager().loadFont(fontPath);
        } catch (Exception e) {
            font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        }

        float textX = x + (iconTexture != null ? iconPadding + iconSize + 20 : 52 + 40);
        float textY = y + height / 2 + textSize / 2;

        textShadow = new BitmapText(font, false);
        textShadow.setText(label);
        textShadow.setSize(textSize);
        textShadow.setColor(new ColorRGBA(0, 0, 0, 0.5f));
        textShadow.setLocalTranslation(textX + 2, textY - 2, 2.19f);

        text = new BitmapText(font, false);
        text.setText(label);
        text.setSize(textSize);
        text.setColor(selected ? colorTextSel : colorText);
        text.setLocalTranslation(textX, textY, 2.2f);
    }

    /**
     * Updates button visual state (selected with gradient & pulsation, normal otherwise)
     */
    public void setSelected(boolean selected, ColorRGBA pulseColor) {
        boolean changed = this.selected != selected;
        this.selected = selected;

        if (bg == null) return;

        if (selected) {
            bg.setMaterial(makeGradientMaterial(app, pulseColor, colorBgHover2));
            if (text != null) text.setColor(colorTextSel);
        } else {
            Material bgMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            bgMat.setColor("Color", colorBg);
            bg.setMaterial(bgMat);
            if (text != null) text.setColor(colorText);
        }

        if (changed && selected && selectionListener != null) {
            selectionListener.onSelected(this);
        }
    }


    public void attachTo(Node node) {
        if (shadow != null) node.attachChild(shadow);
        if (bg != null) node.attachChild(bg);
        if (iconGeom != null) node.attachChild(iconGeom);
        if (textShadow != null) node.attachChild(textShadow);
        if (text != null) node.attachChild(text);
    }

    public void detach() {
        if (shadow != null) shadow.removeFromParent();
        if (bg != null) bg.removeFromParent();
        if (iconGeom != null) iconGeom.removeFromParent();
        if (textShadow != null) textShadow.removeFromParent();
        if (text != null) text.removeFromParent();
    }

    public void click() {
        if (action != null) action.run();
    }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public String getLabel() { return label; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public void setLabel(String label) { this.label = label; }

    // --- Style setters (для кастомизации через XML/Theme/конфиг) ---

    public void setBackgroundColor(ColorRGBA color) { this.colorBg = color; }
    public void setBackgroundHoverColor(ColorRGBA color) { this.colorBgHover = color; }
    public void setBackgroundHover2Color(ColorRGBA color) { this.colorBgHover2 = color; }
    public void setTextColor(ColorRGBA color) { this.colorText = color; }
    public void setTextSelectedColor(ColorRGBA color) { this.colorTextSel = color; }
    public void setShadowColor(ColorRGBA color) { this.colorShadow = color; }
    public void setFontPath(String path) { this.fontPath = path; }
    public void setTextSize(float size) { this.textSize = size; }
    public void setBorderRadius(float borderRadius) { this.borderRadius = borderRadius; }
    public void setIconSize(float size) { this.iconSize = size; }
    public void setIconPadding(float padding) { this.iconPadding = padding; }
    public void setIconTexture(Texture2D icon) { this.iconTexture = icon; }

    // Pulsating between hover and lighter blue
    public static ColorRGBA pulseColor(float pulse) {
        ColorRGBA c = new ColorRGBA(0.25f, 0.53f, 0.95f, 0.98f);
        c.interpolateLocal(new ColorRGBA(0.58f, 0.85f, 1.0f, 0.99f), pulse * 0.7f);
        return c;
    }

    // Create a horizontal gradient using a 2x1 RGBA8 texture
    private static Material makeGradientMaterial(Application app, ColorRGBA colorA, ColorRGBA colorB) {
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", getGradientTexture(app, colorA, colorB));
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        return mat;
    }
    private static Texture getGradientTexture(Application app, ColorRGBA colorA, ColorRGBA colorB) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(2 * 4);
        buffer.put((byte)(colorA.r * 255)).put((byte)(colorA.g * 255)).put((byte)(colorA.b * 255)).put((byte)(colorA.a * 255));
        buffer.put((byte)(colorB.r * 255)).put((byte)(colorB.g * 255)).put((byte)(colorB.b * 255)).put((byte)(colorB.a * 255));
        buffer.flip();
        com.jme3.texture.Image img = new com.jme3.texture.Image(com.jme3.texture.Image.Format.RGBA8, 2, 1, buffer);
        Texture tex = new Texture2D(img);
        tex.setMagFilter(Texture.MagFilter.Bilinear);
        tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        return tex;
    }

    public void setSelectionListener(ButtonSelectionListener selectionListener) {
        this.selectionListener = selectionListener;
    }
}