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
    private final int      MAX_NAME_LENGTH = 4096;
    protected final ByteOrder BYTE_ORDER    = ByteOrder.LITTLE_ENDIAN;

    public CGSFile(File file, String mode) {
        super(file, mode);
        setMAGIC("CGS0");
        setVERSION(1);
    }

    /**
     * Reads and validates the CGS header.
     */
    public CGSHeader readHeader() throws IOException {
        seek(0);
        byte[] magicBytes = readBytes(getMAGIC().length());
        String magic = new String(magicBytes, java.nio.charset.StandardCharsets.US_ASCII);
        if (!getMAGIC().equals(magic)) {
            throw new IOException("Invalid CGS magic: " + magic);
        }

        int version = readInt();
        if (version != getVERSION()) {
            throw new IOException("Unsupported CGS version: " + version);
        }

        String sceneName = readString(MAX_NAME_LENGTH);
        long tableOffset = readLong();
        return new CGSHeader(version, sceneName, getMAGIC(), tableOffset);
    }

    /**
     * Writes the CGS header; returns position to backfill the chunk table offset.
     */
    public long writeHeader(String sceneName) throws IOException {
        raf.setLength(0);
        seek(0);

        writeBytes(getMAGIC().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        writeInt(getVERSION());
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
