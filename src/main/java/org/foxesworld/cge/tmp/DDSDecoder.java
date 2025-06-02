package org.foxesworld.cge.tmp;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility for decoding DDS image data (DXT1, DXT3, DXT5) into a BufferedImage.
 * <p>
 * Supported formats:
 * <ul>
 *     <li>fmt = 1 (DXT1)</li>
 *     <li>fmt = 3 (DXT3)</li>
 *     <li>fmt = 5 (DXT5)</li>
 * </ul>
 * If an unsupported format is provided, a checkerboard pattern is drawn.
 */
public final class DDSDecoder {

    private DDSDecoder() {
        // Utility class; prevent instantiation.
    }

    /**
     * Decodes raw DDS-compressed data into a BufferedImage of the specified width and height.
     *
     * @param width    image width in pixels
     * @param height   image height in pixels
     * @param format   format code: 1 = DXT1, 3 = DXT3, 5 = DXT5
     * @param data     raw compressed byte array
     * @return decoded image as TYPE_INT_ARGB
     */
    public static BufferedImage decode(int width, int height, byte format, byte[] data) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        switch (format) {
            case 1 -> decodeBlocks(width, height, buf, img, false, false);
            case 3 -> decodeBlocks(width, height, buf, img, true, false);
            case 5 -> decodeBlocks(width, height, buf, img, false, true);
            default -> fillCheckerboard(img, 20);
        }

        return img;
    }

    /**
     * Draws a checkerboard pattern onto the image.
     *
     * @param img        target image
     * @param squareSize size of each checker square in pixels
     */
    private static void fillCheckerboard(BufferedImage img, int squareSize) {
        int w = img.getWidth();
        int h = img.getHeight();
        int cols = (w + squareSize - 1) / squareSize;
        int rows = (h + squareSize - 1) / squareSize;
        Graphics2D g = img.createGraphics();
        try {
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    boolean isLight = ((x + y) & 1) == 0;
                    g.setColor(isLight ? new Color(192, 192, 192) : new Color(128, 128, 128));
                    g.fillRect(x * squareSize, y * squareSize,
                            Math.min(squareSize, w - x * squareSize),
                            Math.min(squareSize, h - y * squareSize));
                }
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Decodes 4×4 blocks of DXT-compressed data into the image's pixel buffer.
     *
     * @param width            image width
     * @param height           image height
     * @param buf              ByteBuffer positioned at block data start
     * @param img              target BufferedImage (TYPE_INT_ARGB)
     * @param hasExplicitAlpha true for DXT3 (explicit 4-bit alpha per pixel)
     * @param hasSmoothAlpha   true for DXT5 (interpolated alpha)
     */
    private static void decodeBlocks(int width, int height, ByteBuffer buf, BufferedImage img,
                                     boolean hasExplicitAlpha, boolean hasSmoothAlpha) {
        int blockCols = (width + 3) >> 2;
        int blockRows = (height + 3) >> 2;
        int[] pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

        for (int by = 0; by < blockRows; by++) {
            for (int bx = 0; bx < blockCols; bx++) {
                int[] alphaArray = decodeAlpha(buf, hasExplicitAlpha, hasSmoothAlpha);
                int[] colorArray = decodeColors(buf);
                int bitmask = buf.getInt();

                for (int i = 0; i < 16; i++) {
                    int code = bitmask & 0x3;
                    bitmask >>>= 2;

                    int px = (bx << 2) + (i & 3);
                    int py = (by << 2) + (i >>> 2);

                    if (px < width && py < height) {
                        int a = alphaArray != null ? (alphaArray[i] & 0xFF) : 0xFF;
                        int rgb = colorArray[code] & 0x00FFFFFF;
                        int argb = (a << 24) | rgb;
                        pixels[py * width + px] = argb;
                    }
                }
            }
        }
    }

    /**
     * Reads and computes the 4-color palette for a 4×4 DXT block.
     *
     * @param buf ByteBuffer positioned at the start of a color block
     * @return array of 4 ARGB colors
     */
    private static int[] decodeColors(ByteBuffer buf) {
        int c0 = buf.getShort() & 0xFFFF;
        int c1 = buf.getShort() & 0xFFFF;
        int[] cols = new int[4];
        cols[0] = rgb565ToArgb(c0);
        cols[1] = rgb565ToArgb(c1);

        if (c0 > c1) {
            cols[2] = interpolate(cols[0], cols[1], 2, 1);
            cols[3] = interpolate(cols[0], cols[1], 1, 2);
        } else {
            cols[2] = interpolate(cols[0], cols[1], 1, 1);
            cols[3] = 0x00000000; // fully transparent black
        }

        return cols;
    }

    /**
     * Decodes the alpha values for a 4×4 block.
     *
     * @param buf      ByteBuffer positioned at the start of an alpha block
     * @param explicit true for DXT3 (explicit 4-bit alpha)
     * @param smooth   true for DXT5 (interpolated alpha)
     * @return array of 16 alpha values (0–255) or null if no alpha is present
     */
    private static int[] decodeAlpha(ByteBuffer buf, boolean explicit, boolean smooth) {
        if (explicit) {
            // DXT3: 64 bits of alpha, 4 bits per pixel
            int[] alpha16 = new int[16];
            int word1 = buf.getShort() & 0xFFFF;
            int word2 = buf.getShort() & 0xFFFF;
            // Extract 4-bit alphas for 16 pixels
            for (int i = 0; i < 4; i++) {
                int aNibble = (word1 >>> (i * 4)) & 0xF;
                int alphaVal = (aNibble << 4) | aNibble;
                for (int y = 0; y < 4; y++) {
                    alpha16[i + (y << 2)] = alphaVal;
                }
            }
            for (int i = 0; i < 4; i++) {
                int aNibble = (word2 >>> (i * 4)) & 0xF;
                int alphaVal = (aNibble << 4) | aNibble;
                for (int y = 0; y < 4; y++) {
                    alpha16[i + 4 + (y << 2)] = alphaVal;
                }
            }
            return alpha16;
        }

        if (smooth) {
            // DXT5: 2 base alphas, followed by 6 bytes of 3-bit indices (16 pixels)
            int a0 = buf.get() & 0xFF;
            int a1 = buf.get() & 0xFF;
            long bits = 0L;
            for (int i = 0; i < 6; i++) {
                bits |= ((long) (buf.get() & 0xFF)) << (8 * i);
            }
            int[] alphaLookup = new int[8];
            alphaLookup[0] = a0;
            alphaLookup[1] = a1;

            if (a0 > a1) {
                for (int i = 2; i < 8; i++) {
                    alphaLookup[i] = ((8 - i) * a0 + (i - 1) * a1) / 7;
                }
            } else {
                for (int i = 2; i < 6; i++) {
                    alphaLookup[i] = ((6 - i) * a0 + (i - 1) * a1) / 5;
                }
                alphaLookup[6] = 0;
                alphaLookup[7] = 0xFF;
            }

            int[] alpha16 = new int[16];
            for (int i = 0; i < 16; i++) {
                int index = (int) ((bits >>> (i * 3)) & 0x7);
                alpha16[i] = alphaLookup[index];
            }
            return alpha16;
        }

        return null;
    }

    /**
     * Converts a 16-bit RGB565 value to a 32-bit ARGB (opaque) integer.
     *
     * @param value 16-bit RGB565 value
     * @return 0xFFRRGGBB
     */
    private static int rgb565ToArgb(int value) {
        int r = ((value >>> 11) & 0x1F) << 3;
        int g = ((value >>> 5) & 0x3F) << 2;
        int b = (value & 0x1F) << 3;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Linearly interpolates between two ARGB colors using integer weights.
     *
     * @param c0 first ARGB color
     * @param c1 second ARGB color
     * @param w0 weight for c0
     * @param w1 weight for c1
     * @return interpolated ARGB color
     */
    private static int interpolate(int c0, int c1, int w0, int w1) {
        int a0 = (c0 >>> 24) & 0xFF, r0 = (c0 >>> 16) & 0xFF, g0 = (c0 >>> 8) & 0xFF, b0 = c0 & 0xFF;
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int wSum = w0 + w1;
        int a = (w0 * a0 + w1 * a1) / wSum;
        int r = (w0 * r0 + w1 * r1) / wSum;
        int g = (w0 * g0 + w1 * g1) / wSum;
        int b = (w0 * b0 + w1 * b1) / wSum;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}