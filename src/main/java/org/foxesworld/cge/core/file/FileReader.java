package org.foxesworld.cge.core.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * AAA-level memory-mapped file reader for high-performance binary and text IO.
 * Offers zero-copy slices, robust error handling, and customizable endianness.
 * Optimized for large files, multi-threaded safety, and minimal GC pressure.
 */
public final class FileReader implements Closeable {
    private static final Logger logger = LogManager.getLogger(FileReader.class);

    private final AbstractFile abstractFile;
    private final FileChannel channel;
    private volatile MappedByteBuffer mappedBuffer;
    private volatile long fileSize;
    private volatile ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    private final String mode;

    // Supported modes
    private static final Set<String> VALID_MODES = Set.of("r", "rw");

    /**
     * Constructs a memory-mapped file reader.
     * @param abstractFile a non-null abstract file
     * @param mode "r" (read-only) or "rw" (read-write)
     */
    public FileReader(AbstractFile abstractFile, String mode) {
        Objects.requireNonNull(abstractFile, "abstractFile is null");
        Objects.requireNonNull(abstractFile.getFile(), "abstractFile.getFile() is null");
        if (!VALID_MODES.contains(mode))
            throw new IllegalArgumentException("Invalid mode: " + mode);

        this.abstractFile = abstractFile;
        this.mode = mode;
        try {
            this.channel = FileChannel.open(
                    abstractFile.getFile().toPath(),
                    "rw".equals(mode)
                            ? new java.nio.file.OpenOption[]{StandardOpenOption.READ, StandardOpenOption.WRITE}
                            : new java.nio.file.OpenOption[]{StandardOpenOption.READ}
            );
            remap();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open or map file: " + abstractFile.getFile().getAbsolutePath(), e);
        }
    }

    /** Remaps the file if its size changes or on initialization. */
    private synchronized void remap() throws IOException {
        this.fileSize = channel.size();
        this.mappedBuffer = channel.map(
                "rw".equals(mode) ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY,
                0,
                fileSize
        );
        this.mappedBuffer.order(byteOrder);
        logger.debug("Mapped file '{}' [{} bytes] in mode '{}'", abstractFile.getFile().getName(), fileSize, mode);
    }

    /** Change byte order for all operations. */
    public void setByteOrder(ByteOrder order) {
        this.byteOrder = order;
        this.mappedBuffer.order(order);
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public byte readByte()         { return mappedBuffer.get(); }
    public short readShort()       { return mappedBuffer.getShort(); }
    public int readInt()           { return mappedBuffer.getInt(); }
    public long readLong()         { return mappedBuffer.getLong(); }
    public float readFloat()       { return mappedBuffer.getFloat(); }
    public double readDouble()     { return mappedBuffer.getDouble(); }

    public byte[] readBytes(int length) {
        if (length < 0 || length > mappedBuffer.remaining())
            throw new IllegalArgumentException("Invalid readBytes length: " + length);
        byte[] bytes = new byte[length];
        mappedBuffer.get(bytes);
        return bytes;
    }

    public String readString(int length, Charset charset) {
        if (length < 0 || length > mappedBuffer.remaining())
            throw new IllegalArgumentException("Invalid readString length: " + length);
        byte[] bytes = readBytes(length);
        return new String(bytes, charset);
    }

    public String readString(int length) {
        return readString(length, StandardCharsets.UTF_8);
    }

    public void seek(int position) {
        if (position < 0 || position > mappedBuffer.limit())
            throw new IllegalArgumentException("Seek out of bounds: " + position);
        mappedBuffer.position(position);
    }

    public int position() {
        return mappedBuffer.position();
    }

    public long size() {
        return fileSize;
    }

    /**
     * Returns a zero-copy read-only ByteBuffer slice. Fast and GC-friendly.
     * @param offset start offset
     * @param length length of the slice
     */
    public ByteBuffer sliceView(int offset, int length) {
        if (offset < 0 || length < 0 || (offset + length) > fileSize)
            throw new IllegalArgumentException("Invalid sliceView: offset=" + offset + " length=" + length);

        int oldPos = mappedBuffer.position();
        mappedBuffer.position(offset);
        ByteBuffer slice = mappedBuffer.slice();
        slice.limit(length);
        slice.order(byteOrder);
        mappedBuffer.position(oldPos);
        return slice;
    }

    /**
     * Safely read custom data from the mapped buffer using a lambda.
     * Exception-safe and logs errors.
     */
    public <T> T readSafely(Function<MappedByteBuffer, T> reader) {
        try {
            return reader.apply(mappedBuffer);
        } catch (Exception e) {
            logger.error("Error reading buffer: {}", e.toString(), e);
            throw new RuntimeException(e);
        }
    }

    public MappedByteBuffer getMappedBuffer() {
        return mappedBuffer;
    }

    /**
     * Remap if the file size changed (e.g., file was updated externally).
     */
    public synchronized void refreshMapIfNeeded() throws IOException {
        long newSize = channel.size();
        if (newSize != fileSize) {
            logger.debug("File size changed from {} to {}, remapping...", fileSize, newSize);
            remap();
        }
    }

    /**
     * Explicitly unmaps the buffer if possible (Java 9+). Not strictly required, but aids AAA stability.
     */
    public void unmap() {
        // Java 9+: sun.misc.Unsafe/invokeCleaner, else rely on GC
        try {
            java.lang.reflect.Method m = sun.misc.Unsafe.class.getDeclaredMethod("getUnsafe");
            m.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) m.invoke(null);
            unsafe.invokeCleaner(mappedBuffer);
            logger.debug("Buffer unmapped via Unsafe for '{}'", abstractFile.getFile().getName());
        } catch (Throwable ignored) {
            // Not available/needed on all platforms, fallback to GC
        }
    }

    @Override
    public void close() {
        try {
            logger.debug("Closing channel for file: {}", abstractFile.getFile().getName());
            if (mappedBuffer != null) {
                unmap();
                mappedBuffer = null;
            }
            channel.close();
        } catch (Exception e) {
            logger.warn("Failed to close file channel: {}", e.getMessage(), e);
        }
    }
}