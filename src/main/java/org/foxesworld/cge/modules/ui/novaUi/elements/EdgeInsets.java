package org.foxesworld.cge.modules.ui.novaUi.elements;

/**
 * A simple, immutable data-holder for representing edge values like margin or padding.
 * @param top The top edge value.
 * @param right The right edge value.
 * @param bottom The bottom edge value.
 * @param left The left edge value.
 */
public record EdgeInsets(float top, float right, float bottom, float left) {
    public static final EdgeInsets ZERO = new EdgeInsets(0, 0, 0, 0);

    /**
     * @return The total horizontal value (left + right).
     */
    public float getHorizontal() {
        return left + right;
    }

    /**
     * @return The total vertical value (top + bottom).
     */
    public float getVertical() {
        return top + bottom;
    }
}