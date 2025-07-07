package org.foxesworld.cge.core.utils;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Improved DDS texture decoder optimized for AAA game engine workloads.
 * Enhancements include optional parallel decoding with ForkJoinPool (for large textures),
 * reduced buffer checks, and refined fallback pattern rendering.
 */
public final class DDSDecoder {
    // Format constants
    public static final byte FORMAT_DXT1 = 1;
    public static final byte FORMAT_DXT3 = 3;
    public static final byte FORMAT_DXT5 = 5;

    // --- Pre-computed Lookup Tables (LUTs) for fast RGB565 conversion ---
    private static final int[] RGB565_RED_LUT = new int[32];
    private static final int[] RGB565_GREEN_LUT = new int[64];
    private static final int[] RGB565_BLUE_LUT = new int[32];

    // Thread-local work buffers to eliminate allocation in hot paths
    private static final ThreadLocal<WorkBuffers> THREAD_BUFFERS = ThreadLocal.withInitial(WorkBuffers::new);

    // --- Constants for faster computation ---
    private static final int BLOCK_SIZE = 4;
    private static final int DXT1_BLOCK_BYTES = 8;
    private static final int DXT3_BLOCK_BYTES = 16;
    private static final int DXT5_BLOCK_BYTES = 16;

    // Use a shared ForkJoinPool to allow parallel decoding
    private static final ForkJoinPool DECODER_POOL = ForkJoinPool.commonPool();

    static {
        for (int i = 0; i < 32; i++) {
            RGB565_RED_LUT[i] = (i * 255 + 15) / 31;
            RGB565_BLUE_LUT[i] = (i * 255 + 15) / 31;
        }
        for (int i = 0; i < 64; i++) {
            RGB565_GREEN_LUT[i] = (i * 255 + 31) / 63;
        }
    }

    /** Private constructor to prevent instantiation. */
    private DDSDecoder() {}

    /**
     * Decodes DDS compressed texture data into a {@link BufferedImage}.
     * This method is the main entry point for the decoder. Optionally runs in parallel.
     *
     * @param width  The width of the image in pixels. Must be > 0.
     * @param height The height of the image in pixels. Must be > 0.
     * @param format The compression format (DXT1, DXT3, or DXT5).
     * @param data   The byte array containing the compressed texture data. Must not be null.
     * @param parallel If true, decoding is attempted in parallel for large textures.
     * @return A {@code BufferedImage} containing the decoded texture in {@code TYPE_INT_ARGB} format.
     * @throws IllegalArgumentException if parameters are invalid or the data buffer is too small.
     */
    public static BufferedImage decode(int width, int height, byte format, byte[] data, boolean parallel) {
        // --- Input Validation ---
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid image dimensions: " + width + "x" + height);
        }
        if (data == null) {
            throw new IllegalArgumentException("DDS data buffer is null.");
        }

        final int blockSize = switch (format) {
            case FORMAT_DXT1 -> DXT1_BLOCK_BYTES;
            case FORMAT_DXT3, FORMAT_DXT5 -> DXT3_BLOCK_BYTES;
            default -> throw new IllegalArgumentException("Unsupported DDS format: " + format);
        };

        final int blocksWide = (width + 3) >> 2;
        final int blocksHigh = (height + 3) >> 2;
        final int expectedSize = blocksWide * blocksHigh * blockSize;

        if (data.length < expectedSize) {
            throw new IllegalArgumentException(String.format(
                    "DDS data buffer too small: %d bytes (expected at least %d)", data.length, expectedSize));
        }

        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        final ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        if (parallel && (blocksWide * blocksHigh > 8)) {
            // Parallel decoding of larger texture
            DECODER_POOL.invoke(new DecodeTask(buffer, pixels, width, height, format, blocksWide, blocksHigh));
        } else {
            // Sequential decoding
            decodeSequential(width, height, format, buffer, pixels, blocksWide, blocksHigh);
        }
        return image;
    }

    /**
     * Sequential decoding used by decode() for smaller textures or when parallel=false.
     */
    private static void decodeSequential(int width, int height, byte format, ByteBuffer buffer,
                                         int[] pixels, int blocksWide, int blocksHigh) {
        final WorkBuffers wb = THREAD_BUFFERS.get();
        switch (format) {
            case FORMAT_DXT1 -> decodeDXT1(width, height, buffer, pixels, blocksWide, blocksHigh, wb);
            case FORMAT_DXT3 -> decodeDXT3(width, height, buffer, pixels, blocksWide, blocksHigh, wb);
            case FORMAT_DXT5 -> decodeDXT5(width, height, buffer, pixels, blocksWide, blocksHigh, wb);
        }
    }

    /** Decodes DXT1 blocks. */
    private static void decodeDXT1(int width, int height, ByteBuffer buffer, int[] pixels, int blocksWide,
                                   int blocksHigh, WorkBuffers buffers) {
        for (int by = 0; by < blocksHigh; by++) {
            final int y0 = by * BLOCK_SIZE;
            for (int bx = 0; bx < blocksWide; bx++) {
                if (buffer.remaining() < DXT1_BLOCK_BYTES) {
                    renderFallbackPattern(width, height, pixels);
                    return;
                }
                final int x0 = bx * BLOCK_SIZE;
                final int c0 = buffer.getShort() & 0xFFFF;
                final int c1 = buffer.getShort() & 0xFFFF;
                decodeColorBlock(c0, c1, buffers.colorMap, true);
                final int colorIndices = buffer.getInt();

                final int w = Math.min(BLOCK_SIZE, width - x0);
                final int h = Math.min(BLOCK_SIZE, height - y0);
                outputBlockDXT1(pixels, width, x0, y0, w, h, buffers.colorMap, colorIndices, c0 <= c1);
            }
        }
    }

    /** Decodes DXT3 blocks. */
    private static void decodeDXT3(int width, int height, ByteBuffer buffer, int[] pixels, int blocksWide,
                                   int blocksHigh, WorkBuffers buffers) {
        for (int by = 0; by < blocksHigh; by++) {
            final int y0 = by * BLOCK_SIZE;
            for (int bx = 0; bx < blocksWide; bx++) {
                if (buffer.remaining() < DXT3_BLOCK_BYTES) {
                    renderFallbackPattern(width, height, pixels);
                    return;
                }
                final int x0 = bx * BLOCK_SIZE;
                decodeAlphaDXT3(buffer.getLong(), buffers.alphaMap);
                final int c0 = buffer.getShort() & 0xFFFF;
                final int c1 = buffer.getShort() & 0xFFFF;
                decodeColorBlock(c0, c1, buffers.colorMap, false);
                final int colorIndices = buffer.getInt();

                final int w = Math.min(BLOCK_SIZE, width - x0);
                final int h = Math.min(BLOCK_SIZE, height - y0);
                outputBlockWithAlpha(pixels, width, x0, y0, w, h, buffers.colorMap, colorIndices, buffers.alphaMap);
            }
        }
    }

    /** Decodes DXT5 blocks. */
    private static void decodeDXT5(int width, int height, ByteBuffer buffer, int[] pixels, int blocksWide,
                                   int blocksHigh, WorkBuffers buffers) {
        for (int by = 0; by < blocksHigh; by++) {
            final int y0 = by * BLOCK_SIZE;
            for (int bx = 0; bx < blocksWide; bx++) {
                if (buffer.remaining() < DXT5_BLOCK_BYTES) {
                    renderFallbackPattern(width, height, pixels);
                    return;
                }
                final int x0 = bx * BLOCK_SIZE;
                decodeAlphaDXT5(buffer, buffers.alphaMap, buffers.alphaTable);
                final int c0 = buffer.getShort() & 0xFFFF;
                final int c1 = buffer.getShort() & 0xFFFF;
                decodeColorBlock(c0, c1, buffers.colorMap, false);
                final int colorIndices = buffer.getInt();

                final int w = Math.min(BLOCK_SIZE, width - x0);
                final int h = Math.min(BLOCK_SIZE, height - y0);
                outputBlockWithAlpha(pixels, width, x0, y0, w, h, buffers.colorMap, colorIndices, buffers.alphaMap);
            }
        }
    }

    /** Decodes a DXT color block. */
    private static void decodeColorBlock(int c0, int c1, int[] colorMap, boolean isDXT1) {
        final int r0 = RGB565_RED_LUT[(c0 >> 11) & 0x1F];
        final int g0 = RGB565_GREEN_LUT[(c0 >> 5) & 0x3F];
        final int b0 = RGB565_BLUE_LUT[c0 & 0x1F];

        final int r1 = RGB565_RED_LUT[(c1 >> 11) & 0x1F];
        final int g1 = RGB565_GREEN_LUT[(c1 >> 5) & 0x3F];
        final int b1 = RGB565_BLUE_LUT[c1 & 0x1F];

        colorMap[0] = 0xFF000000 | (r0 << 16) | (g0 << 8) | b0;
        colorMap[1] = 0xFF000000 | (r1 << 16) | (g1 << 8) | b1;

        if (isDXT1 && c0 <= c1) {
            colorMap[2] = 0xFF000000 | (((r0 + r1) >> 1) << 16) | (((g0 + g1) >> 1) << 8) | ((b0 + b1) >> 1);
            colorMap[3] = 0; // Transparent black for DXT1
        } else {
            colorMap[2] = 0xFF000000 | ((r0 * 2 + r1) / 3 << 16) | ((g0 * 2 + g1) / 3 << 8) | ((b0 * 2 + b1) / 3);
            colorMap[3] = 0xFF000000 | ((r0 + r1 * 2) / 3 << 16) | ((g0 + g1 * 2) / 3 << 8) | ((b0 + b1 * 2) / 3);
        }
    }

    /** Decodes a DXT3 alpha block from a 64-bit value. */
    private static void decodeAlphaDXT3(long alphaBits, int[] alphaMap) {
        for (int i = 0; i < 16; i++) {
            alphaMap[i] = (int) (((alphaBits >> (i * 4)) & 0xF) * 17);
        }
    }

    /** Decodes a DXT5 alpha block from the buffer. */
    private static void decodeAlphaDXT5(ByteBuffer buffer, int[] alphaMap, int[] alphaTable) {
        final int a0 = buffer.get() & 0xFF;
        final int a1 = buffer.get() & 0xFF;
        generateAlphaTable(a0, a1, alphaTable);

        // Grab the next 6 bytes for alpha bits
        final long alphaBits = buffer.getLong(buffer.position() - 6) & 0x0000FFFFFFFFFFFFL;

        for (int i = 0; i < 16; i++) {
            alphaMap[i] = alphaTable[(int) ((alphaBits >> (i * 3)) & 0x7)];
        }
    }

    /** Fills the alpha table for DXT5 alpha interpolation. */
    private static void generateAlphaTable(int a0, int a1, int[] table) {
        table[0] = a0;
        table[1] = a1;
        if (a0 > a1) {
            table[2] = (6 * a0 + a1 + 3) / 7;
            table[3] = (5 * a0 + 2 * a1 + 3) / 7;
            table[4] = (4 * a0 + 3 * a1 + 3) / 7;
            table[5] = (3 * a0 + 4 * a1 + 3) / 7;
            table[6] = (2 * a0 + 5 * a1 + 3) / 7;
            table[7] = (a0 + 6 * a1 + 3) / 7;
        } else {
            table[2] = (4 * a0 + a1 + 2) / 5;
            table[3] = (3 * a0 + 2 * a1 + 2) / 5;
            table[4] = (2 * a0 + 3 * a1 + 2) / 5;
            table[5] = (a0 + 4 * a1 + 2) / 5;
            table[6] = 0;
            table[7] = 255;
        }
    }

    /** Optimized block writer for DXT1. */
    private static void outputBlockDXT1(int[] pixels, int width, int x0, int y0, int w, int h,
                                        int[] colorMap, int indices, boolean hasAlpha) {
        final int transparentColor = colorMap[3];
        for (int y = 0; y < h; y++) {
            int dstIdx = (y0 + y) * width + x0;
            for (int x = 0; x < w; x++) {
                int index = (indices >> ((y << 2 | x) << 1)) & 0x3;
                int color = colorMap[index];
                if (hasAlpha && index == 3) {
                    pixels[dstIdx + x] = transparentColor;
                } else {
                    pixels[dstIdx + x] = color;
                }
            }
        }
    }

    /** Optimized block writer for DXT3/DXT5. */
    private static void outputBlockWithAlpha(int[] pixels, int width, int x0, int y0, int w, int h,
                                             int[] colorMap, int indices, int[] alphaMap) {
        for (int y = 0; y < h; y++) {
            int dstIdx = (y0 + y) * width + x0;
            for (int x = 0; x < w; x++) {
                int pixelOffset = (y << 2) + x;
                int colorIndex = (indices >> (pixelOffset << 1)) & 0x3;
                pixels[dstIdx + x] = (colorMap[colorIndex] & 0x00FFFFFF) | (alphaMap[pixelOffset] << 24);
            }
        }
    }

    /** Renders a fallback pattern for corrupt or unsupported data. */
    private static void renderFallbackPattern(int width, int height, int[] pixels) {
        final int cell = 8, color1 = 0xFFE040C0, color2 = 0xFF404040;
        final Random rnd = new Random(width * 1000L + height);
        final int accent = 0xFF000000 | rnd.nextInt(0x1000000);
        for (int y = 0; y < height; y++) {
            int row = y * width;
            boolean oddRow = ((y / cell) & 1) == 1;
            for (int x = 0; x < width; x++) {
                boolean oddCol = ((x / cell) & 1) == 1;
                pixels[row + x] = (oddRow ^ oddCol) ? color1 : color2;
                if ((x % cell == 0) || (y % cell == 0)) {
                    pixels[row + x] = accent;
                }
            }
        }
    }

    /** A container for thread-local work buffers to avoid re-allocation. */
    private static class WorkBuffers {
        final int[] colorMap = new int[4];
        final int[] alphaMap = new int[16];
        final int[] alphaTable = new int[8];
    }

    /**
     * RecursiveAction wrapper to enable parallel decoding.
     * Splits the blocks area and decodes each portion concurrently.
     */
    private static class DecodeTask extends RecursiveAction {
        private static final long serialVersionUID = 1L;

        private final ByteBuffer buffer;
        private final int[] pixels;
        private final int width, height;
        private final byte format;
        private final int blocksWide, blocksHigh;
        private static final int THRESHOLD = 2; // minimal height in blocks to split further

        DecodeTask(ByteBuffer buffer, int[] pixels, int width, int height,
                   byte format, int blocksWide, int blocksHigh) {
            this.buffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.format = format;
            this.blocksWide = blocksWide;
            this.blocksHigh = blocksHigh;
        }

        @Override
        protected void compute() {
            if (blocksHigh <= THRESHOLD) {
                decodeSequential(width, height, format, buffer, pixels, blocksWide, blocksHigh);
            } else {
                int mid = blocksHigh / 2;
                int offset = mid * blocksWide * blockSize();
                ByteBuffer topBuffer = sliceBuffer(buffer, 0, offset);
                ByteBuffer bottomBuffer = sliceBuffer(buffer, offset, buffer.remaining() - offset);

                DecodeTask topTask = new DecodeTask(topBuffer, pixels, width, height, format, blocksWide, mid);
                DecodeTask bottomTask = new DecodeTask(bottomBuffer, pixels, width, height, format, blocksWide, blocksHigh - mid);
                invokeAll(topTask, bottomTask);
            }
        }

        private int blockSize() {
            return switch (format) {
                case FORMAT_DXT1 -> DXT1_BLOCK_BYTES;
                case FORMAT_DXT3, FORMAT_DXT5 -> DXT3_BLOCK_BYTES;
                default -> 0; // won't happen with valid format
            };
        }

        // Slices a ByteBuffer without modifying the original buffer's position
        private ByteBuffer sliceBuffer(ByteBuffer original, int start, int length) {
            ByteBuffer dup = original.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            dup.position(start).limit(start + length);
            return dup.slice().order(ByteOrder.LITTLE_ENDIAN);
        }
    }
}