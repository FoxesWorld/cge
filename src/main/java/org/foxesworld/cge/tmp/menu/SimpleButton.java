package org.foxesworld.cge.tmp.menu;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;

public class SimpleButton {

    private final Node buttonNode;
    private final BitmapText label;
    private final Geometry background;
    private final Runnable action;

    public SimpleButton(AssetManager assetManager, String text, String fontPath, Runnable action) {
        this.action = action;
        this.buttonNode = new Node("Button: " + text);

        // 1. Создаем фон
        background = new Geometry("ButtonBackground", new Quad(1, 1)); // Размер будет задан позже
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        background.setMaterial(mat);
        buttonNode.attachChild(background);

        // 2. Создаем текст
        label = new BitmapText(assetManager.loadFont(fontPath), false);
        label.setText(text.toUpperCase()); // Стиль GTA - всегда заглавные
        label.setLocalTranslation(0, 0, 1); // Текст поверх фона
        buttonNode.attachChild(label);
    }

    public void setSize(float width, float height) {
        // Масштабируем фон
        background.setLocalScale(width, height, 1);

        // Масштабируем и центрируем текст
        float textHeight = label.getLineHeight();
        float desiredScale = (height * 0.7f) / textHeight; // Текст занимает 70% высоты кнопки
        label.setSize(desiredScale * label.getFont().getCharSet().getRenderedSize());

        // Центрируем текст внутри кнопки
        float textWidth = label.getLineWidth();
        float textX = (width - textWidth) / 2f;
        float textY = (height + label.getLineHeight()) / 2f;
        label.setLocalTranslation(textX, textY, 1);
    }

    public void setPosition(float x, float y) {
        buttonNode.setLocalTranslation(x, y, 0);
    }

    public void setStyle(ColorRGBA bgColor, ColorRGBA textColor) {
        // Устанавливаем цвет текста
        label.setColor(textColor);
        // Устанавливаем цвет фона
        background.getMaterial().setColor("Color", bgColor);
        // Если фон полностью прозрачный, он не будет отрисовываться
        background.getMaterial().getAdditionalRenderState().setBlendMode(
                bgColor.a < 1.0f ? com.jme3.material.RenderState.BlendMode.Alpha : com.jme3.material.RenderState.BlendMode.Off
        );
    }

// В классе SimpleButton.java

    public void setBackgroundVisibility(boolean visible) {
        if (visible) {
            background.setCullHint(Spatial.CullHint.Inherit); // Или CullHint.Never, если у родителя может быть Always
        } else {
            background.setCullHint(Spatial.CullHint.Always); // Сказать рендеру полностью игнорировать этот объект
        }
    }

    public void executeAction() {
        if (action != null) {
            action.run();
        }
    }

    public Node getButtonNode() {
        return buttonNode;
    }

    public BitmapText getLabel() {
        return label;
    }
}