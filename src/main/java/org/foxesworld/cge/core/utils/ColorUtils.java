package org.foxesworld.cge.core.utils;

import com.jme3.math.ColorRGBA;

/**
 * A utility class for color-related operations.
 */
public final class ColorUtils {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ColorUtils() {
    }

    /**
     * Parses a hexadecimal color string and converts it into a JME3 {@link ColorRGBA} object.
     * <p>
     * This method is robust and supports the following common web formats:
     * <ul>
     *   <li>{@code #RRGGBB} (e.g., "#FF0000" for red)</li>
     *   <li>{@code #AARRGGBB} (e.g., "#80FF0000" for semi-transparent red)</li>
     * </ul>
     * The '#' prefix is optional.
     *
     * @param hex The hexadecimal color string.
     * @return A new {@link ColorRGBA} instance representing the parsed color.
     * @throws IllegalArgumentException if the hex string is null, empty, or has an invalid format.
     * @throws NumberFormatException if the string contains non-hexadecimal characters after the optional '#'.
     */
    public static ColorRGBA fromHexString(String hex) {
        if (hex == null || hex.isBlank()) {
            throw new IllegalArgumentException("Hex string cannot be null or empty.");
        }

        // Remove the leading '#' if it exists for uniform processing.
        String sanitizedHex = hex.startsWith("#") ? hex.substring(1) : hex;

        int r, g, b;
        int a = 255; // Default alpha to fully opaque (255)

        switch (sanitizedHex.length()) {
            case 6: // Format: RRGGBB
                r = Integer.parseInt(sanitizedHex.substring(0, 2), 16);
                g = Integer.parseInt(sanitizedHex.substring(2, 4), 16);
                b = Integer.parseInt(sanitizedHex.substring(4, 6), 16);
                break;
            case 8: // Format: AARRGGBB
                a = Integer.parseInt(sanitizedHex.substring(0, 2), 16);
                r = Integer.parseInt(sanitizedHex.substring(2, 4), 16);
                g = Integer.parseInt(sanitizedHex.substring(4, 6), 16);
                b = Integer.parseInt(sanitizedHex.substring(6, 8), 16);
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid hex color string format. Expected 6 (RRGGBB) or 8 (AARRGGBB) characters, but got "
                                + sanitizedHex.length() + " for string: \"" + hex + "\""
                );
        }

        // Convert 0-255 integer values to 0.0-1.0 float values as required by ColorRGBA constructor.
        return new ColorRGBA(
                r / 255.0f,
                g / 255.0f,
                b / 255.0f,
                a / 255.0f
        );
    }
}