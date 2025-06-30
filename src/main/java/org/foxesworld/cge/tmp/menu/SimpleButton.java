package org.foxesworld.cge.tmp.menu;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;

public final class SimpleButton {

    private final Node buttonNode;
    private final BitmapText label;
    private final Geometry background;
    private final Runnable action;

    private float x, y, width, height;

    private final ColorRGBA currentColor = new ColorRGBA();
    private final ColorRGBA targetColor = new ColorRGBA();
    private static final float LERP_SPEED = 10f;

    public SimpleButton(AssetManager assetManager, String text, String fontPath, Runnable action) {
        this.action = action;
        this.buttonNode = new Node("Button: " + text);

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        this.background = new Geometry("ButtonBackground", new Quad(1, 1));
        this.background.setMaterial(mat);

        this.label = new BitmapText(assetManager.loadFont(fontPath), false);
        this.label.setText(text.toUpperCase());
        //this.label.setShadow(new ColorRGBA(0, 0, 0, 0.6f)); // Добавляем тень для читаемости
        //this.label.setShadowOffset(new Vector2f(2f, -2f));
        this.label.setLocalTranslation(0, 0, 1);

        this.buttonNode.attachChild(background);
        this.buttonNode.attachChild(label);
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        this.background.setLocalScale(width, height, 1);
        centerLabel();
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        this.buttonNode.setLocalTranslation(x, y, 0);
    }

    /**
     * ВОССТАНОВЛЕННЫЙ МЕТОД: Устанавливает стиль мгновенно, но плавно
     * переходит к новому цвету фона.
     */
    public void setStyle(ColorRGBA bgColor, ColorRGBA textColor) {
        this.targetColor.set(bgColor);
        this.label.setColor(textColor);
    }

    /**
     * НОВЫЙ МЕТОД: Позволяет изменять размер текста вручную, как для заголовка.
     */
    public void setLabelSize(float size) {
        this.label.setSize(size);
        centerLabel(); // Перецентровка после изменения размера
    }

    /**
     * НОВЫЙ МЕТОД: Позволяет изменять цвет текста вручную.
     */
    public void setLabelColor(ColorRGBA color) {
        this.label.setColor(color);
    }

    private void centerLabel() {
        if (width == 0 || height == 0) return;
        float textWidth = label.getLineWidth();
        float textHeight = label.getLineHeight();
        float textX = (width - textWidth) / 2f;
        float textY = (height + textHeight) / 2f;
        this.label.setLocalTranslation(textX, textY, 1);
    }

    public void setBackgroundVisibility(boolean visible) {
        this.background.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    public void executeAction() {
        if (action != null) {
            action.run();
        }
    }

    public boolean intersects(Vector2f point) {
        return point.x >= this.x && point.x <= (this.x + this.width) &&
                point.y >= this.y && point.y <= (this.y + this.height);
    }

    public void update(float tpf) {
        // Плавный переход цвета фона
        if (!currentColor.equals(targetColor)) {
            currentColor.interpolateLocal(targetColor, tpf * LERP_SPEED);
            this.background.getMaterial().setColor("Color", currentColor);
        }
    }

    public Node getNode() {
        return buttonNode;
    }

    public BitmapText getLabel() {
        return label;
    }
}