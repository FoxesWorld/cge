package org.foxesworld.cge.core.file.extensions.cgtex;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a single texture entry within a CGTEX file.
 * This is an immutable data carrier class.
 *
 * @param width      The width of the texture in pixels.
 * @param height     The height of the texture in pixels.
 * @param name       The unique identifier name for this texture.
 * @param format     The texture format identifier (e.g., 0 for RGBA8, 1 for DXT1).
 * @param data       The raw byte array of the texture's pixel data.
 */
public record TextureEntry(int width, int height, String name, byte format, byte[] data) {

    public TextureEntry {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(data, "Data cannot be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextureEntry that = (TextureEntry) o;
        return width == that.width && height == that.height && format == that.format && name.equals(that.name) && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(width, height, name, format);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "TextureEntry{" +
                "name='" + name + '\'' +
                ", width=" + width +
                ", height=" + height +
                ", format=" + format +
                ", data.length=" + data.length +
                '}';
    }
}