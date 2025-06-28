package org.foxesworld.cge.modules.ui.novaUi.elements;

import com.jme3.math.ColorRGBA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class for parsing common UI property string values into usable types.
 */
public final class PropertyParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyParser.class);

    private PropertyParser() {} // Private constructor for utility class

    /**
     * Parses a string like "10" or "10,5,10,5" into an array of floats.
     * @param value The string value representing edge values (e.g., padding or margin).
     * @return An array of floats. Returns an empty array on parsing failure.
     */
    public static float[] parseEdgeValues(String value) {
        if (value == null || value.isEmpty()) {
            return new float[0];
        }
        String[] parts = value.split(",");
        float[] vals = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                vals[i] = Float.parseFloat(parts[i].trim());
            }
            return vals;
        } catch (NumberFormatException e) {
            LOGGER.warn("Failed to parse edge values from string '{}': {}", value, e.getMessage());
            return new float[0];
        }
    }

    /**
     * Parses a color string "r,g,b,a" or "r,g,b" into a ColorRGBA object.
     * Values are expected to be in the [0, 1] range.
     * @param value The string representation of the color.
     * @return A new ColorRGBA instance. Defaults to white on failure.
     */
    public static ColorRGBA parseColorRGBA(String value) {
        if (value == null || value.isEmpty()) {
            return ColorRGBA.White.clone();
        }
        String[] parts = value.split(",");
        try {
            float r = (parts.length > 0) ? Float.parseFloat(parts[0].trim()) : 1f;
            float g = (parts.length > 1) ? Float.parseFloat(parts[1].trim()) : 1f;
            float b = (parts.length > 2) ? Float.parseFloat(parts[2].trim()) : 1f;
            float a = (parts.length > 3) ? Float.parseFloat(parts[3].trim()) : 1f;
            return new ColorRGBA(r, g, b, a);
        } catch (NumberFormatException e) {
            LOGGER.warn("Failed to parse color from string '{}'. Defaulting to white. Error: {}", value, e.getMessage());
            return ColorRGBA.White.clone();
        }
    }
}