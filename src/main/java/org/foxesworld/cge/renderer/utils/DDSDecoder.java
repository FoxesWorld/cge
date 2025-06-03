package org.foxesworld.cge.renderer.utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class DDSDecoder {

    private DDSDecoder() {}

    public static BufferedImage decode(int width, int height, byte format, byte[] data) {
        if (width <= 0 || height <= 0 || data == null) {
            throw new IllegalArgumentException("Invalid image dimensions or data");
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        switch (format) {
            case 1 -> decodeBlocks(width, height, buf, img, false, false); // DXT1
            case 3 -> decodeBlocks(width, height, buf, img, true, false);  // DXT3
            case 5 -> decodeBlocks(width, height, buf, img, false, true);  // DXT5
            default -> fillCheckerboard(img, 16);
        }

        return img;
    }

    private static void fillCheckerboard(BufferedImage img, int squareSize) {
        Graphics2D g = img.createGraphics();
        try {
            int w = img.getWidth(), h = img.getHeight();
            for (int y = 0; y < h; y += squareSize) {
                for (int x = 0; x < w; x += squareSize) {
                    boolean light = ((x / squareSize + y / squareSize) & 1) == 0;
                    g.setColor(light ? Color.LIGHT_GRAY : Color.DARK_GRAY);
                    g.fillRect(x, y, Math.min(squareSize, w - x), Math.min(squareSize, h - y));
                }
            }
        } finally {
            g.dispose();
        }
    }

    private static void decodeBlocks(int width, int height, ByteBuffer buf, BufferedImage img,
                                     boolean dxt3, boolean dxt5) {
        int[] pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        int blocksWide = (width + 3) / 4;
        int blocksHigh = (height + 3) / 4;

        for (int by = 0; by < blocksHigh; by++) {
            for (int bx = 0; bx < blocksWide; bx++) {
                int[] alpha = decodeAlpha(buf, dxt3, dxt5);
                int[] colors = decodeColors(buf);
                int indices = buf.getInt();

                for (int i = 0; i < 16; i++) {
                    int index = (indices >>> (2 * i)) & 0x3;
                    int px = bx * 4 + (i & 3);
                    int py = by * 4 + (i >>> 2);

                    if (px >= width || py >= height) continue;

                    int a = alpha != null ? alpha[i] & 0xFF : 0xFF;
                    int rgb = colors[index] & 0x00FFFFFF;
                    pixels[py * width + px] = (a << 24) | rgb;
                }
            }
        }
    }

    private static int[] decodeColors(ByteBuffer buf) {
        int c0 = buf.getShort() & 0xFFFF;
        int c1 = buf.getShort() & 0xFFFF;
        int[] out = new int[4];
        out[0] = rgb565ToArgb(c0);
        out[1] = rgb565ToArgb(c1);

        if (c0 > c1) {
            out[2] = interpolate(out[0], out[1], 2, 1);
            out[3] = interpolate(out[0], out[1], 1, 2);
        } else {
            out[2] = interpolate(out[0], out[1], 1, 1);
            out[3] = 0x00000000;
        }
        return out;
    }

    private static int[] decodeAlpha(ByteBuffer buf, boolean dxt3, boolean dxt5) {
        int[] alpha = new int[16];

        if (dxt3) {
            for (int i = 0; i < 8; i++) {
                int b = buf.get() & 0xFF;
                alpha[i * 2] = ((b & 0x0F) * 17);
                alpha[i * 2 + 1] = ((b >>> 4) * 17);
            }
            return alpha;
        }

        if (dxt5) {
            int a0 = buf.get() & 0xFF;
            int a1 = buf.get() & 0xFF;
            long bits = 0;
            for (int i = 0; i < 6; i++) {
                bits |= ((long) (buf.get() & 0xFF)) << (8 * i);
            }

            int[] alphaLUT = new int[8];
            alphaLUT[0] = a0;
            alphaLUT[1] = a1;

            if (a0 > a1) {
                for (int i = 2; i < 8; i++) {
                    alphaLUT[i] = ((8 - i) * a0 + (i - 1) * a1) / 7;
                }
            } else {
                for (int i = 2; i < 6; i++) {
                    alphaLUT[i] = ((6 - i) * a0 + (i - 1) * a1) / 5;
                }
                alphaLUT[6] = 0;
                alphaLUT[7] = 255;
            }

            for (int i = 0; i < 16; i++) {
                int index = (int) ((bits >>> (3 * i)) & 0x07);
                alpha[i] = alphaLUT[index];
            }

            return alpha;
        }

        return null;
    }

    private static int rgb565ToArgb(int value) {
        int r = ((value >> 11) & 0x1F) * 255 / 31;
        int g = ((value >> 5) & 0x3F) * 255 / 63;
        int b = (value & 0x1F) * 255 / 31;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int interpolate(int c0, int c1, int w0, int w1) {
        int a = ((c0 >>> 24) * w0 + (c1 >>> 24) * w1) / (w0 + w1);
        int r = (((c0 >> 16) & 0xFF) * w0 + ((c1 >> 16) & 0xFF) * w1) / (w0 + w1);
        int g = (((c0 >> 8) & 0xFF) * w0 + ((c1 >> 8) & 0xFF) * w1) / (w0 + w1);
        int b = ((c0 & 0xFF) * w0 + (c1 & 0xFF) * w1) / (w0 + w1);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
