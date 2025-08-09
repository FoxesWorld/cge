package org.foxesworld.cge.ue;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Универсальный бинарный ридер для работы с Unreal Engine и другими LE/BE форматами.
 * Основан на RandomAccessFile, поддерживает seek/skip и работу с кодировками.
 */
public final class BinaryReader implements Closeable {
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private ByteOrder order = ByteOrder.LITTLE_ENDIAN;
    private long markPos = -1;

    public BinaryReader(File file) throws FileNotFoundException {
        this.raf = new RandomAccessFile(file, "r");
        this.channel = raf.getChannel();
    }

    /** Установить порядок байт (LE или BE) */
    public void setByteOrder(ByteOrder order) {
        this.order = order;
    }

    /** Получить текущую позицию */
    public long position() throws IOException {
        return raf.getFilePointer();
    }

    /** Перейти в указанную позицию */
    public void seek(long pos) throws IOException {
        raf.seek(pos);
    }

    /** Пропустить N байт */
    public void skip(long bytes) throws IOException {
        if (bytes > 0) raf.seek(raf.getFilePointer() + bytes);
    }

    /** Сохранить текущую позицию */
    public void mark() throws IOException {
        this.markPos = raf.getFilePointer();
    }

    /** Восстановить сохранённую позицию */
    public void reset() throws IOException {
        if (markPos >= 0) {
            raf.seek(markPos);
        }
    }

    // ---------- Чтение числовых типов ----------
    public byte readByte() throws IOException {
        return raf.readByte();
    }

    public short readShort() throws IOException {
        return readBuffer(2).getShort();
    }

    public int readUShort() throws IOException {
        return Short.toUnsignedInt(readShort());
    }

    public int readInt() throws IOException {
        return readBuffer(4).getInt();
    }

    public long readUInt32() throws IOException {
        return Integer.toUnsignedLong(readInt());
    }

    public long readLong() throws IOException {
        return readBuffer(8).getLong();
    }

    public float readFloat() throws IOException {
        return readBuffer(4).getFloat();
    }

    public double readDouble() throws IOException {
        return readBuffer(8).getDouble();
    }

    /** Прочитать массив байт */
    public byte[] readBytes(int len) throws IOException {
        byte[] b = new byte[len];
        raf.readFully(b);
        return b;
    }

    // ---------- Чтение строк ----------
    /**
     * Unreal FString: int32 length; >0 UTF-8; <0 UTF-16LE; 0 = пустая строка.
     */
    public String readFString() throws IOException {
        int len = readInt();
        if (len == 0) return "";

        if (len > 0) {
            // UTF-8
            byte[] data = readBytes(len);
            int strLen = len;
            while (strLen > 0 && data[strLen - 1] == 0) strLen--; // обрезаем нули
            return new String(data, 0, strLen, StandardCharsets.UTF_8);
        } else {
            // UTF-16LE
            int chars = Math.abs(len);
            byte[] data = readBytes(chars * 2);
            return new String(data, StandardCharsets.UTF_16LE).replace("\0", "");
        }
    }

    /** Чтение null-terminated UTF-8 строки */
    public String readStringUTF8(int maxLen) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < maxLen; i++) {
            byte b = raf.readByte();
            if (b == 0) break;
            baos.write(b);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    /** Чтение строки фиксированной длины */
    public String readFixedString(int length, Charset charset) throws IOException {
        byte[] data = readBytes(length);
        return new String(data, charset);
    }

    @Override
    public void close() throws IOException {
        channel.close();
        raf.close();
    }

    // ---------- Вспомогательные ----------
    private ByteBuffer readBuffer(int size) throws IOException {
        byte[] b = new byte[size];
        raf.readFully(b);
        return ByteBuffer.wrap(b).order(order);
    }
}