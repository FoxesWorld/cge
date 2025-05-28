package org.foxesworld.cge.core.file.cgtex;

public class TextureEntry {
    public final int width;
    public final int height;
    public final byte format;
    public final byte[] compressedData;

    public TextureEntry(int width, int height, byte format, byte[] compressedData) {
        this.width = width;
        this.height = height;
        this.format = format;
        this.compressedData = compressedData;
    }

    @Override
    public String toString() {
        return "TextureEntry{" +
                "width=" + width +
                ", height=" + height +
                ", format=" + format +
                ", compressedDataSize=" + (compressedData != null ? compressedData.length : 0) +
                '}';
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte getFormat() {
        return format;
    }

    public byte[] getCompressedData() {
        return compressedData;
    }
}