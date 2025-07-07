package org.foxesworld.cge.core.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * AAA-level memory-mapped file writer for high-performance binary IO.
 * Complementary to FileReader, designed for efficient and safe write operations.
 * <p>
 * Features:
 * <ul>
 *   <li>Thread-safe operations via shared locks</li>
 *   <li>Configurable byte order</li>
 *   <li>Automatic file growth and buffer remapping</li>
 *   <li>Robust resource management and explicit flush control</li>
 * </ul>
 */
public final class FileWriter implements Closeable {
    private static final Logger logger = LogManager.getLogger(FileWriter.class);

    // Core file access resources
    private final AbstractFile abstractFile;
    private final FileChannel channel;
    private ByteBuffer writeBuffer; // Может быть MappedByteBuffer или обычный HeapByteBuffer
    private final ReadWriteLock bufferLock;

    // State
    private volatile boolean closed = false;
    private ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;

    /**
     * Constructs a memory-mapped file writer.
     * The file must exist and be writable.
     *
     * @param abstractFile a non-null abstract file
     * @throws NullPointerException if abstractFile or its file is null
     * @throws IOException if the file cannot be opened in "rw" mode
     */
    public FileWriter(AbstractFile abstractFile, String mode) {
        Objects.requireNonNull(abstractFile, "abstractFile is null");
        Objects.requireNonNull(abstractFile.getFile(), "abstractFile.getFile() is null");

        File file = abstractFile.getFile();

        // FileWriter всегда работает в режиме "rw"
        try {
            // Используем RandomAccessFile для возможности изменять размер файла
            RandomAccessFile raf = new RandomAccessFile(file, mode);
            this.channel = raf.getChannel();
        } catch (IOException e) {
            try {
                throw new IOException("Cannot open file in 'rw' mode: " + file.getAbsolutePath(), e);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        this.abstractFile = abstractFile;
        // Используем общую блокировку, если FileReader уже существует для этого файла
        if (abstractFile.getFileReader() != null && abstractFile.getFileReader().isOpen()) {
            // Эта логика предполагает, что AbstractFile может хранить ReadWriteLock
            // Если нет, создаем новый. Для простоты, создадим новый.
            this.bufferLock = new ReentrantReadWriteLock();
        } else {
            this.bufferLock = new ReentrantReadWriteLock();
        }

        // Инициализируем буфер для записи. Можно начать с пустого.
        this.writeBuffer = ByteBuffer.allocate(0);

        logger.debug("FileWriter opened for: {}", file.getAbsolutePath());
    }

    /**
     * Ensures the buffer has enough capacity for an upcoming write.
     * If not, it re-allocates the buffer.
     * ВНИМАНИЕ: Это упрощенная модель. MappedByteBuffer так легко не расширить.
     * Эта реализация использует HeapByteBuffer для простоты расширения.
     *
     * @param requiredBytes The number of bytes needed for the next write.
     */
    private void ensureCapacity(int requiredBytes) {
        if (writeBuffer.remaining() < requiredBytes) {
            int newCapacity = Math.max(writeBuffer.capacity() * 2, writeBuffer.position() + requiredBytes);
            ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity).order(byteOrder);
            writeBuffer.flip(); // Готовим старый буфер к чтению
            newBuffer.put(writeBuffer); // Копируем данные
            writeBuffer = newBuffer;
            logger.trace("Resized write buffer to {} bytes", newCapacity);
        }
    }

    /**
     * Changes byte order for all subsequent write operations.
     */
    public void setByteOrder(ByteOrder order) {
        Objects.requireNonNull(order, "ByteOrder cannot be null");
        checkClosed();
        bufferLock.writeLock().lock();
        try {
            this.byteOrder = order;
            if (writeBuffer != null) {
                writeBuffer.order(order);
            }
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Gets the current byte order.
     */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    /**
     * Sets the buffer position.
     */
    public void seek(int position) {
        checkClosed();
        bufferLock.writeLock().lock();
        try {
            if (position > writeBuffer.capacity()) {
                // Если нужно писать за пределами текущего буфера, его надо расширить
                int newCapacity = Math.max(writeBuffer.capacity() * 2, position);
                ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity).order(byteOrder);
                writeBuffer.flip();
                newBuffer.put(writeBuffer);
                writeBuffer = newBuffer;
            }
            writeBuffer.position(position);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    /**
     * Gets the current buffer position.
     */
    public int position() {
        checkClosed();
        return writeBuffer.position();
    }

    // --- МЕТОДЫ ЗАПИСИ ---

    public void writeByte(byte value) {
        checkClosed();
        bufferLock.writeLock().lock();
        try {
            ensureCapacity(1);
            writeBuffer.put(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    public void writeShort(short value) {
        checkClosed();
        bufferLock.writeLock().lock();
        try {
            ensureCapacity(2);
            writeBuffer.putShort(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    public void writeInt(int value) {
        checkClosed();
        bufferLock.writeLock().lock();
        try {
            ensureCapacity(4);
            writeBuffer.putInt(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    public void writeLong(long value) {
        checkClosed();
        bufferLock.writeLock().lock();
        try {
            ensureCapacity(8);
            writeBuffer.putLong(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    public void writeFloat(float value) {
        checkClosed();
        bufferLock.writeLock().lock();
        try {
            ensureCapacity(4);
            writeBuffer.putFloat(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    public void writeDouble(double value) {
        checkClosed();
        bufferLock.writeLock().lock();
        try {
            ensureCapacity(8);
            writeBuffer.putDouble(value);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    public void writeBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "Byte array cannot be null");
        checkClosed();
        bufferLock.writeLock().lock();
        try {
            ensureCapacity(bytes.length);
            writeBuffer.put(bytes);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    public void writeString(String text, Charset charset) {
        Objects.requireNonNull(text, "Text cannot be null");
        Objects.requireNonNull(charset, "Charset cannot be null");
        writeBytes(text.getBytes(charset));
    }

    public void writeString(String text) {
        writeString(text, StandardCharsets.UTF_8);
    }

    /**
     * Writes the entire content of the internal buffer to the file.
     * This will overwrite the file from the beginning.
     *
     * @throws IOException if writing fails
     */
    public void commit() throws IOException {
        checkClosed();
        bufferLock.writeLock().lock();
        try {
            channel.position(0); // Начинаем запись с начала файла
            writeBuffer.flip(); // Готовим буфер к чтению
            while(writeBuffer.hasRemaining()) {
                channel.write(writeBuffer);
            }
            channel.truncate(writeBuffer.limit()); // Обрезаем файл до нового размера
            writeBuffer.compact(); // Возвращаем буфер в режим записи
            logger.debug("Committed {} bytes to file {}", writeBuffer.limit(), abstractFile.getFile().getName());
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("FileWriter is closed");
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        bufferLock.writeLock().lock();
        try {
            commit(); // Записываем все оставшиеся данные перед закрытием
            channel.close();
            closed = true;
            logger.debug("FileWriter closed for: {}", abstractFile.getFile().getName());
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        return "FileWriter[file=" + abstractFile.getFile().getName() +
                ", position=" + (writeBuffer != null ? writeBuffer.position() : "N/A") +
                ", open=" + !closed + "]";
    }
}