package org.foxesworld.cge.core.file.cgtex;

public class TextureEntry {
    private final int width;
    private final int height;
    private final String name;
    private final byte format;
    private final byte[] compressedData;

    public TextureEntry(int width, int height, String name, byte format, byte[] compressedData) {
        this.width = width;
        this.height = height;
        this.name = name;
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

    public String getName() {
        return name;
    }
}