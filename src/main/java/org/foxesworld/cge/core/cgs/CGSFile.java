package org.foxesworld.cge.core.cgs;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class CGSFile {
    public static final String MAGIC = "CGS0";
    private final RandomAccessFile raf;
    private final Map<Integer, ChunkEntry> chunkTable = new HashMap<>();
    private final File sourceFile;

    public CGSFile(File file) throws IOException {
        this.sourceFile = file;
        this.raf = new RandomAccessFile(file, "r");
        parseHeaderAndChunks();
    }

    private void parseHeaderAndChunks() throws IOException {
        raf.seek(0);
        byte[] magic = new byte[4];
        raf.readFully(magic);
        if (!MAGIC.equals(new String(magic))) {
            throw new IOException("Invalid CGS file magic: " + Arrays.toString(magic));
        }

        int version = raf.readInt(); // future use
        long chunkTableOffset = raf.readLong();
        raf.seek(chunkTableOffset);

        int chunkCount = raf.readInt();
        for (int i = 0; i < chunkCount; i++) {
            int id = raf.readInt();
            long offset = raf.readLong();
            int length = raf.readInt();
            int type = raf.readInt(); // enum: 0 = GEOMETRY, 1 = PHYSICS, etc.
            chunkTable.put(id, new ChunkEntry(id, offset, length, type));
        }
    }

    public SceneChunk loadChunk(int chunkId) throws IOException {
        ChunkEntry entry = chunkTable.get(chunkId);
        if (entry == null) throw new IllegalArgumentException("No such chunk: " + chunkId);

        byte[] data = new byte[entry.length()];
        raf.seek(entry.offset());
        raf.readFully(data);
        return new SceneChunk(entry, ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
    }

    public Collection<ChunkEntry> getAllChunks() {
        return chunkTable.values();
    }

    public void close() throws IOException {
        raf.close();
    }
}
