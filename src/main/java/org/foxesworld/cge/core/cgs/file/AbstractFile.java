package org.foxesworld.cge.core.cgs.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;

/**
 * Base file handler: manages opening/closing RandomAccessFile and provides
 * common utility methods for reading/writing bytes, strings and primitive types.
 */
public abstract class AbstractFile implements AutoCloseable {
    protected final RandomAccessFile raf;
    protected String MAGIC;
    protected int VERSION;
    protected int MAX_NAME_LENGTH;
    protected ByteOrder BYTE_ORDER;
    private final File file;

    protected AbstractFile(File file, String mode) {
        this.file = file;
        this.raf  = openRandomAccess(file, mode);
    }

    private RandomAccessFile openRandomAccess(File f, String mode) {
        try {
            return new RandomAccessFile(f, mode);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Cannot open file: " + f.getAbsolutePath(), e);
        }
    }

    protected void seek(long position) throws IOException {
        raf.seek(position);
    }

    protected byte[] readBytes(int length) throws IOException {
        byte[] data = new byte[length];
        raf.readFully(data);
        return data;
    }

    protected void writeBytes(byte[] data) throws IOException {
        raf.write(data);
    }

    protected int readInt() throws IOException {
        return raf.readInt();
    }

    protected void writeInt(int value) throws IOException {
        raf.writeInt(value);
    }

    protected long readLong() throws IOException {
        return raf.readLong();
    }

    protected void writeLong(long value) throws IOException {
        raf.writeLong(value);
    }

    protected String readString(int maxLength) throws IOException {
        int len = raf.readInt();
        if (len < 0 || len > maxLength) {
            throw new IOException("Invalid string length: " + len);
        }
        byte[] data = new byte[len];
        raf.readFully(data);
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    protected void writeString(String s) throws IOException {
        byte[] data = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        raf.writeInt(data.length);
        raf.write(data);
    }

    public File getFile() {
        return file;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}