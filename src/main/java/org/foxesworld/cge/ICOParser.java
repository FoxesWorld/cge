package org.foxesworld.cge;

import org.foxesworld.cge.core.io.ByteParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ICOParser extends ByteParser<List<BufferedImage>> {

    private static final Logger logger = LoggerFactory.getLogger(ICOParser.class.getName());
    public ICOParser(){

    }
    @Override
    protected List<BufferedImage> parseBytes(byte[] data) throws IOException {
        if (data == null || data.length < 6) {
            throw new IOException("Invalid ICO data: too short or null");
        }

        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            int reserved = readLEShort(dis);
            int type = readLEShort(dis);
            int count = readLEShort(dis);

            if (reserved != 0 || (type != 1 && type != 2) || count <= 0) {
                throw new IOException("Invalid ICO header");
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

                entries.add(new IconDirEntry(
                        width == 0 ? 256 : width,
                        height == 0 ? 256 : height,
                        colorCount,
                        planes,
                        bitCount,
                        bytesInRes,
                        imageOffset
                ));
            }

            List<BufferedImage> images = new ArrayList<>();
            for (IconDirEntry entry : entries) {
                if (entry.imageOffset + entry.bytesInRes > data.length) {
                    throw new IOException("Image entry out of bounds");
                }
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
                    throw new IOException("Unsupported image format or decoding failed");
                }
            }

            return images;
        }
    }

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

    private static BufferedImage readBmpFromIco(byte[] data) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            int headerSize = readLEInt(dis);
            int width = readLEInt(dis);
            int height = readLEInt(dis) / 2; // height includes AND mask

            int planes = readLEShort(dis);
            int bitCount = readLEShort(dis);

            if (width <= 0 || height <= 0) return null;

            if (bitCount == 8) {
                int compression = readLEInt(dis);
                int imageSize = readLEInt(dis);
                dis.skipBytes(16);

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

    private static int readLEShort(DataInputStream dis) throws IOException {
        int b1 = dis.readUnsignedByte();
        int b2 = dis.readUnsignedByte();
        return (b2 << 8) | b1;
    }

    private static int readLEInt(DataInputStream dis) throws IOException {
        int b1 = dis.readUnsignedByte();
        int b2 = dis.readUnsignedByte();
        int b3 = dis.readUnsignedByte();
        int b4 = dis.readUnsignedByte();
        return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    private record IconDirEntry(int width, int height, int colorCount, int planes, int bitCount, int bytesInRes, int imageOffset) {
    }

    public BufferedImage getBestIcon(List<BufferedImage> icons) {
        if (icons == null || icons.isEmpty()) return null;

        // Например, выбираем иконку с максимальной площадью
        BufferedImage best = icons.get(0);
        int bestSize = best.getWidth() * best.getHeight();

        for (BufferedImage icon : icons) {
            int size = icon.getWidth() * icon.getHeight();
            if (size > bestSize) {
                best = icon;
                bestSize = size;
            }
        }
        return best;
    }

}
