package org.foxesworld.cge.core.utils;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * DDSDecoder: поддерживает DXT1, DXT3, DXT5 форматы (f=1,3,5).
 * При нераспознанном формате рисует шахматку.
 */
public final class DDSDecoder {
    private DDSDecoder() {}

    /**
     * Декодирует DDS-данные в BufferedImage ARGB.
     * @param width  ширина
     * @param height высота
     * @param format формат: 1->DXT1,3->DXT3,5->DXT5
     * @param data   байты изображения
     */
    public static BufferedImage decode(int width, int height, byte format, byte[] data) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        if (format == 1 || format == 3 || format == 5) {
            decodeDXT(width, height, buf, pixels, format);
        } else {
            fillCheckerboard(width, height, pixels, 20, 0xFFC0C0C0, 0xFF808080);
        }
        return image;
    }

    private static void decodeDXT(int w, int h, ByteBuffer buf, int[] px, byte fmt) {
        int cols = (w + 3) / 4, rows = (h + 3) / 4;
        int[] alphaBlock = new int[16];
        int[] colorTable = new int[4];

        boolean hasExplicitAlpha = fmt == 3;
        boolean hasSmallAlpha    = fmt == 5;

        for (int by = 0; by < rows; by++) {
            for (int bx = 0; bx < cols; bx++) {
                int[] alphaArr = hasExplicitAlpha ? decodeAlphaExplicit(buf)
                        : hasSmallAlpha    ? decodeAlphaSmall(buf)
                        : null;
                decodeColorBlock(buf, colorTable);
                int mask = buf.getInt();

                for (int i = 0; i < 16; i++, mask >>>= 2) {
                    int x = (bx << 2) + (i & 3);
                    int y = (by << 2) + (i >> 2);
                    if (x < w && y < h) {
                        int col = colorTable[mask & 3];
                        int a   = (alphaArr != null ? alphaArr[i] : 255) << 24;
                        px[y * w + x] = a | (col & 0x00FFFFFF);
                    }
                }
            }
        }
    }

    private static void decodeColorBlock(ByteBuffer buf, int[] colors) {
        int c0 = buf.getShort() & 0xFFFF;
        int c1 = buf.getShort() & 0xFFFF;
        colors[0] = toARGB(c0);
        colors[1] = toARGB(c1);

        if (c0 > c1) {
            colors[2] = mix(colors[0], colors[1], 2, 1);
            colors[3] = mix(colors[0], colors[1], 1, 2);
        } else {
            colors[2] = mix(colors[0], colors[1], 1, 1);
            colors[3] = 0x00000000;
        }
    }

    private static int[] decodeAlphaExplicit(ByteBuffer buf) {
        int[] a = new int[16];
        for (int row = 0; row < 4; row++) {
            int bits = buf.getShort() & 0xFFFF;
            for (int col = 0; col < 4; col++) {
                int nibble = (bits >> (col * 4)) & 0xF;
                a[row * 4 + col] = (nibble << 4) | nibble;
            }
        }
        return a;
    }

    private static int[] decodeAlphaSmall(ByteBuffer buf) {
        int a0 = buf.get() & 0xFF;
        int a1 = buf.get() & 0xFF;
        long bits = 0;
        for (int i = 0; i < 6; i++) {
            bits |= (long)(buf.get() & 0xFF) << (8 * i);
        }
        int[] table = buildAlphaTable(a0, a1);
        int[] a  = new int[16];
        for (int i = 0; i < 16; i++) {
            int idx = (int)((bits >> (i * 3)) & 7);
            a[i] = table[idx];
        }
        return a;
    }

    private static int[] buildAlphaTable(int a0, int a1) {
        int[] t = new int[8];
        t[0] = a0; t[1] = a1;
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

    private static int toARGB(int val) {
        int r = ((val >> 11) & 0x1F) * 255 / 31;
        int g = ((val >>  5) & 0x3F) * 255 / 63;
        int b = ( val        & 0x1F) * 255 / 31;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int c0, int c1, int w0, int w1) {
        int a = ((c0 >>> 24) * w0 + (c1 >>> 24) * w1) / (w0 + w1);
        int r = (((c0 >>> 16) & 0xFF) * w0 + ((c1 >>> 16) & 0xFF) * w1) / (w0 + w1);
        int g = (((c0 >>>  8) & 0xFF) * w0 + ((c1 >>>  8) & 0xFF) * w1) / (w0 + w1);
        int b = (((c0       ) & 0xFF) * w0 + ((c1       ) & 0xFF) * w1) / (w0 + w1);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void fillCheckerboard(int w, int h, int[] px, int cellSize, int c1, int c2) {
        for (int y = 0; y < h; y++) {
            int row = y / cellSize;
            for (int x = 0; x < w; x++) {
                px[y * w + x] = ((x / cellSize + row) & 1) == 0 ? c1 : c2;
            }
        }
    }
}
