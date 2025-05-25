package org.foxesworld.cge.core.cgs.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Abstract base for CGS file handlers (reader/writer).
 * Ensures consistent byte formatting (endianness, charset) and provides header/chunk scaffolding.
 */
public abstract class AbstractCGSFile implements AutoCloseable {
    public static final String MAGIC = "CGS0";
    public static final int VERSION = 1;
    protected static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;

    protected final RandomAccessFile raf;
    private final File file;

    protected AbstractCGSFile(File file, String mode) {
        this.file = file;
        try {
            this.raf = new RandomAccessFile(file, mode);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads and validates the header (magic, version, sceneName, tableOffset).
     * Implementations should call super.readHeader() then process further.
     */
    protected CGSHeader readHeader() throws IOException {
        raf.seek(0);
        byte[] magicBytes = new byte[4];
        raf.readFully(magicBytes);
        String magic = new String(magicBytes, StandardCharsets.US_ASCII);
        if (!MAGIC.equals(magic)) {
            throw new IOException("Invalid CGS magic: " + magic);
        }
        int version = raf.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported CGS version: " + version);
        }
        int nameLen = raf.readInt();
        if (nameLen < 0 || nameLen > 4096) {
            throw new IOException("Invalid scene name length: " + nameLen);
        }
        byte[] nameBytes = new byte[nameLen];
        raf.readFully(nameBytes);
        String sceneName = new String(nameBytes, StandardCharsets.UTF_8);

        long tableOffset = raf.readLong();
        return new CGSHeader(version, sceneName, tableOffset);
    }

    /**
     * Writes the header (magic, version, sceneName, placeholder for tableOffset).
     * Returns the file pointer position where tableOffset should be updated later.
     */
    protected long writeHeader(String sceneName) throws IOException {
        raf.seek(0);
        raf.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
        raf.writeInt(VERSION);
        byte[] nameBytes = sceneName.getBytes(StandardCharsets.UTF_8);
        raf.writeInt(nameBytes.length);
        raf.write(nameBytes);
        // placeholder for chunk table offset
        long offsetPosition = raf.getFilePointer();
        raf.writeLong(0L);
        return offsetPosition;
    }

    /**
     * Writes the actual chunk table offset back into header placeholder.
     */
    protected void updateHeaderTableOffset(long offsetPosition, long tableOffset) throws IOException {
        raf.seek(offsetPosition);
        raf.writeLong(tableOffset);
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

    public File getFile() {
        return file;
    }
}

