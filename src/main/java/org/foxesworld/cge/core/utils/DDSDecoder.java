package org.foxesworld.cge.core.utils;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * DDSDecoder: supports DXT1, DXT3, DXT5 formats.
 * If format is unrecognized, draws a checkerboard.
 * <p>
 * Improvements:
 * - Reuses temporary buffers to minimize allocations
 * - Validates input length
 * - Simplified branch logic
 * - Bounds checks for safety
 */
public final class DDSDecoder {
    public static final byte FORMAT_DXT1 = 1;
    public static final byte FORMAT_DXT3 = 3;
    public static final byte FORMAT_DXT5 = 5;
    private DDSDecoder() {}

    /**
     * Decodes DDS data into ARGB BufferedImage.
     * @param width  Image width
     * @param height Image height
     * @param format One of FORMAT_DXT1, FORMAT_DXT3, FORMAT_DXT5
     * @param data   Raw DDS pixel data in little-endian
     * @return BufferedImage of decoded pixels
     * @throws IllegalArgumentException if data is null or insufficient
     */
    public static BufferedImage decode(int width, int height, byte format, byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data buffer is null");
        }
        // Minimum size check: at least header-less size for one block
        int minBlockSize = (format == FORMAT_DXT3 ? 16 : format == FORMAT_DXT5 ? 16 : 8);
        if (data.length < minBlockSize) {
            throw new IllegalArgumentException("Data buffer too small: " + data.length);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        if (format == FORMAT_DXT1 || format == FORMAT_DXT3 || format == FORMAT_DXT5) {
            decodeDXT(width, height, buf, pixels, format);
        } else {
            fillCheckerboard(width, height, pixels, 16, 0xFFC0C0C0, 0xFF808080);
        }
        return image;
    }

    private static void decodeDXT(int w, int h, ByteBuffer buf, int[] outPx, byte fmt) {
        final int blockW = (w + 3) >> 2;
        final int blockH = (h + 3) >> 2;
        final boolean explicitAlpha = (fmt == FORMAT_DXT3);
        final boolean smallAlpha    = (fmt == FORMAT_DXT5);
        int[] alphaMap = new int[16];
        int[] colorMap = new int[4];

        for (int by = 0; by < blockH; by++) {
            for (int bx = 0; bx < blockW; bx++) {
                if (explicitAlpha) {
                    decodeAlphaDXT3(buf, alphaMap);
                } else if (smallAlpha) {
                    decodeAlphaDXT5(buf, alphaMap);
                } else {
                    Arrays.fill(alphaMap, 0xFF);
                }

                decodeColorBlock(buf, colorMap);
                int mask = buf.getInt();

                int baseX = bx << 2;
                int baseY = by << 2;
                for (int i = 0; i < 16; i++) {
                    int idx = (mask >> (i * 2)) & 0x3;
                    int px = colorMap[idx];
                    int a  = alphaMap[i] << 24;
                    int x = baseX + (i & 3);
                    int y = baseY + (i >>> 2);
                    if (x < w && y < h) {
                        outPx[y * w + x] = a | (px & 0x00FFFFFF);
                    }
                }
            }
        }
    }

    private static void decodeColorBlock(ByteBuffer buf, int[] cols) {
        int c0 = buf.getShort() & 0xFFFF;
        int c1 = buf.getShort() & 0xFFFF;
        cols[0] = convert565(c0);
        cols[1] = convert565(c1);
        if (c0 > c1) {
            cols[2] = blend(cols[0], cols[1], 2, 1);
            cols[3] = blend(cols[0], cols[1], 1, 2);
        } else {
            cols[2] = blend(cols[0], cols[1], 1, 1);
            cols[3] = 0; // transparent
        }
    }

    private static void decodeAlphaDXT3(ByteBuffer buf, int[] out) {
        for (int row = 0; row < 4; row++) {
            int rowData = buf.getShort() & 0xFFFF;
            int shift = 0;
            for (int col = 0; col < 4; col++, shift += 4) {
                int v = ((rowData >> shift) & 0xF) * 17;
                out[row * 4 + col] = v;
            }
        }
    }

    private static void decodeAlphaDXT5(ByteBuffer buf, int[] out) {
        int a0 = buf.get() & 0xFF;
        int a1 = buf.get() & 0xFF;
        long bits = buf.getLong();
        int[] table = makeAlphaTable(a0, a1);
        for (int i = 0; i < 16; i++) {
            int code = (int)((bits >> (i * 3)) & 0x07);
            out[i] = table[code];
        }
    }

    private static int[] makeAlphaTable(int a0, int a1) {
        int[] t = new int[8];
        t[0] = a0;
        t[1] = a1;
        if (a0 > a1) {
            for (int i = 2; i < 8; i++) {
                t[i] = ((8 - i) * a0 + (i - 1) * a1) / 7;
            }
        } else {
            for (int i = 2; i < 6; i++) {
                t[i] = ((6 - i) * a0 + (i - 1) * a1) / 5;
            }
            t[6] = 0;
            t[7] = 255;
        }
        return t;
    }

    private static int convert565(int val) {
        int r = ((val >>> 11) * 255) / 31;
        int g = (((val >>> 5) & 0x3F) * 255) / 63;
        int b = ((val & 0x1F) * 255) / 31;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int blend(int c0, int c1, int w0, int w1) {
        int a = (((c0 >>> 24) * w0) + ((c1 >>> 24) * w1)) / (w0 + w1);
        int r = (((c0 >>> 16) & 0xFF) * w0 + ((c1 >>> 16) & 0xFF) * w1) / (w0 + w1);
        int g = (((c0 >>> 8)  & 0xFF) * w0 + ((c1 >>> 8)  & 0xFF) * w1) / (w0 + w1);
        int b = (((c0        )  & 0xFF) * w0 + ((c1        )  & 0xFF) * w1) / (w0 + w1);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void fillCheckerboard(int w, int h, int[] px, int cellSize, int c1, int c2) {
        int wCells = (w + cellSize - 1) / cellSize;
        for (int y = 0; y < h; y++) {
            int row = (y / cellSize) & 1;
            int base = y * w;
            for (int x = 0; x < w; x++) {
                px[base + x] = (((x / cellSize) & 1) ^ row) == 0 ? c1 : c2;
            }
        }
    }
}