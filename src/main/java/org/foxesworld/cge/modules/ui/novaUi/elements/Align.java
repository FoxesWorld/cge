package org.foxesworld.cge.modules.ui.novaUi.elements;

import java.util.Arrays;

/**
 * Defines alignment options for UI elements within their parent container.
 */
public enum Align {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    // Special value for elements managed by a layout (e.g., horizontal/vertical)
    NONE;

    /**
     * Parses a string representation into an Align enum value.
     * Case-insensitive and replaces hyphens with underscores.
     * @param value The string to parse (e.g., "top-left", "center").
     * @param defaultValue The value to return if parsing fails.
     * @return The corresponding Align enum, or defaultValue.
     */
    public static Align fromString(String value, Align defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            // Convert "top-left" to "TOP_LEFT"
            return Align.valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}