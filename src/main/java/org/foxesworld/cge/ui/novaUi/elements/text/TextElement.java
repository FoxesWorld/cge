package org.foxesworld.cge.ui.novaUi.elements.text;

import com.jme3.asset.AssetManager;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.*;
import com.jme3.math.ColorRGBA;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.ui.novaUi.elements.panel.PanelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TextElement — текстовый лейбл на GUI.
 * При обновлении текста анимируется (fade out → смена строки → fade in).
 *
 * Поддерживает:
 *   • color="r,g,b,a"
 *   • fontSize="20"
 *   • fontPath="..."
 *   • posX=".."
 *   • posY=".."
 *   • align=".."
 *   • onClick="methodName" (RawInputListener)
 */
public class TextElement extends AbstractUIElement implements RawInputListener {
    private final Logger logger = LoggerFactory.getLogger(TextElement.class);

    private final CalistaGameEngine calistaGameEngine;
    private final AssetManager assetManager;

    private final TextRenderer textRenderer;
    private final TextAnimator textAnimator;

    private float rawPosX = 0f;
    private float rawPosY = 0f;

    public TextElement(CalistaGameEngine engine,
                       String id,
                       PanelElement parent,
                       String fontPath,
                       float fontSize) {
        this.id = id;
        this.calistaGameEngine = engine;
        this.assetManager = engine.getAssetManager();
        this.parentPanel = parent;
        this.node.setName("Text_" + id);

        // Создаём рендерер и аниматор
        this.textRenderer = new TextRenderer(assetManager, fontPath, fontSize);
        this.textAnimator = new TextAnimator(textRenderer.getBitmapText());

        node.attachChild(textRenderer.getBitmapText());
    }

    /**
     * Запускает анимацию смены текста.
     */
    public void setText(String newText) {
        textAnimator.animateTextChange(newText);
    }

    public float getWidth() {
        return textRenderer.getWidth();
    }

    public float getHeight() {
        return textRenderer.getHeight();
    }

    public float getRawPosX() {
        return rawPosX;
    }

    public float getRawPosY() {
        return rawPosY;
    }

    @Override
    public boolean hasOwnAlign() {
        return ownAlign != null;
    }

    @Override
    public String getOwnAlign() {
        return ownAlign;
    }

    @Override
    public void setProperty(String key, String value) {
        switch (key) {
            case "text" -> setText(value);
            case "color" -> textRenderer.setColor(parseColor(value));
            case "fontPath" -> textRenderer.setFont(value);
            case "fontSize" -> textRenderer.setFontSize(Float.parseFloat(value));
            case "posX" -> rawPosX = Float.parseFloat(value);
            case "posY" -> rawPosY = Float.parseFloat(value);
            case "align" -> ownAlign = value;
            default -> logger.warn("TextElement '{}' unknown property '{}'", id, key);
        }
    }

    @Override
    public void setOnClickHandler(String methodName, Object eventHandlerTarget) {
        super.setOnClickHandler(methodName, eventHandlerTarget);
        calistaGameEngine.getInputManager().addRawInputListener(this);
    }

    private ColorRGBA parseColor(String s) {
        String[] parts = s.split(",");
        try {
            float r = Float.parseFloat(parts[0].trim());
            float g = Float.parseFloat(parts[1].trim());
            float b = Float.parseFloat(parts[2].trim());
            float a = Float.parseFloat(parts[3].trim());
            return new ColorRGBA(r, g, b, a);
        } catch (Exception e) {
            logger.warn("TextElement '{}' failed to parse color '{}'", id, s);
            return ColorRGBA.White.clone();
        }
    }

    /**
     * Вызывать из вашего UI-менеджера (или AppState) каждый кадр.
     * Например, в update(float tpf) того AppState, где хранится этот элемент:
     * textElement.update(tpf);
     */
    public void update(float tpf) {
        textAnimator.update(tpf);
    }

    // Обработка кликов по AABB текста (не изменялось):
    @Override
    public void onMouseButtonEvent(MouseButtonEvent evt) {
        if (!evt.isPressed()) return;
        float clickX = evt.getX();
        float clickY = evt.getY();
        float tx = textRenderer.getX();
        float ty = textRenderer.getY();
        float w = getWidth();
        float h = getHeight();
        if (clickX >= tx && clickX <= tx + w &&
                clickY <= ty && clickY >= ty - h) {
            logger.debug("TextElement '{}' clicked", id);
            triggerClick();
        }
    }

    @Override public void beginInput() {}
    @Override public void endInput() {}
    @Override public void onMouseMotionEvent(MouseMotionEvent evt) {}
    @Override public void onKeyEvent(KeyInputEvent evt) {}
    @Override public void onTouchEvent(TouchEvent evt) {}
    @Override public void onJoyAxisEvent(JoyAxisEvent evt) {}
    @Override public void onJoyButtonEvent(JoyButtonEvent evt) {}
}
