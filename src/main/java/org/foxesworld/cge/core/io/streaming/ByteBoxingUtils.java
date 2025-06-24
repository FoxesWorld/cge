package org.foxesworld.cge.core.io.streaming;

public final class ByteBoxingUtils {
    private ByteBoxingUtils() {}

    public static Byte[] toObject(byte[] bytes) {
        if (bytes == null) return null;
        Byte[] boxed = new Byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            boxed[i] = bytes[i];
        }
        return boxed;
    }
}
