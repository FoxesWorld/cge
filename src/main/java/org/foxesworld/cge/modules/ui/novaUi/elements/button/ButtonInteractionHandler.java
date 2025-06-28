package org.foxesworld.cge.modules.ui.novaUi.elements.button;

import com.jme3.math.ColorRGBA;

/**
 * Manages the visual states (normal, hover, pressed) of a ButtonElement.
 */
public class ButtonInteractionHandler {
    private enum State { NORMAL, HOVER, PRESSED }

    private final ButtonRenderer renderer;
    private State currentState = State.NORMAL;

    // Configurable colors for different states
    private ColorRGBA bgColorNormal = ColorRGBA.Gray.clone();
    private ColorRGBA bgColorHover = ColorRGBA.LightGray.clone();
    private ColorRGBA bgColorPressed = ColorRGBA.DarkGray.clone();

    // You can add more properties for different states, e.g., textColorHover

    public ButtonInteractionHandler(ButtonRenderer renderer) {
        this.renderer = renderer;
        updateVisuals(); // Apply initial state
    }

    public void onHoverEnter() {
        if (currentState != State.PRESSED) {
            currentState = State.HOVER;
            updateVisuals();
        }
    }

    public void onHoverLeave() {
        if (currentState != State.PRESSED) {
            currentState = State.NORMAL;
            updateVisuals();
        }
    }

    public void onPress() {
        currentState = State.PRESSED;
        updateVisuals();
    }

    public void onRelease() {
        // When released, return to hover state if the mouse is still over it,
        // otherwise return to normal.
        // This check should be performed by the central event handler.
        // For simplicity here, we assume it goes back to hover.
        currentState = State.HOVER;
        updateVisuals();
    }

    private void updateVisuals() {
        switch (currentState) {
            case PRESSED:
                renderer.setBackgroundColor(bgColorPressed);
                break;
            case HOVER:
                renderer.setBackgroundColor(bgColorHover);
                break;
            case NORMAL:
            default:
                renderer.setBackgroundColor(bgColorNormal);
                break;
        }
    }

    // --- Setters for configurable properties ---
    public void setBgColorNormal(ColorRGBA color) {
        this.bgColorNormal = color;
        if(currentState == State.NORMAL) updateVisuals();
    }
    public void setBgColorHover(ColorRGBA color) { this.bgColorHover = color; }
    public void setBgColorPressed(ColorRGBA color) { this.bgColorPressed = color; }
}