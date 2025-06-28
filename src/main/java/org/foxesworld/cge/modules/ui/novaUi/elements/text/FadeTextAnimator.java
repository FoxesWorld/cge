package org.foxesworld.cge.modules.ui.novaUi.elements.text;

import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;

/**
 * An animator that changes text by fading out the old text,
 * swapping the string, and fading in the new text.
 */
public class FadeTextAnimator implements ITextAnimator {

    private enum State { IDLE, FADING_OUT, FADING_IN }

    private final BitmapText targetText;
    private final float fadeDuration;
    private State currentState = State.IDLE;
    private float timer = 0f;
    private String pendingText = null;

    public FadeTextAnimator(BitmapText targetText, float fadeDuration) {
        this.targetText = targetText;
        this.fadeDuration = Math.max(0.01f, fadeDuration); // Avoid division by zero
    }

    @Override
    public void setText(String newText) {
        if (currentState != State.IDLE) {
            // If already animating, just update the pending text
            this.pendingText = newText;
            return;
        }
        if (targetText.getText() == null || targetText.getText().isEmpty()) {
            // If text is initially empty, just set it and fade in
            targetText.setText(newText);
            currentState = State.FADING_IN;
            timer = 0f;
        } else {
            this.pendingText = newText;
            currentState = State.FADING_OUT;
            timer = 0f;
        }
    }

    @Override
    public void update(float tpf) {
        if (currentState == State.IDLE) return;

        timer += tpf;
        float progress = Math.min(1f, timer / fadeDuration);

        switch (currentState) {
            case FADING_OUT:
                targetText.setAlpha(1f - progress);
                if (progress >= 1f) {
                    targetText.setText(pendingText);
                    pendingText = null;
                    currentState = State.FADING_IN;
                    timer = 0f;
                }
                break;
            case FADING_IN:
                targetText.setAlpha(progress);
                if (progress >= 1f) {
                    targetText.setAlpha(1f);
                    currentState = State.IDLE;
                    timer = 0f;
                    // If a new text was set during fade-in, start the cycle again
                    if (pendingText != null) {
                        setText(pendingText);
                    }
                }
                break;
        }
    }

    @Override
    public boolean isAnimating() {
        return currentState != State.IDLE;
    }
}