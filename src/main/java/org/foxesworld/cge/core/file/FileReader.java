package org.foxesworld.cge.core.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;

public class FileReader implements Closeable {
    private static final Logger logger = LogManager.getLogger(FileReader.class);

    private final AbstractFile abstractFile;
    private final FileChannel channel;
    private MappedByteBuffer mappedBuffer;
    private long fileSize;
    private ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    private final String mode;

    public FileReader(AbstractFile abstractFile, String mode) {
        if (abstractFile == null || abstractFile.getFile() == null)
            throw new IllegalArgumentException("abstractFile or its file is null");
        if (!"r".equals(mode) && !"rw".equals(mode))
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

    private void remap() throws IOException {
        this.fileSize = channel.size();
        this.mappedBuffer = channel.map(
                "rw".equals(mode) ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY,
                0,
                fileSize
        );
        this.mappedBuffer.order(byteOrder);
    }

    public void setByteOrder(ByteOrder order) {
        this.byteOrder = order;
        this.mappedBuffer.order(order);
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public byte readByte() {
        return mappedBuffer.get();
    }

    public short readShort() {
        return mappedBuffer.getShort();
    }

    public int readInt() {
        return mappedBuffer.getInt();
    }

    public long readLong() {
        return mappedBuffer.getLong();
    }

    public float readFloat() {
        return mappedBuffer.getFloat();
    }

    public double readDouble() {
        return mappedBuffer.getDouble();
    }

    public byte[] readBytes(int length) {
        byte[] bytes = new byte[length];
        mappedBuffer.get(bytes);
        return bytes;
    }

    public String readString(int length, Charset charset) {
        byte[] bytes = readBytes(length);
        return new String(bytes, charset);
    }

    public String readString(int length) {
        return readString(length, StandardCharsets.UTF_8);
    }

    public void seek(int position) {
        mappedBuffer.position(position);
    }

    public int position() {
        return mappedBuffer.position();
    }

    public long size() {
        return fileSize;
    }

    /**
     * Возвращает ByteBuffer-срез без копирования — для быстрой работы с чанками.
     */
    public ByteBuffer sliceView(int offset, int length) {
        int oldPos = mappedBuffer.position();
        mappedBuffer.position(offset);
        ByteBuffer slice = mappedBuffer.slice();
        slice.limit(length);
        slice.order(byteOrder);
        mappedBuffer.position(oldPos);
        return slice;
    }

    public <T> T readSafely(Function<MappedByteBuffer, T> reader) {
        try {
            return reader.apply(mappedBuffer);
        } catch (Exception e) {
            logger.error("Error reading buffer: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public MappedByteBuffer getMappedBuffer() {
        return mappedBuffer;
    }

    /**
     * Если размер файла увеличился — перемапить mmap.
     */
    public void refreshMapIfNeeded() throws IOException {
        long newSize = channel.size();
        if (newSize != fileSize) {
            remap();
        }
    }

    @Override
    public void close() {
        try {
            logger.debug("Closing channel for file: {}", abstractFile.getFile().getName());
            channel.close();
        } catch (Exception e) {
            logger.warn("Failed to close file channel: {}", e.getMessage());
        }
    }
}