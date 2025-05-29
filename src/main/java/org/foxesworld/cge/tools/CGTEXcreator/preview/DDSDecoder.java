package org.foxesworld.cge.tools.CGTEXcreator.preview;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

public final class DDSDecoder {
    private static final Logger logger = Logger.getLogger(DDSDecoder.class.getName());

    public static BufferedImage decode(int w, int h, byte fmt, byte[] data) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        switch (fmt) {
            case 1 -> decodeBlocks(w, h, buf, img, false, false);
            case 3 -> decodeBlocks(w, h, buf, img, true, false);
            case 5 -> decodeBlocks(w, h, buf, img, false, true);
            default -> fillChecker(img);
        }
        return img;
    }

    private static void fillChecker(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight(), gs = 20;
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        for (int x = 0; x < w; x += gs) {
            for (int y = 0; y < h; y += gs) {
                g.setColor(((x + y) & gs) == 0 ? new Color(128, 0, 128) : Color.BLACK);
                g.drawLine(x, 0, x, h);
                g.drawLine(0, y, w, y);
            }
        }
        g.dispose();
    }

    private static void decodeBlocks(int w, int h, ByteBuffer buf, BufferedImage img,
                                     boolean hasExplicitAlpha, boolean hasSmoothAlpha) {
        int bw = (w + 3) / 4, bh = (h + 3) / 4;
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                int[] alpha = decodeAlpha(buf, hasExplicitAlpha, hasSmoothAlpha);
                int[] cols = decodeColors(buf);
                int bits = buf.getInt();
                for (int i = 0; i < 16; i++) {
                    int ci = bits & 3; bits >>>= 2;
                    int x = bx * 4 + (i & 3), y = by * 4 + (i >>> 2);
                    if (x < w && y < h) {
                        int a = alpha != null ? alpha[i] & 0xFF : 0xFF;
                        int rgb = cols[ci] & 0x00FFFFFF;
                        img.setRGB(x, y, (a << 24) | rgb);
                    }
                }
            }
        }
    }

    private static int[] decodeColors(ByteBuffer buf) {
        int c0 = buf.getShort() & 0xFFFF, c1 = buf.getShort() & 0xFFFF;
        int[] cols = new int[4];
        cols[0] = rgb565(c0);
        cols[1] = rgb565(c1);
        if (c0 > c1) {
            cols[2] = interp(cols[0], cols[1], 2, 1);
            cols[3] = interp(cols[0], cols[1], 1, 2);
        } else {
            cols[2] = interp(cols[0], cols[1], 1, 1);
            cols[3] = 0;
        }
        return cols;
    }

    private static int[] decodeAlpha(ByteBuffer buf, boolean explicit, boolean smooth) {
        if (!explicit && !smooth) return null;
        if (explicit) {
            int[] alpha = new int[16];
            int a1 = buf.getShort() & 0xFFFF;
            int a2 = buf.getShort() & 0xFFFF;
            for (int i = 0; i < 8; i++) {
                int v = (i < 4 ? (a1 >> (i * 4)) : (a2 >> ((i - 4) * 4))) & 0xF;
                alpha[i] = (v << 4) | (v & 0xF);
            }
            return expandAlpha(alpha);
        } else {
            int a0 = buf.get() & 0xFF, a1 = buf.get() & 0xFF;
            long bits = 0;
            for (int i = 0; i < 6; i++) bits |= (long)(buf.get() & 0xFF) << (8 * i);
            int[] alpha = new int[8];
            alpha[0] = a0; alpha[1] = a1;
            if (a0 > a1) for (int i = 2; i < 8; i++) alpha[i] = ((8 - i) * a0 + (i - 1) * a1) / 7;
            else {
                for (int i = 2; i < 6; i++) alpha[i] = ((6 - i) * a0 + (i - 1) * a1) / 5;
                alpha[6] = 0; alpha[7] = 0xFF;
            }
            int[] out = new int[16];
            for (int i = 0; i < 16; i++) out[i] = alpha[(int)((bits >> (i * 3)) & 7)];
            return out;
        }
    }

    private static int[] expandAlpha(int[] a4) {
        int[] a16 = new int[16];
        for (int i = 0; i < 4; i++) {
            int v = a4[i];
            for (int j = 0; j < 4; j++) a16[i * 4 + j] = v;
        }
        return a16;
    }

    private static int rgb565(int v) {
        int r = ((v >>> 11) & 0x1F) << 3;
        int g = ((v >>> 5) & 0x3F) << 2;
        int b = (v & 0x1F) << 3;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int interp(int c0, int c1, int w0, int w1) {
        int a0 = (c0 >>> 24) & 0xFF, r0 = (c0 >>> 16) & 0xFF, g0 = (c0 >>> 8) & 0xFF, b0 = c0 & 0xFF;
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int w = w0 + w1;
        return ((w0 * a0 + w1 * a1) / w) << 24 |
                ((w0 * r0 + w1 * r1) / w) << 16 |
                ((w0 * g0 + w1 * g1) / w) << 8  |
                ((w0 * b0 + w1 * b1) / w);
    }
}