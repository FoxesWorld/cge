package org.foxesworld.cge.core.io.streaming;

import java.util.Objects;

/**
 * Utility methods for converting between the primitive {@code byte[]} array
 * and its boxed equivalent {@code Byte[]}.
 */
public final class ByteBoxingUtils {

    private ByteBoxingUtils() {
        // Prevent instantiation
    }

    /**
     * Boxes an entire primitive byte array into a {@code Byte[]} array.
     *
     * @param bytes the source primitive array
     * @return a new {@code Byte[]} of the same length, or {@code null} if input is {@code null}
     */
    public static Byte[] toObject(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        Byte[] boxed = new Byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            boxed[i] = bytes[i];  // auto-boxing
        }
        return boxed;
    }

    /**
     * Boxes a subrange of the primitive byte array into a {@code Byte[]} array.
     *
     * @param bytes  the source primitive array
     * @param offset the starting index in the source array
     * @param length the number of bytes to copy
     * @return a new {@code Byte[]} of length {@code length}, or {@code null} if input is {@code null}
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length} is negative,
     *                                   or {@code offset + length} exceeds the array length
     */
    public static Byte[] toObject(byte[] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (offset < 0 || length < 0 || offset + length > bytes.length) {
            throw new IndexOutOfBoundsException(
                    String.format("Invalid offset=%d, length=%d for array of size %d",
                            offset, length, bytes.length));
        }
        Byte[] boxed = new Byte[length];
        for (int i = 0; i < length; i++) {
            boxed[i] = bytes[offset + i];
        }
        return boxed;
    }

    /**
     * Unboxes an entire {@code Byte[]} array into a primitive {@code byte[]} array.
     *
     * @param bytes the source boxed array
     * @return a new primitive {@code byte[]} of the same length, or {@code null} if input is {@code null}
     * @throws NullPointerException if any element in the input array is {@code null}
     */
    public static byte[] toPrimitive(Byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        byte[] unboxed = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            // Throws NPE if any boxed element is null
            unboxed[i] = Objects.requireNonNull(bytes[i], "Null Byte at index " + i);
        }
        return unboxed;
    }

    /**
     * Unboxes a subrange of the {@code Byte[]} array into a primitive {@code byte[]} array.
     *
     * @param bytes  the source boxed array
     * @param offset the starting index in the source array
     * @param length the number of bytes to copy
     * @return a new primitive {@code byte[]} of length {@code length}, or {@code null} if input is {@code null}
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length} is negative,
     *                                   or {@code offset + length} exceeds the array length
     * @throws NullPointerException      if any element in the specified range is {@code null}
     */
    public static byte[] toPrimitive(Byte[] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (offset < 0 || length < 0 || offset + length > bytes.length) {
            throw new IndexOutOfBoundsException(
                    String.format("Invalid offset=%d, length=%d for Byte[] of size %d",
                            offset, length, bytes.length));
        }
        byte[] unboxed = new byte[length];
        for (int i = 0; i < length; i++) {
            unboxed[i] = Objects.requireNonNull(bytes[offset + i],
                    "Null Byte at index " + (offset + i));
        }
        return unboxed;
    }
}
