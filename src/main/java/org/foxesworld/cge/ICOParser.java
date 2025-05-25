package org.foxesworld.cge;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Microsoft ICO files and extracts contained images.
 * Supports both PNG and BMP-encoded entries with various color depths (8/24/32-bit).
 */
public class ICOParser {

    /**
     * Reads and parses an ICO file from the given input stream.
     *
     * @param inputStream input stream containing the .ico file
     * @return list of extracted {@link BufferedImage} objects
     * @throws IOException if the ICO file is malformed or an image fails to decode
     */
    public static List<BufferedImage> read(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }

        // Read entire stream into byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        byte[] data = baos.toByteArray();

        if (data.length < 6) {
            throw new IOException("Invalid ICO file: too short");
        }

        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            int reserved = readLEShort(dis);
            int type = readLEShort(dis);
            int count = readLEShort(dis);

            if (reserved != 0 || (type != 1 && type != 2) || count <= 0) {
                throw new IOException("Invalid ICO file: incorrect header");
            }

            List<IconDirEntry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int width = dis.readUnsignedByte();
                int height = dis.readUnsignedByte();
                int colorCount = dis.readUnsignedByte();
                dis.readByte(); // reserved
                int planes = readLEShort(dis);
                int bitCount = readLEShort(dis);
                int bytesInRes = readLEInt(dis);
                int imageOffset = readLEInt(dis);

                if (imageOffset + bytesInRes > data.length || bytesInRes <= 0) {
                    throw new IOException("Invalid ICO entry: out of bounds or corrupted size");
                }

                entries.add(new IconDirEntry(width, height, colorCount, planes, bitCount, bytesInRes, imageOffset));
            }

            List<BufferedImage> images = new ArrayList<>();
            for (IconDirEntry entry : entries) {
                byte[] imageData = new byte[entry.bytesInRes];
                System.arraycopy(data, entry.imageOffset, imageData, 0, entry.bytesInRes);
                BufferedImage img;

                if (isPng(imageData)) {
                    img = ImageIO.read(new ByteArrayInputStream(imageData));
                } else {
                    img = readBmpFromIco(imageData);
                }

                if (img != null) {
                    images.add(img);
                } else {
                    throw new IOException("Unsupported image format or decoding failed at offset " + entry.imageOffset);
                }
            }

            return images;
        }
    }

    /**
     * Checks if the given byte array starts with a PNG header.
     */
    private static boolean isPng(byte[] data) {
        return data.length >= 8 &&
                (data[0] & 0xFF) == 0x89 &&
                data[1] == 0x50 &&
                data[2] == 0x4E &&
                data[3] == 0x47 &&
                data[4] == 0x0D &&
                data[5] == 0x0A &&
                data[6] == 0x1A &&
                data[7] == 0x0A;
    }

    /**
     * Reads a BMP-formatted image from ICO data.
     * Supports 8-bit indexed, 24-bit and 32-bit images.
     */
    private static BufferedImage readBmpFromIco(byte[] data) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            int headerSize = readLEInt(dis);
            int width = readLEInt(dis);
            int height = readLEInt(dis) / 2; // includes AND mask
            int planes = readLEShort(dis);
            int bitCount = readLEShort(dis);

            if (width <= 0 || height <= 0) return null;

            if (bitCount == 8) {
                int compression = readLEInt(dis);
                int imageSize = readLEInt(dis);
                dis.skipBytes(16); // skip rest of BITMAPINFOHEADER

                int[] palette = new int[256];
                for (int i = 0; i < 256; i++) {
                    int b = dis.readUnsignedByte();
                    int g = dis.readUnsignedByte();
                    int r = dis.readUnsignedByte();
                    dis.readByte(); // reserved
                    palette[i] = (r << 16) | (g << 8) | b;
                }

                byte[] pixels = new byte[width * height];
                for (int y = height - 1; y >= 0; y--) {
                    dis.readFully(pixels, y * width, width);
                }

                byte[] r = new byte[256], g = new byte[256], b = new byte[256];
                for (int i = 0; i < 256; i++) {
                    r[i] = (byte) ((palette[i] >> 16) & 0xFF);
                    g[i] = (byte) ((palette[i] >> 8) & 0xFF);
                    b[i] = (byte) (palette[i] & 0xFF);
                }

                IndexColorModel colorModel = new IndexColorModel(8, 256, r, g, b);
                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
                img.getRaster().setDataElements(0, 0, width, height, pixels);
                return img;

            } else if (bitCount == 24 || bitCount == 32) {
                int rowSize = ((bitCount * width + 31) / 32) * 4;
                byte[] row = new byte[rowSize];
                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

                for (int y = height - 1; y >= 0; y--) {
                    dis.readFully(row);
                    for (int x = 0; x < width; x++) {
                        int offset = x * (bitCount / 8);
                        if (offset + (bitCount / 8) > row.length) continue;
                        int b = row[offset] & 0xFF;
                        int g = row[offset + 1] & 0xFF;
                        int r = row[offset + 2] & 0xFF;
                        int a = (bitCount == 32) ? (row[offset + 3] & 0xFF) : 255;
                        int argb = (a << 24) | (r << 16) | (g << 8) | b;
                        img.setRGB(x, y, argb);
                    }
                }
                return img;
            }

            return null;
        }
    }

    /**
     * Reads a 2-byte little-endian short from the stream.
     */
    private static int readLEShort(DataInputStream dis) throws IOException {
        int b1 = dis.readUnsignedByte();
        int b2 = dis.readUnsignedByte();
        return (b2 << 8) | b1;
    }

    /**
     * Reads a 4-byte little-endian int from the stream.
     */
    private static int readLEInt(DataInputStream dis) throws IOException {
        int b1 = dis.readUnsignedByte();
        int b2 = dis.readUnsignedByte();
        int b3 = dis.readUnsignedByte();
        int b4 = dis.readUnsignedByte();
        return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    /**
     * Internal representation of a directory entry in an ICO file.
     */
    private static class IconDirEntry {
        final int width, height, colorCount, planes, bitCount, bytesInRes, imageOffset;

        IconDirEntry(int width, int height, int colorCount, int planes, int bitCount, int bytesInRes, int imageOffset) {
            this.width = (width == 0 ? 256 : width);   // 0 means 256 in ICO format
            this.height = (height == 0 ? 256 : height);
            this.colorCount = colorCount;
            this.planes = planes;
            this.bitCount = bitCount;
            this.bytesInRes = bytesInRes;
            this.imageOffset = imageOffset;
        }
    }

    /**
     * Возвращает наилучшую иконку из списка, основываясь на максимальном размере (площадь) и глубине цвета.
     *
     * @param icons список иконок, загруженных из ICO-файла
     * @return самая большая и наиболее качественная иконка
     * @throws IOException если список пуст
     */
    public static BufferedImage getBestIcon(List<BufferedImage> icons) throws IOException {
        return icons.stream()
                .sorted((a, b) -> {
                    int sizeA = a.getWidth() * a.getHeight();
                    int sizeB = b.getWidth() * b.getHeight();
                    if (sizeA != sizeB) return Integer.compare(sizeB, sizeA); // по убыванию площади
                    int bitsA = a.getColorModel().getPixelSize();
                    int bitsB = b.getColorModel().getPixelSize();
                    return Integer.compare(bitsB, bitsA); // по убыванию битности
                })
                .findFirst()
                .orElseThrow(() -> new IOException("No icons found in ICO file"));
    }

}