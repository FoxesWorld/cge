package org.foxesworld.cge.modules.ui.novaUi.elements.button;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.PropertyParser;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;

/**
 * A clickable button element composed of a background and a text label.
 * It delegates rendering and interaction logic to helper classes.
 *
 * Supported Properties:
 * - text: The text label on the button.
 * - textColor, bgColor, bgColorHover, bgColorPressed: Colors for different states.
 * - fontPath, fontSize: Text properties.
 * - enabled: (true/false)
 * - All layout properties from AbstractUIElement (margin, padding, align, etc.).
 */
public class ButtonElement extends AbstractUIElement {

    private final ButtonRenderer renderer;
    private final ButtonInteractionHandler interactionHandler;

    private float width;
    private float height;
    private boolean enabled = true;

    public ButtonElement(CalistaGameEngine engine, String id, PanelElement parent) {
        super(engine, id, parent);
        this.node.setName("Button_" + id);

        this.renderer = new ButtonRenderer(engine.getAssetManager());
        this.interactionHandler = new ButtonInteractionHandler(renderer);

        this.node.attachChild(renderer.getNode());
        recalculateSize(); // Initial size calculation
    }

    @Override
    public void setProperty(String key, String value) {
        boolean needsRecalc = false;
        switch (key.toLowerCase()) {
            case "text" -> {
                renderer.setText(value);
                needsRecalc = true;
            }
            case "fontsize" -> {
                renderer.setFontSize(Float.parseFloat(value));
                needsRecalc = true;
            }
            case "fontpath" -> {
                renderer.setFont(assetManager.loadFont(value));
                needsRecalc = true;
            }
            case "textcolor" -> renderer.setTextColor(PropertyParser.parseColorRGBA(value));
            case "bgcolor" -> interactionHandler.setBgColorNormal(PropertyParser.parseColorRGBA(value));
            case "bgcolorhover" -> interactionHandler.setBgColorHover(PropertyParser.parseColorRGBA(value));
            case "bgcolorpressed" -> interactionHandler.setBgColorPressed(PropertyParser.parseColorRGBA(value));
            case "enabled" -> this.enabled = Boolean.parseBoolean(value);
            case "padding" -> { // Padding is a layout property, needs recalc
                super.setProperty(key, value);
                needsRecalc = true;
            }
            default ->
                // For margin, align, onClick, etc.
                    super.setProperty(key, value);
        }

        if (needsRecalc) {
            recalculateSize();
        }
    }

    private void recalculateSize() {
        // Button size is determined by its text plus padding
        this.width = renderer.getTextWidth() + getPaddingH() * 2;
        this.height = renderer.getTextHeight() + getPaddingV() * 2;
        renderer.updateSize(this.width, this.height);

        // Notify parent that this element's size has changed
        if(getParentPanel() != null) {
            getParentPanel().markLayoutDirty();
        }
    }

    @Override
    public float getWidth() {
        return this.width;
    }

    @Override
    public float getHeight() {
        return this.height;
    }

    // --- Methods for the central event handler (NovaUIUpdater) ---

    public void onHoverEnter() {
        if (enabled) interactionHandler.onHoverEnter();
    }
    public void onHoverLeave() {
        if (enabled) interactionHandler.onHoverLeave();
    }
    public void onPress() {
        if (enabled) interactionHandler.onPress();
    }
    public void onRelease() {
        if (enabled) {
            interactionHandler.onRelease();
            // The click is triggered on release
            triggerClick();
        }
    }
}