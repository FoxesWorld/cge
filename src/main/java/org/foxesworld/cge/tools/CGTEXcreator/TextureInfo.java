package org.foxesworld.cge.tools.CGTEXcreator;

import org.foxesworld.cge.tools.CGTEXcreator.preview.DDSDecoder;

import java.awt.image.BufferedImage;
import java.io.File;

public class TextureInfo {
    private final File file;
    private final int width, height;
    private final byte formatCode;
    private final byte[] data;
    private BufferedImage preview;

    public TextureInfo(File file, int width, int height, byte formatCode, byte[] data) {
        this.file       = file;
        this.width      = width;
        this.height     = height;
        this.formatCode = formatCode;
        this.data       = data;
    }

    public File getFile() { return file; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public byte getFormatCode() { return formatCode; }
    public byte[] getData() { return data; }

    /** Декодирует DDS→BufferedImage (синглтон-кэш) */
    public BufferedImage getPreviewImage() {
        if (preview == null) {
            preview = DDSDecoder.decode(width, height, formatCode, data);
        }
        return preview;
    }
}