package org.foxesworld.cge.core.utils;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * High-performance DDS texture decoder optimized for AAA game engine performance.
 * Supports DXT1, DXT3, and DXT5 formats with advanced optimizations.
 *
 * Improvements/Fixes:
 * - Consistent handling of alpha and DXT1 transparency
 * - Defensive buffer and bounds checks
 * - Fixed DXT1 color block index bug (endianness, index shift)
 * - Optimized alpha index extraction for DXT5
 * - Improved fallback rendering for incomplete/corrupt data
 * - JavaDoc for all public methods
 * - Better separation of DXT1/3/5 fast paths
 * - Precomputed RGB565 LUTs
 * - Thread-safe, allocation-free decoding
 */
public final class DDSDecoder {
    // Format constants
    public static final byte FORMAT_DXT1 = 1;
    public static final byte FORMAT_DXT3 = 3;
    public static final byte FORMAT_DXT5 = 5;

    // Pre-computed tables for faster color conversion
    private static final int[] RGB565_RED_LUT = new int[32];
    private static final int[] RGB565_GREEN_LUT = new int[64];
    private static final int[] RGB565_BLUE_LUT = new int[32];

    // Thread-local work buffers to eliminate allocation in hot paths
    private static final ThreadLocal<WorkBuffers> THREAD_BUFFERS = ThreadLocal.withInitial(WorkBuffers::new);

    // Constants for faster computation
    private static final int BLOCK_SIZE = 4;
    private static final int BLOCK_PIXELS = BLOCK_SIZE * BLOCK_SIZE;
    private static final int DXT1_BLOCK_BYTES = 8;
    private static final int DXT3_BLOCK_BYTES = 16;
    private static final int DXT5_BLOCK_BYTES = 16;

    // Error messages
    private static final String ERR_INVALID_SIZE = "Invalid image dimensions: width=%d, height=%d";
    private static final String ERR_NULL_DATA = "DDS data buffer is null";
    private static final String ERR_BUFFER_TOO_SMALL = "DDS data buffer too small: %d bytes (expected at least %d)";
    private static final String ERR_UNSUPPORTED_FORMAT = "Unsupported DDS format: %d (expected 1, 3, or 5)";

    // Initialize lookup tables for RGB565 conversion
    static {
        for (int i = 0; i < 32; i++) {
            RGB565_RED_LUT[i] = (i * 255 / 31) << 16;
            RGB565_BLUE_LUT[i] = (i * 255 / 31);
        }
        for (int i = 0; i < 64; i++) {
            RGB565_GREEN_LUT[i] = (i * 255 / 63) << 8;
        }
    }

    /** Private constructor to prevent instantiation */
    private DDSDecoder() {}

    /**
     * Decodes DDS compressed texture data into a BufferedImage.
     *
     * @param width  The width of the image in pixels
     * @param height The height of the image in pixels
     * @param format The compression format (DXT1, DXT3, or DXT5)
     * @param data   The compressed texture data
     * @return A BufferedImage containing the decoded texture
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static BufferedImage decode(int width, int height, byte format, byte[] data) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(String.format(ERR_INVALID_SIZE, width, height));
        }
        if (data == null) {
            throw new IllegalArgumentException(ERR_NULL_DATA);
        }
        if (format != FORMAT_DXT1 && format != FORMAT_DXT3 && format != FORMAT_DXT5) {
            throw new IllegalArgumentException(String.format(ERR_UNSUPPORTED_FORMAT, format));
        }

        int blocksWide = (width + 3) >> 2;
        int blocksHigh = (height + 3) >> 2;
        int blockSize = (format == FORMAT_DXT1) ? DXT1_BLOCK_BYTES :
                (format == FORMAT_DXT3) ? DXT3_BLOCK_BYTES : DXT5_BLOCK_BYTES;
        int expectedSize = blocksWide * blocksHigh * blockSize;

        if (data.length < expectedSize) {
            throw new IllegalArgumentException(
                    String.format(ERR_BUFFER_TOO_SMALL, data.length, expectedSize));
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        WorkBuffers buffers = THREAD_BUFFERS.get();

        ByteBuffer buffer = ByteBuffer.wrap(data, 0, data.length)
                .order(ByteOrder.LITTLE_ENDIAN);

        switch (format) {
            case FORMAT_DXT1:
                decodeDXT1(width, height, buffer, pixels, blocksWide, blocksHigh, buffers);
                break;
            case FORMAT_DXT3:
                decodeDXT3(width, height, buffer, pixels, blocksWide, blocksHigh, buffers);
                break;
            case FORMAT_DXT5:
                decodeDXT5(width, height, buffer, pixels, blocksWide, blocksHigh, buffers);
                break;
            default:
                renderFallbackPattern(width, height, pixels);
        }

        return image;
    }

    // ---------------- DXT1 decoder ----------------

    private static void decodeDXT1(int width, int height, ByteBuffer buffer, int[] pixels,
                                   int blocksWide, int blocksHigh, WorkBuffers workBuffers) {
        for (int by = 0; by < blocksHigh; by++) {
            int y0 = by * BLOCK_SIZE;
            for (int bx = 0; bx < blocksWide; bx++) {
                int x0 = bx * BLOCK_SIZE;
                if (buffer.remaining() < DXT1_BLOCK_BYTES) {
                    renderFallbackPattern(width, height, pixels);
                    return;
                }
                decodeColorBlock(buffer, workBuffers.colorMap, true);
                int colorIndices = buffer.getInt(); // DXT1 indices are 4 bytes, little-endian
                int w = Math.min(BLOCK_SIZE, width - x0);
                int h = Math.min(BLOCK_SIZE, height - y0);
                outputBlock(pixels, width, x0, y0, w, h, workBuffers.colorMap, colorIndices, workBuffers.colorMap, null);
            }
        }
    }

    // ---------------- DXT3 decoder ----------------

    private static void decodeDXT3(int width, int height, ByteBuffer buffer, int[] pixels,
                                   int blocksWide, int blocksHigh, WorkBuffers workBuffers) {
        for (int by = 0; by < blocksHigh; by++) {
            int y0 = by * BLOCK_SIZE;
            for (int bx = 0; bx < blocksWide; bx++) {
                int x0 = bx * BLOCK_SIZE;
                if (buffer.remaining() < DXT3_BLOCK_BYTES) {
                    renderFallbackPattern(width, height, pixels);
                    return;
                }
                decodeAlphaDXT3(buffer, workBuffers.alphaMap);
                decodeColorBlock(buffer, workBuffers.colorMap, false);
                int colorIndices = buffer.getInt();
                int w = Math.min(BLOCK_SIZE, width - x0);
                int h = Math.min(BLOCK_SIZE, height - y0);
                outputBlock(pixels, width, x0, y0, w, h, workBuffers.colorMap, colorIndices, null, workBuffers.alphaMap);
            }
        }
    }

    // ---------------- DXT5 decoder ----------------

    private static void decodeDXT5(int width, int height, ByteBuffer buffer, int[] pixels,
                                   int blocksWide, int blocksHigh, WorkBuffers workBuffers) {
        for (int by = 0; by < blocksHigh; by++) {
            int y0 = by * BLOCK_SIZE;
            for (int bx = 0; bx < blocksWide; bx++) {
                int x0 = bx * BLOCK_SIZE;
                if (buffer.remaining() < DXT5_BLOCK_BYTES) {
                    renderFallbackPattern(width, height, pixels);
                    return;
                }
                decodeAlphaDXT5(buffer, workBuffers.alphaMap, workBuffers.alphaTable);
                decodeColorBlock(buffer, workBuffers.colorMap, false);
                int colorIndices = buffer.getInt();
                int w = Math.min(BLOCK_SIZE, width - x0);
                int h = Math.min(BLOCK_SIZE, height - y0);
                outputBlock(pixels, width, x0, y0, w, h, workBuffers.colorMap, colorIndices, null, workBuffers.alphaMap);
            }
        }
    }

    // ----------- Color and Alpha block decoders -----------

    /** Decodes a DXT color block with optimized color interpolation. */
    private static void decodeColorBlock(ByteBuffer buffer, int[] colorMap, boolean isDXT1) {
        // Read color endpoints
        int color0 = buffer.getShort() & 0xFFFF;
        int color1 = buffer.getShort() & 0xFFFF;
        colorMap[0] = 0xFF000000 |
                RGB565_RED_LUT[(color0 >> 11) & 0x1F] |
                RGB565_GREEN_LUT[(color0 >> 5) & 0x3F] |
                RGB565_BLUE_LUT[color0 & 0x1F];
        colorMap[1] = 0xFF000000 |
                RGB565_RED_LUT[(color1 >> 11) & 0x1F] |
                RGB565_GREEN_LUT[(color1 >> 5) & 0x3F] |
                RGB565_BLUE_LUT[color1 & 0x1F];
        if (isDXT1 && color0 <= color1) {
            colorMap[2] = blendColorsHalf(colorMap[0], colorMap[1]);
            colorMap[3] = 0; // Transparent
        } else {
            colorMap[2] = blendColors(colorMap[0], colorMap[1], 2, 1);
            colorMap[3] = blendColors(colorMap[0], colorMap[1], 1, 2);
        }
    }

    /** Decodes DXT3 alpha block (explicit 4-bpp alpha) */
    private static void decodeAlphaDXT3(ByteBuffer buffer, int[] alphaMap) {
        if (buffer.remaining() < 8) throw new IllegalArgumentException("DXT3 alpha block too small");
        for (int i = 0; i < 4; i++) {
            int rowData = buffer.getShort() & 0xFFFF;
            int rowOffset = i * 4;
            alphaMap[rowOffset]     = ((rowData >>  0) & 0xF) * 17;
            alphaMap[rowOffset + 1] = ((rowData >>  4) & 0xF) * 17;
            alphaMap[rowOffset + 2] = ((rowData >>  8) & 0xF) * 17;
            alphaMap[rowOffset + 3] = ((rowData >> 12) & 0xF) * 17;
        }
    }

    /** Decodes DXT5 alpha block (interpolated 3-bpp alpha) */
    private static void decodeAlphaDXT5(ByteBuffer buffer, int[] alphaMap, int[] alphaTable) {
        if (buffer.remaining() < 8) throw new IllegalArgumentException("DXT5 alpha block too small");
        int alpha0 = buffer.get() & 0xFF;
        int alpha1 = buffer.get() & 0xFF;
        generateAlphaTable(alpha0, alpha1, alphaTable);

        // Read 48 bits (6 bytes) of alpha indices
        long alphaBits = 0;
        for (int i = 0; i < 6; ++i) {
            alphaBits |= ((long)(buffer.get() & 0xFF)) << (8 * i);
        }
        for (int i = 0; i < 16; ++i) {
            int index = (int)((alphaBits >> (3 * i)) & 0x7);
            alphaMap[i] = alphaTable[index];
        }
    }

    /** Fills alphaTable for DXT5 alpha interpolation. */
    private static void generateAlphaTable(int alpha0, int alpha1, int[] table) {
        table[0] = alpha0;
        table[1] = alpha1;
        if (alpha0 > alpha1) {
            for (int i = 2; i < 8; i++) {
                table[i] = ((8 - i) * alpha0 + (i - 1) * alpha1) / 7;
            }
        } else {
            for (int i = 2; i < 6; i++) {
                table[i] = ((6 - i) * alpha0 + (i - 1) * alpha1) / 5;
            }
            table[6] = 0;
            table[7] = 255;
        }
    }

    // ---------------- Output block ----------------

    /**
     * Outputs a block of pixels to the pixel buffer.
     */
    private static void outputBlock(int[] pixels, int width, int blockX, int blockY,
                                    int blockWidth, int blockHeight, int[] colorMap,
                                    int colorIndices, int[] dxt1ColorMap, int[] explicitAlphaMap) {
        for (int y = 0; y < blockHeight; y++) {
            int dstRow = (blockY + y) * width + blockX;
            for (int x = 0; x < blockWidth; x++) {
                int pixelIndex = y * 4 + x;
                int dst = dstRow + x;
                if (dst < 0 || dst >= pixels.length) continue;
                int shift = pixelIndex * 2;
                int colorIndex = (colorIndices >> shift) & 0x3;
                int color = colorMap[colorIndex];
                if (explicitAlphaMap != null) {
                    int alpha = explicitAlphaMap[pixelIndex] << 24;
                    pixels[dst] = (color & 0x00FFFFFF) | alpha;
                } else if (dxt1ColorMap != null && colorIndex == 3 && (colorMap[0] <= colorMap[1])) {
                    pixels[dst] = 0; // Transparent for DXT1
                } else {
                    pixels[dst] = color;
                }
            }
        }
    }

    // ---------------- Color blending helpers ----------------

    private static int blendColors(int color1, int color2, int weight1, int weight2) {
        int total = weight1 + weight2;
        int r = ((color1 >> 16) & 0xFF) * weight1 + ((color2 >> 16) & 0xFF) * weight2;
        int g = ((color1 >> 8) & 0xFF) * weight1 + ((color2 >> 8) & 0xFF) * weight2;
        int b = (color1 & 0xFF) * weight1 + (color2 & 0xFF) * weight2;
        r /= total; g /= total; b /= total;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int blendColorsHalf(int color1, int color2) {
        // Average
        int rb = ((color1 & 0xFF00FF) + (color2 & 0xFF00FF)) >> 1 & 0xFF00FF;
        int g  = ((color1 & 0x00FF00) + (color2 & 0x00FF00)) >> 1 & 0x00FF00;
        return 0xFF000000 | rb | g;
    }

    // ---------------- Fallback/corrupt pattern ----------------

    private static void renderFallbackPattern(int width, int height, int[] pixels) {
        int cell = 8, color1 = 0xFFE040C0, color2 = 0xFF404040;
        int seed = width * 1000 + height;
        Random rnd = new Random(seed);
        int accent = 0xFF000000 | rnd.nextInt(0x1000000);
        for (int y = 0; y < height; y++) {
            int row = y * width;
            boolean oddRow = ((y / cell) & 1) == 1;
            for (int x = 0; x < width; x++) {
                boolean oddCol = ((x / cell) & 1) == 1;
                boolean isCorner = (x % cell < 2) && (y % cell < 2);
                if (row + x < 0 || row + x >= pixels.length) continue;
                if (isCorner) pixels[row + x] = accent;
                else pixels[row + x] = (oddRow ^ oddCol) ? color1 : color2;
            }
        }
    }

    /** Thread-local work buffers to avoid allocations. */
    private static class WorkBuffers {
        final int[] colorMap = new int[4];
        final int[] alphaMap = new int[16];
        final int[] alphaTable = new int[8];
    }
}