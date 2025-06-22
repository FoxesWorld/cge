package org.foxesworld.cge.modules.ui.novaUi.elements.button;

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
 * ButtonElement — a clickable button for the GUI.
 * Supports:
 *   • text="Play"
 *   • color="r,g,b,a"
 *   • bgColor="r,g,b,a"
 *   • fontSize="20"
 *   • fontPath="..."
 *   • posX=".."
 *   • posY=".."
 *   • align=".."
 *   • padding="10" or padding="left,top,right,bottom"
 *   • margin="4" or margin="left,top,right,bottom"
 *   • onClick="methodName"
 */
public class ButtonElement extends AbstractUIElement implements RawInputListener {
    private final Logger logger = LoggerFactory.getLogger(ButtonElement.class);

    private final CalistaGameEngine calistaGameEngine;
    private final AssetManager assetManager;

    private final ButtonRenderer buttonRenderer;
    private final ButtonAnimator buttonAnimator;

    private float rawPosX = 0f;
    private float rawPosY = 0f;

    private boolean enabled = true;

    public ButtonElement(CalistaGameEngine engine,
                         String id,
                         PanelElement parent,
                         String fontPath,
                         float fontSize) {
        this.id = id;
        this.calistaGameEngine = engine;
        this.assetManager = engine.getAssetManager();
        this.parentPanel = parent;
        this.node.setName("Button_" + id);

        // Create renderer and animator
        this.buttonRenderer = new ButtonRenderer(assetManager, fontPath, fontSize);
        this.buttonAnimator = new ButtonAnimator(buttonRenderer);

        node.attachChild(buttonRenderer.getNode());
    }

    /**
     * Sets the button's label text.
     */
    public void setText(String newText) {
        buttonRenderer.setText(newText);
    }

    public float getWidth() {
        return buttonRenderer.getWidth();
    }

    public float getHeight() {
        return buttonRenderer.getHeight();
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

    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        buttonRenderer.setEnabled(enabled);
    }

    @Override
    public void setProperty(String key, String value) {
        switch (key) {
            case "text" -> setText(value);
            case "color" -> buttonRenderer.setTextColor(parseColor(value));
            case "bgColor" -> buttonRenderer.setBackgroundColor(parseColor(value));
            case "fontPath" -> buttonRenderer.setFont(value);
            case "fontSize" -> buttonRenderer.setFontSize(Float.parseFloat(value));
            case "posX" -> rawPosX = Float.parseFloat(value);
            case "posY" -> rawPosY = Float.parseFloat(value);
            case "align" -> ownAlign = value;
            case "padding" -> parseAndSetPadding(value);
            case "margin" -> parseAndSetMargin(value);
            case "enabled" -> setEnabled(Boolean.parseBoolean(value));
            default -> logger.warn("ButtonElement '{}' unknown property '{}'", id, key);
        }
    }

    private void parseAndSetPadding(String value) {
        float[] vals = parseFourFloats(value);
        if (vals.length == 1) {
            buttonRenderer.setPadding(vals[0]);
        } else if (vals.length == 4) {
            buttonRenderer.setPadding(vals[0], vals[2], vals[1], vals[3]);
        } else {
            logger.warn("ButtonElement '{}' invalid padding value '{}'", id, value);
        }
    }

    private void parseAndSetMargin(String value) {
        float[] vals = parseFourFloats(value);
        if (vals.length == 1) {
            buttonRenderer.setMargin(vals[0]);
        } else if (vals.length == 4) {
            buttonRenderer.setMargin(vals[0], vals[2], vals[1], vals[3]);
        } else {
            logger.warn("ButtonElement '{}' invalid margin value '{}'", id, value);
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
            logger.warn("ButtonElement '{}' received empty color string", id);
            return ColorRGBA.White.clone();
        }
        String[] parts = s.split(",");
        try {
            float r = 1f, g = 1f, b = 1f, a = 1f;
            if (parts.length > 0) r = Float.parseFloat(parts[0].trim());
            if (parts.length > 1) g = Float.parseFloat(parts[1].trim());
            if (parts.length > 2) b = Float.parseFloat(parts[2].trim());
            if (parts.length > 3) a = Float.parseFloat(parts[3].trim());
            r = Math.max(0f, Math.min(r, 1f));
            g = Math.max(0f, Math.min(g, 1f));
            b = Math.max(0f, Math.min(b, 1f));
            a = Math.max(0f, Math.min(a, 1f));
            return new ColorRGBA(r, g, b, a);
        } catch (Exception e) {
            logger.warn("ButtonElement '{}' failed to parse color '{}': {}", id, s, e.getMessage());
            return ColorRGBA.White.clone();
        }
    }

    /**
     * Should be called every frame from your UI manager (or AppState).
     */
    public void update(float tpf) {
        buttonAnimator.update(tpf);
    }

    // Mouse click handling by AABB of the button:
    @Override
    public void onMouseButtonEvent(MouseButtonEvent evt) {
        if (!enabled) return;
        if (!evt.isPressed()) return;
        float clickX = evt.getX();
        float clickY = evt.getY();
        float tx = buttonRenderer.getX();
        float ty = buttonRenderer.getY();
        float w = getWidth();
        float h = getHeight();
        if (clickX >= tx && clickX <= tx + w &&
                clickY <= ty && clickY >= ty - h) {
            logger.debug("ButtonElement '{}' clicked", id);
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