package org.foxesworld.cge.modules.ui.novaUi.elements.text;

import com.jme3.font.BitmapText;

/**
 * Defines the contract for an object that can animate text changes on a BitmapText component.
 */
public interface ITextAnimator {
    /**
     * Initiates the process of changing the text to a new value.
     * @param newText The target text to display.
     */
    void setText(String newText);

    /**
     * Updates the animation state.
     * @param tpf Time per frame.
     */
    void update(float tpf);

    /**
     * Checks if the animator is currently in the middle of an animation.
     * @return true if animating, false otherwise.
     */
    boolean isAnimating();
}