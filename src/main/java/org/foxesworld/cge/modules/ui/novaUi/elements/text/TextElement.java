package org.foxesworld.cge.modules.ui.novaUi.elements.text;

import com.jme3.asset.AssetManager;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.*;
import com.jme3.math.ColorRGBA;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TextElement — a text label for the GUI.
 * When the text is updated, it animates (fade out → line change → fade in).
 *
 * Supports:
 *   • color="r,g,b,a"
 *   • fontSize="20"
 *   • fontPath="..."
 *   • posX=".."
 *   • posY=".."
 *   • align=".."
 *   • padding="10" or padding="left,top,right,bottom"
 *   • margin="4" or margin="left,top,right,bottom"
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

        // Create renderer and animator
        this.textRenderer = new TextRenderer(assetManager, fontPath, fontSize);
        this.textAnimator = new TextAnimator(textRenderer.getBitmapText());

        node.attachChild(textRenderer.getBitmapText());
    }

    /**
     * Starts text change animation.
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
            case "padding" -> parseAndSetPadding(value);
            case "margin" -> parseAndSetMargin(value);
            default -> logger.warn("TextElement '{}' unknown property '{}'", id, key);
        }
    }

    private void parseAndSetPadding(String value) {
        float[] vals = parseFourFloats(value);
        if (vals.length == 1) {
            textRenderer.setPadding(vals[0]);
        } else if (vals.length == 4) {
            textRenderer.setPadding(vals[0], vals[2], vals[1], vals[3]);
        } else {
            logger.warn("TextElement '{}' invalid padding value '{}'", id, value);
        }
    }

    private void parseAndSetMargin(String value) {
        float[] vals = parseFourFloats(value);
        if (vals.length == 1) {
            textRenderer.setMargin(vals[0]);
        } else if (vals.length == 4) {
            textRenderer.setMargin(vals[0], vals[2], vals[1], vals[3]);
        } else {
            logger.warn("TextElement '{}' invalid margin value '{}'", id, value);
        }
    }

    // Helper: parses "10", "10,20,30,40", etc.
    private float[] parseFourFloats(String value) {
        String[] parts = value.split(",");
        float[] vals = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                vals[i] = Float.parseFloat(parts[i].trim());
            }
            return vals;
        } catch (Exception e) {
            logger.warn("Failed to parse paddings/margins '{}': {}", value, e.getMessage());
            return new float[0];
        }
    }

    private ColorRGBA parseColor(String s) {
        if (s == null || s.isEmpty()) {
            logger.warn("TextElement '{}' received empty color string", id);
            return ColorRGBA.White.clone();
        }
        String[] parts = s.split(",");
        try {
            float r = 1f, g = 1f, b = 1f, a = 1f; // defaults
            if (parts.length > 0) r = Float.parseFloat(parts[0].trim());
            if (parts.length > 1) g = Float.parseFloat(parts[1].trim());
            if (parts.length > 2) b = Float.parseFloat(parts[2].trim());
            if (parts.length > 3) a = Float.parseFloat(parts[3].trim());

            // Clamp values to [0,1]
            r = Math.max(0f, Math.min(r, 1f));
            g = Math.max(0f, Math.min(g, 1f));
            b = Math.max(0f, Math.min(b, 1f));
            a = Math.max(0f, Math.min(a, 1f));

            return new ColorRGBA(r, g, b, a);
        } catch (Exception e) {
            logger.warn("TextElement '{}' failed to parse color '{}': {}", id, s, e.getMessage());
            return ColorRGBA.White.clone();
        }
    }

    /**
     * Should be called every frame from your UI manager (or AppState).
     * For example, in update(float tpf) inside the AppState that holds this element:
     * textElement.update(tpf);
     */
    public void update(float tpf) {
        textAnimator.update(tpf);
    }

    // Mouse click handling by AABB of the text (unchanged):
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