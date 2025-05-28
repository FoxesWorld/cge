package org.foxesworld.cge.core.file.cgs;

import org.foxesworld.cge.core.file.AbstractFile;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;

/**
 * CGS-specific file handler: reads/writes CGS header and chunk table using
 * AbstractFile utilities.
 */
public class CGSFile extends AbstractFile {
    public final String    MAGIC           = "CGS0";
    public final int       VERSION         = 1;
    private final int      MAX_NAME_LENGTH = 4096;
    protected final ByteOrder BYTE_ORDER    = ByteOrder.LITTLE_ENDIAN;

    public CGSFile(File file, String mode) {
        super(file, mode);
    }

    /**
     * Reads and validates the CGS header.
     */
    public CGSHeader readHeader() throws IOException {
        seek(0);
        byte[] magicBytes = readBytes(MAGIC.length());
        String magic = new String(magicBytes, java.nio.charset.StandardCharsets.US_ASCII);
        if (!MAGIC.equals(magic)) {
            throw new IOException("Invalid CGS magic: " + magic);
        }

        int version = readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported CGS version: " + version);
        }

        String sceneName = readString(MAX_NAME_LENGTH);
        long tableOffset = readLong();
        return new CGSHeader(version, sceneName, MAGIC, tableOffset);
    }

    /**
     * Writes the CGS header; returns position to backfill the chunk table offset.
     */
    public long writeHeader(String sceneName) throws IOException {
        raf.setLength(0);
        seek(0);

        writeBytes(MAGIC.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        writeInt(VERSION);
        writeString(sceneName);

        long placeholder = raf.getFilePointer();
        writeLong(0L);
        return placeholder;
    }

    /**
     * Updates the placeholder with the actual chunk table offset.
     */
    public void updateHeaderOffset(long placeholderPos, long offset) throws IOException {
        seek(placeholderPos);
        writeLong(offset);
    }
}
