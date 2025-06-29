package org.foxesworld.cge.modules.ui.novaUi.elements;

import com.jme3.math.ColorRGBA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class for parsing common UI property string values into usable types.
 * Provides safe parsing methods with default fallbacks and support for multiple formats (e.g., HEX colors).
 */
@SuppressWarnings("unused")
public final class PropertyParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyParser.class);

    private PropertyParser() {
        // Utility class, not meant to be instantiated.
    }

    /**
     * Parses a string like "10" or "10,5,10,5" into an array of floats.
     * Useful for properties like padding or margin.
     * @param value The string value representing edge values.
     * @return An array of floats. Returns a zero-length array on parsing failure.
     */
    public static float[] parseEdgeValues(String value) {
        if (value == null || value.trim().isEmpty()) {
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
            LOGGER.warn("Failed to parse edge values from string '{}': {}. Returning empty array.", value, e.getMessage());
            return new float[0];
        }
    }

    /**
     * Parses a color string into a ColorRGBA object.
     * Supports formats:
     * <ul>
     *   <li><b>Component Floats:</b> "r,g,b,a" or "r,g,b" (e.g., "1, 0.5, 0, 1"). Values are 0-1.</li>
     *   <li><b>HEX RGB:</b> "#RRGGBB" (e.g., "#FF8000").</li>
     *   <li><b>HEX ARGB:</b> "#AARRGGBB" (e.g., "#FFFF8000").</li>
     * </ul>
     * @param value The string representation of the color.
     * @return A new ColorRGBA instance. Defaults to white on failure.
     */
    public static ColorRGBA parseColorRGBA(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ColorRGBA.White.clone();
        }

        value = value.trim();

        // --- Try parsing HEX format ---
        if (value.startsWith("#")) {
            try {
                // Remove '#' prefix
                String hex = value.substring(1);
                // Handle formats like #RRGGBB and #AARRGGBB
                long hexVal = Long.parseLong(hex, 16);
                float a = (hex.length() == 8) ? ((hexVal >> 24) & 0xFF) / 255.0f : 1.0f;
                float r = ((hexVal >> 16) & 0xFF) / 255.0f;
                float g = ((hexVal >> 8) & 0xFF) / 255.0f;
                float b = (hexVal & 0xFF) / 255.0f;
                return new ColorRGBA(r, g, b, a);
            } catch (NumberFormatException e) {
                LOGGER.warn("Failed to parse HEX color from string '{}'. Defaulting to white. Error: {}", value, e.getMessage());
                return ColorRGBA.White.clone();
            }
        }

        // --- Try parsing "r,g,b,a" float format ---
        String[] parts = value.split(",");
        try {
            float r = (parts.length > 0) ? Float.parseFloat(parts[0].trim()) : 1f;
            float g = (parts.length > 1) ? Float.parseFloat(parts[1].trim()) : 1f;
            float b = (parts.length > 2) ? Float.parseFloat(parts[2].trim()) : 1f;
            float a = (parts.length > 3) ? Float.parseFloat(parts[3].trim()) : 1f;
            return new ColorRGBA(r, g, b, a);
        } catch (NumberFormatException e) {
            LOGGER.warn("Failed to parse component color from string '{}'. Defaulting to white. Error: {}", value, e.getMessage());
            return ColorRGBA.White.clone();
        }
    }

    /**
     * Safely parses a string to a float, returning a default value on failure.
     * @param value The string to parse.
     * @param defaultValue The value to return if parsing fails.
     * @return The parsed float or the default value.
     */
    public static float tryParseFloat(String value, float defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.trace("Could not parse float from '{}'. Using default value {}.", value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Safely parses a string to an integer, returning a default value on failure.
     * @param value The string to parse.
     * @param defaultValue The value to return if parsing fails.
     * @return The parsed integer or the default value.
     */
    public static int tryParseInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.trace("Could not parse integer from '{}'. Using default value {}.", value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Safely parses a string to a boolean.
     * "true" (case-insensitive) evaluates to true. Any other value evaluates to false.
     * @param value The string to parse.
     * @return The parsed boolean.
     */
    public static boolean tryParseBoolean(String value) {
        return "true".equalsIgnoreCase(value);
    }

    /**
     * Safely parses a string to an Enum constant of the specified type.
     * This method is case-insensitive.
     * @param enumType The class of the Enum to parse into.
     * @param value The string name of the enum constant.
     * @param defaultValue The value to return if the string does not match any constant.
     * @param <T> The enum type.
     * @return The matching enum constant or the default value.
     */
    public static <T extends Enum<T>> T tryParseEnum(Class<T> enumType, String value, T defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid enum value '{}' for type '{}'. Using default value '{}'.", value, enumType.getSimpleName(), defaultValue);
            return defaultValue;
        }
    }
}