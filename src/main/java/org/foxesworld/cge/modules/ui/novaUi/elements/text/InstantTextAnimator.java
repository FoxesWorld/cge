package org.foxesworld.cge.modules.ui.novaUi.elements.text;

import com.jme3.font.BitmapText;

/**
 * An "animator" that changes the text instantly with no visual effect.
 */
public class InstantTextAnimator implements ITextAnimator {

    private final BitmapText targetText;

    public InstantTextAnimator(BitmapText targetText) {
        this.targetText = targetText;
    }

    @Override
    public void setText(String newText) {
        targetText.setText(newText);
    }

    @Override
    public void update(float tpf) {
        // Does nothing
    }

    @Override
    public boolean isAnimating() {
        return false;
    }
}