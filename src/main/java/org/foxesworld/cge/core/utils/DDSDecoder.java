package org.foxesworld.cge.core.utils;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class DDSDecoder {
    private DDSDecoder() {}

    public static BufferedImage decode(int w, int h, byte f, byte[] d) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        ByteBuffer buf = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        if (f == 1 || f == 3 || f == 5) {
            decodeBlocks(w, h, buf, px, f == 3, f == 5);
        } else {
            int c1 = 0xFFC0C0C0, c2 = 0xFF808080;
            for (int y = 0; y < h; y += 20) {
                for (int x = 0; x < w; x += 20) {
                    for (int yy = y; yy < y + 20 && yy < h; yy++) {
                        for (int xx = x; xx < x + 20 && xx < w; xx++) {
                            px[yy * w + xx] = (((xx / 20 + yy / 20) & 1) == 0) ? c1 : c2;
                        }
                    }
                }
            }
        }
        return img;
    }

    private static void decodeBlocks(int w, int h, ByteBuffer buf, int[] px, boolean expA, boolean smlA) {
        int cols = (w + 3) >> 2, rows = (h + 3) >> 2;
        int[] aArr = new int[16], cArr = new int[4];
        for (int by = 0; by < rows; by++) {
            for (int bx = 0; bx < cols; bx++) {
                int[] aBlk = decodeAlpha(buf, expA, smlA, aArr);
                decodeColors(buf, cArr);
                int mask = buf.getInt();
                for (int i = 0; i < 16; i++, mask >>>= 2) {
                    int x = (bx << 2) + (i & 3), y = (by << 2) + (i >>> 2);
                    if (x < w && y < h) {
                        int col = cArr[mask & 3];
                        int a = (aBlk != null ? aBlk[i] : 255) << 24;
                        px[y * w + x] = a | (col & 0x00FFFFFF);
                    }
                }
            }
        }
    }

    private static void decodeColors(ByteBuffer buf, int[] o) {
        int c0 = buf.getShort() & 0xFFFF, c1 = buf.getShort() & 0xFFFF;
        o[0] = toArgb(c0);
        o[1] = toArgb(c1);
        if (c0 > c1) {
            o[2] = interp(o[0], o[1], 2, 1);
            o[3] = interp(o[0], o[1], 1, 2);
        } else {
            o[2] = interp(o[0], o[1], 1, 1);
            o[3] = 0;
        }
    }

    private static int[] decodeAlpha(ByteBuffer buf, boolean exp, boolean sml, int[] o) {
        if (exp) {
            for (int row = 0; row < 4; row++) {
                int w = buf.getShort() & 0xFFFF;
                for (int col = 0; col < 4; col++) {
                    int n = (w >>> (col * 4)) & 0xF, v = (n << 4) | n;
                    o[row * 4 + col] = v;
                }
            }
            return o;
        }
        if (sml) {
            int a0 = buf.get() & 0xFF, a1 = buf.get() & 0xFF;
            long bits = 0;
            for (int i = 0; i < 6; i++) bits |= ((long) (buf.get() & 0xFF)) << (8 * i);
            int[] t = new int[8];
            t[0] = a0; t[1] = a1;
            if (a0 > a1) {
                for (int i = 2; i < 8; i++) t[i] = ((8 - i) * a0 + (i - 1) * a1) / 7;
            } else {
                for (int i = 2; i < 6; i++) t[i] = ((6 - i) * a0 + (i - 1) * a1) / 5;
                t[6] = 0; t[7] = 255;
            }
            for (int i = 0; i < 16; i++) {
                int idx = (int) ((bits >>> (i * 3)) & 7);
                o[i] = t[idx];
            }
            return o;
        }
        return null;
    }

    private static int toArgb(int v) {
        int r = ((v >>> 11) & 0x1F) * 255 / 31;
        int g = ((v >>> 5) & 0x3F) * 255 / 63;
        int b = (v & 0x1F) * 255 / 31;
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private static int interp(int c0, int c1, int w0, int w1) {
        int a = (w0 * ((c0 >>> 24) & 0xFF) + w1 * ((c1 >>> 24) & 0xFF)) / (w0 + w1);
        int r = (w0 * ((c0 >>> 16) & 0xFF) + w1 * ((c1 >>> 16) & 0xFF)) / (w0 + w1);
        int g = (w0 * ((c0 >>> 8) & 0xFF) + w1 * ((c1 >>> 8) & 0xFF)) / (w0 + w1);
        int b = (w0 * (c0 & 0xFF) + w1 * (c1 & 0xFF)) / (w0 + w1);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
