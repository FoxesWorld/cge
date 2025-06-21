package org.foxesworld.cge.core.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * AAA-level memory-mapped file reader for high-performance binary and text IO.
 * Offers zero-copy slices, robust error handling, and customizable endianness.
 * Optimized for large files, multi-threaded safety, and minimal GC pressure.
 * <p>
 * Features:
 * <ul>
 *   <li>Thread-safe operations with position preservation</li>
 *   <li>Configurable byte order for cross-platform compatibility</li>
 *   <li>Efficient memory management with proper resource cleanup</li>
 *   <li>Support for both read and write operations</li>
 *   <li>Automatic buffer refresh when file size changes</li>
 * </ul>
 */
public final class FileReader implements Closeable {
    private static final Logger logger = LogManager.getLogger(FileReader.class);

    // Constants
    private static final Set<String> VALID_MODES = Set.of("r", "rw");
    private static final int MAX_AUTO_REMAP_SIZE = 1024 * 1024 * 1024; // 1GB

    // Core file access resources
    private final AbstractFile abstractFile;
    private final FileChannel channel;
    private volatile MappedByteBuffer mappedBuffer;
    private volatile long fileSize;
    private volatile ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    private final String mode;

    // Thread safety
    private final ReadWriteLock bufferLock = new ReentrantReadWriteLock();
    private volatile boolean closed = false;

    /**
     * Constructs a memory-mapped file reader.
     *
     * @param abstractFile a non-null abstract file
     * @param mode "r" (read-only) or "rw" (read-write)
     * @throws NullPointerException if abstractFile or its file is null
     * @throws IllegalArgumentException if mode is invalid or file cannot be accessed
     * @throws IOException if file cannot be opened or mapped
     */
    public FileReader(AbstractFile abstractFile, String mode) {
        Objects.requireNonNull(abstractFile, "abstractFile is null");
        Objects.requireNonNull(abstractFile.getFile(), "abstractFile.getFile() is null");

        if (!VALID_MODES.contains(mode)) {
            throw new IllegalArgumentException("Invalid mode: " + mode + ". Must be one of: " + VALID_MODES);
        }

        File file = abstractFile.getFile();
        Path path = file.toPath();

        // Additional validation
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
        }

        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("File is not readable: " + file.getAbsolutePath());
        }

        if ("rw".equals(mode) && !Files.isWritable(path)) {
            throw new IllegalArgumentException("File is not writable but mode is 'rw': " + file.getAbsolutePath());
        }

        this.abstractFile = abstractFile;
        this.mode = mode;

        try {
            EnumSet<StandardOpenOption> options = EnumSet.of(StandardOpenOption.READ);
            if ("rw".equals(mode)) {
                options.add(StandardOpenOption.WRITE);
            }

            this.channel = FileChannel.open(path, options);
            remap();

            if (logger.isDebugEnabled()) {
                logger.debug("File opened: {} (size: {}, mode: {})",
                        file.getAbsolutePath(), formatSize(fileSize), mode);
            }
        } catch (IOException e) {
            try {
                throw new IOException("Cannot open file: " + file.getAbsolutePath(), e);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Remaps the file buffer if its size changes or on initialization.
     * Thread-safe and handles potential mapping failures.
     *
     * @throws IOException if mapping fails
     */
    private synchronized void remap() throws IOException {
        bufferLock.writeLock().lock();
        try {
            this.fileSize = channel.size();

            if (fileSize > Integer.MAX_VALUE) {
                throw new IOException("File too large to map completely: " + formatSize(fileSize));
            }

            try {
                FileChannel.MapMode mapMode = "rw".equals(mode)
                        ? FileChannel.MapMode.READ_WRITE
                        : FileChannel.MapMode.READ_ONLY;

                this.mappedBuffer = channel.map(mapMode, 0, fileSize);
                this.mappedBuffer.order(byteOrder);

                logger.debug("Mapped file '{}' [{}] in mode '{}'",
                        abstractFile.getFile().getName(), formatSize(fileSize), mode);
            } catch (OutOfMemoryError e) {
                throw new IOException("Not enough memory to map file: " + formatSize(fileSize), e);
            }
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Formats file size in human-readable form.
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Changes byte order for all subsequent operations.
     *
     * @param order the new byte order
     * @throws IllegalStateException if the reader is closed
     */
    public void setByteOrder(ByteOrder order) {
        Objects.requireNonNull(order, "ByteOrder cannot be null");
        checkClosed();

        bufferLock.writeLock().lock();
        try {
            this.byteOrder = order;
            this.mappedBuffer.order(order);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Gets the current byte order.
     *
     * @return current byte order
     */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    /**
     * Reads a single byte from the current position.
     *
     * @return the byte value
     * @throws IllegalStateException if the reader is closed
     */
    public byte readByte() {
        checkClosed();
        bufferLock.readLock().lock();
        try {
            return mappedBuffer.get();
        } finally {
            bufferLock.readLock().unlock();
        }
    }

    /**
     * Reads a short integer (2 bytes) from the current position.
     *
     * @return the short value
     * @throws IllegalStateException if the reader is closed
     */
    public short readShort() {
        checkClosed();
        bufferLock.readLock().lock();
        try {
            return mappedBuffer.getShort();
        } finally {
            bufferLock.readLock().unlock();
        }
    }

    /**
     * Reads an integer (4 bytes) from the current position.
     *
     * @return the integer value
     * @throws IllegalStateException if the reader is closed
     */
    public int readInt() {
        checkClosed();
        bufferLock.readLock().lock();
        try {
            return mappedBuffer.getInt();
        } finally {
            bufferLock.readLock().unlock();
        }
    }

    /**
     * Reads a long integer (8 bytes) from the current position.
     *
     * @return the long value
     * @throws IllegalStateException if the reader is closed
     */
    public long readLong() {
        checkClosed();
        bufferLock.readLock().lock();
        try {
            return mappedBuffer.getLong();
        } finally {
            bufferLock.readLock().unlock();
        }
    }

    /**
     * Reads a float (4 bytes) from the current position.
     *
     * @return the float value
     * @throws IllegalStateException if the reader is closed
     */
    public float readFloat() {
        checkClosed();
        bufferLock.readLock().lock();
        try {
            return mappedBuffer.getFloat();
        } finally {
            bufferLock.readLock().unlock();
        }
    }

    /**
     * Reads a double (8 bytes) from the current position.
     *
     * @return the double value
     * @throws IllegalStateException if the reader is closed
     */
    public double readDouble() {
        checkClosed();
        bufferLock.readLock().lock();
        try {
            return mappedBuffer.getDouble();
        } finally {
            bufferLock.readLock().unlock();
        }
    }

    /**
     * Reads a specified number of bytes from the current position.
     *
     * @param length number of bytes to read
     * @return byte array containing the read bytes
     * @throws IllegalArgumentException if length is invalid
     * @throws IllegalStateException if the reader is closed
     */
    public byte[] readBytes(int length) {
        checkClosed();

        if (length < 0) {
            throw new IllegalArgumentException("Read length cannot be negative: " + length);
        }

        bufferLock.readLock().lock();
        try {
            if (length > mappedBuffer.remaining()) {
                throw new IllegalArgumentException(
                        "Requested length " + length + " exceeds remaining bytes " + mappedBuffer.remaining());
            }

            byte[] bytes = new byte[length];
            mappedBuffer.get(bytes);
            return bytes;
        } finally {
            bufferLock.readLock().unlock();
        }
    }

    /**
     * Reads a string of specified length from the current position using the specified charset.
     *
     * @param length number of bytes to read
     * @param charset character set to use for decoding
     * @return decoded string
     * @throws IllegalArgumentException if length is invalid
     * @throws IllegalStateException if the reader is closed
     */
    public String readString(int length, Charset charset) {
        checkClosed();
        Objects.requireNonNull(charset, "Charset cannot be null");

        byte[] bytes = readBytes(length);
        return new String(bytes, charset);
    }

    /**
     * Reads a UTF-8 string of specified length from the current position.
     *
     * @param length number of bytes to read
     * @return UTF-8 decoded string
     * @throws IllegalArgumentException if length is invalid
     * @throws IllegalStateException if the reader is closed
     */
    public String readString(int length) {
        return readString(length, StandardCharsets.UTF_8);
    }

    /**
     * Sets the buffer position to the specified offset.
     *
     * @param position new buffer position
     * @throws IllegalArgumentException if position is invalid
     * @throws IllegalStateException if the reader is closed
     */
    public void seek(int position) {
        checkClosed();

        bufferLock.writeLock().lock();
        try {
            if (position < 0 || position > mappedBuffer.limit()) {
                throw new IllegalArgumentException(
                        "Seek position " + position + " out of bounds (0-" + mappedBuffer.limit() + ")");
            }
            mappedBuffer.position(position);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Gets the current buffer position.
     *
     * @return current position
     * @throws IllegalStateException if the reader is closed
     */
    public int position() {
        checkClosed();

        bufferLock.readLock().lock();
        try {
            return mappedBuffer.position();
        } finally {
            bufferLock.readLock().unlock();
        }
    }

    /**
     * Gets the file size.
     *
     * @return file size in bytes
     */
    public long size() {
        return fileSize;
    }

    /**
     * Returns a zero-copy slice of the mapped buffer.
     * The slice is thread-safe and independent from the original buffer's position.
     *
     * @param offset start offset
     * @param length length of the slice
     * @return ByteBuffer slice with its own position and limit
     * @throws IllegalArgumentException if offset or length is invalid
     * @throws IllegalStateException if the reader is closed
     */
    public ByteBuffer sliceView(int offset, int length) {
        checkClosed();

        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException(
                    "Invalid parameters: offset=" + offset + " length=" + length);
        }

        if ((long)offset + (long)length > fileSize) {
            throw new IllegalArgumentException(
                    "Slice extends beyond file bounds: offset=" + offset +
                            " length=" + length + " fileSize=" + fileSize);
        }

        bufferLock.readLock().lock();
        try {
            // Create duplicate to avoid position interference
            ByteBuffer duplicate = mappedBuffer.duplicate();
            duplicate.order(byteOrder);
            duplicate.position(offset);
            duplicate.limit(offset + length);

            // Create slice from the duplicate
            ByteBuffer slice = duplicate.slice();
            slice.order(byteOrder);
            return slice;
        } finally {
            bufferLock.readLock().unlock();
        }
    }

    /**
     * Executes a custom operation on the buffer with proper error handling.
     * The operation will use a duplicate of the buffer to avoid position interference.
     *
     * @param <T> return type
     * @param operation function to apply to the buffer
     * @return result of the operation
     * @throws IOException if operation fails
     * @throws IllegalStateException if the reader is closed
     */
    public <T> T readSafely(Function<ByteBuffer, T> operation) throws IOException {
        checkClosed();
        Objects.requireNonNull(operation, "Operation cannot be null");

        bufferLock.readLock().lock();
        try {
            // Create duplicate to avoid position interference
            ByteBuffer duplicate = mappedBuffer.duplicate();
            duplicate.order(byteOrder);
            duplicate.position(mappedBuffer.position());

            try {
                return operation.apply(duplicate);
            } catch (Exception e) {
                throw new IOException("Error during buffer operation: " + e.getMessage(), e);
            }
        } finally {
            bufferLock.readLock().unlock();
        }
    }

    /**
     * Gets direct access to the mapped buffer. Use with caution in multi-threaded contexts.
     * Consider using {@link #sliceView(int, int)} or {@link #readSafely(Function)} for thread safety.
     *
     * @return the mapped byte buffer
     * @throws IllegalStateException if the reader is closed
     */
    public MappedByteBuffer getMappedBuffer() {
        checkClosed();
        return mappedBuffer;
    }

    /**
     * Gets the underlying file channel for advanced operations.
     *
     * @return the file channel
     * @throws IllegalStateException if the reader is closed
     */
    public FileChannel getChannel() {
        checkClosed();
        return channel;
    }

    /**
     * Checks if the file size has changed and remaps if necessary.
     * This is useful when the file is modified externally.
     *
     * @return true if remapping occurred
     * @throws IOException if remapping fails
     * @throws IllegalStateException if the reader is closed
     */
    public synchronized boolean refreshMapIfNeeded() throws IOException {
        checkClosed();

        try {
            long newSize = channel.size();
            if (newSize != fileSize) {
                logger.debug("File size changed from {} to {}, remapping...",
                        formatSize(fileSize), formatSize(newSize));

                // Only automatically remap if size isn't too large
                if (newSize > MAX_AUTO_REMAP_SIZE) {
                    logger.warn("File size exceeds auto-remap limit ({}). " +
                            "Manual handling required.", formatSize(MAX_AUTO_REMAP_SIZE));
                    return false;
                }

                remap();
                return true;
            }
            return false;
        } catch (IOException e) {
            throw new IOException("Failed to refresh file mapping: " + e.getMessage(), e);
        }
    }

    /**
     * Writes a byte to the current position.
     * Only valid in "rw" mode.
     *
     * @param value the byte to write
     * @throws IllegalStateException if the reader is closed or not in "rw" mode
     */
    public void writeByte(byte value) {
        checkWritable();

        bufferLock.writeLock().lock();
        try {
            mappedBuffer.put(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Writes a short integer (2 bytes) to the current position.
     * Only valid in "rw" mode.
     *
     * @param value the short to write
     * @throws IllegalStateException if the reader is closed or not in "rw" mode
     */
    public void writeShort(short value) {
        checkWritable();

        bufferLock.writeLock().lock();
        try {
            mappedBuffer.putShort(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Writes an integer (4 bytes) to the current position.
     * Only valid in "rw" mode.
     *
     * @param value the integer to write
     * @throws IllegalStateException if the reader is closed or not in "rw" mode
     */
    public void writeInt(int value) {
        checkWritable();

        bufferLock.writeLock().lock();
        try {
            mappedBuffer.putInt(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Writes a long integer (8 bytes) to the current position.
     * Only valid in "rw" mode.
     *
     * @param value the long to write
     * @throws IllegalStateException if the reader is closed or not in "rw" mode
     */
    public void writeLong(long value) {
        checkWritable();

        bufferLock.writeLock().lock();
        try {
            mappedBuffer.putLong(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Writes a float (4 bytes) to the current position.
     * Only valid in "rw" mode.
     *
     * @param value the float to write
     * @throws IllegalStateException if the reader is closed or not in "rw" mode
     */
    public void writeFloat(float value) {
        checkWritable();

        bufferLock.writeLock().lock();
        try {
            mappedBuffer.putFloat(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Writes a double (8 bytes) to the current position.
     * Only valid in "rw" mode.
     *
     * @param value the double to write
     * @throws IllegalStateException if the reader is closed or not in "rw" mode
     */
    public void writeDouble(double value) {
        checkWritable();

        bufferLock.writeLock().lock();
        try {
            mappedBuffer.putDouble(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Writes bytes to the current position.
     * Only valid in "rw" mode.
     *
     * @param bytes the byte array to write
     * @throws IllegalStateException if the reader is closed or not in "rw" mode
     * @throws IllegalArgumentException if bytes is null
     */
    public void writeBytes(byte[] bytes) {
        checkWritable();
        Objects.requireNonNull(bytes, "Byte array cannot be null");

        bufferLock.writeLock().lock();
        try {
            if (bytes.length > mappedBuffer.remaining()) {
                throw new IllegalArgumentException(
                        "Byte array length exceeds remaining buffer space: " +
                                bytes.length + " > " + mappedBuffer.remaining());
            }
            mappedBuffer.put(bytes);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Writes a string to the current position using the specified charset.
     * Only valid in "rw" mode.
     *
     * @param text the string to write
     * @param charset the charset to use for encoding
     * @throws IllegalStateException if the reader is closed or not in "rw" mode
     * @throws IllegalArgumentException if text or charset is null
     */
    public void writeString(String text, Charset charset) {
        checkWritable();
        Objects.requireNonNull(text, "Text cannot be null");
        Objects.requireNonNull(charset, "Charset cannot be null");

        byte[] bytes = text.getBytes(charset);
        writeBytes(bytes);
    }

    /**
     * Writes a UTF-8 encoded string to the current position.
     * Only valid in "rw" mode.
     *
     * @param text the string to write
     * @throws IllegalStateException if the reader is closed or not in "rw" mode
     * @throws IllegalArgumentException if text is null
     */
    public void writeString(String text) {
        writeString(text, StandardCharsets.UTF_8);
    }

    /**
     * Forces any changes to the mapped buffer to be written to disk.
     * Only valid in "rw" mode.
     *
     * @throws IllegalStateException if the reader is closed or not in "rw" mode
     */
    public void force() {
        checkWritable();

        bufferLock.writeLock().lock();
        try {
            mappedBuffer.force();
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Checks if this FileReader is in writable mode ("rw").
     *
     * @return true if in writable mode
     */
    public boolean isWritable() {
        return "rw".equals(mode);
    }

    /**
     * Checks if the file is still open.
     *
     * @return true if file is open
     */
    public boolean isOpen() {
        return !closed && channel.isOpen();
    }

    /**
     * Checks if the FileReader is closed and throws an exception if it is.
     *
     * @throws IllegalStateException if the reader is closed
     */
    private void checkClosed() {
        if (closed || !channel.isOpen()) {
            throw new IllegalStateException("FileReader is closed");
        }
    }

    /**
     * Checks if the FileReader is in writable mode and throws an exception if not.
     *
     * @throws IllegalStateException if not in "rw" mode or reader is closed
     */
    private void checkWritable() {
        checkClosed();
        if (!"rw".equals(mode)) {
            throw new IllegalStateException("Cannot write in read-only mode");
        }
    }

    /**
     * Releases all resources associated with this FileReader.
     * After this call, no other methods should be called on this instance.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        bufferLock.writeLock().lock();
        try {
            logger.debug("Closing file reader for: {}", abstractFile.getFile().getName());

            if (isWritable()) {
                try {
                    mappedBuffer.force();
                } catch (Exception e) {
                    logger.warn("Failed to force buffer flush: {}", e.getMessage());
                }
            }

            cleanDirectBuffer();

            try {
                channel.close();
            } catch (IOException e) {
                logger.warn("Failed to close file channel: {}", e.getMessage());
            }

            closed = true;
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Attempts to clean the direct buffer using available methods.
     * This helps release file locks on Windows and reduce native memory usage.
     */
    private void cleanDirectBuffer() {
        if (mappedBuffer == null) {
            return;
        }

        try {
            // Try the Java 9+ method if available
            if (tryCleanJava9(mappedBuffer)) {
                return;
            }

            // Fallback to Java 8 method
            if (tryCleanJava8(mappedBuffer)) {
                return;
            }

            // If all else fails, try to help GC
            mappedBuffer = null;
            System.gc();
            System.runFinalization();
        } catch (Throwable t) {
            // Ignore any errors in cleanup - this is best-effort
            logger.debug("Buffer cleanup failed", t);
        }
    }

    /**
     * Try to clean the buffer using Java 9+ methods.
     */
    private boolean tryCleanJava9(MappedByteBuffer buffer) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field f = unsafeClass.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object unsafe = f.get(null);

            java.lang.reflect.Method m = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
            m.invoke(unsafe, buffer);
            logger.debug("Buffer unmapped via Java 9+ method");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Try to clean the buffer using Java 8 reflection method.
     */
    private boolean tryCleanJava8(MappedByteBuffer buffer) {
        try {
            java.lang.reflect.Method getCleanerMethod = buffer.getClass().getMethod("cleaner");
            getCleanerMethod.setAccessible(true);
            Object cleaner = getCleanerMethod.invoke(buffer);
            java.lang.reflect.Method cleanMethod = cleaner.getClass().getMethod("clean");
            cleanMethod.setAccessible(true);
            cleanMethod.invoke(cleaner);
            logger.debug("Buffer unmapped via Java 8 cleaner");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns a string representation of this FileReader.
     *
     * @return string with file info
     */
    @Override
    public String toString() {
        return "FileReader[file=" + abstractFile.getFile().getName() +
                ", size=" + formatSize(fileSize) +
                ", mode=" + mode +
                ", order=" + byteOrder +
                ", open=" + isOpen() + "]";
    }
}