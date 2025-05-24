package org.foxesworld.cge.core.cgs.writer;

import org.foxesworld.cge.core.cgs.ChunkEntry;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class CGSWriter {
    public static final String MAGIC = "CGS0";
    private final List<ChunkEntry> chunkEntries = new ArrayList<>();
    private final List<byte[]> chunkData = new ArrayList<>();

    public void addChunk(int id, int type, byte[] data) {
        chunkEntries.add(new ChunkEntry(id, 0, data.length, type));
        chunkData.add(data);
    }

    public void writeToFile(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(0); // Clean file
            raf.writeBytes(MAGIC);          // 4 bytes
            raf.writeInt(1);                // version

            long tableOffsetPos = raf.getFilePointer();
            raf.writeLong(0);               // Placeholder for chunk table offset

            // Write all chunks and track their real offsets
            for (int i = 0; i < chunkEntries.size(); i++) {
                ChunkEntry entry = chunkEntries.get(i);
                byte[] data = chunkData.get(i);
                long offset = raf.getFilePointer();
                raf.write(data);
                chunkEntries.set(i, new ChunkEntry(entry.id(), offset, entry.length(), entry.type()));
            }

            // Write chunk table
            long tableOffset = raf.getFilePointer();
            raf.writeInt(chunkEntries.size());
            for (ChunkEntry entry : chunkEntries) {
                raf.writeInt(entry.id());
                raf.writeLong(entry.offset());
                raf.writeInt(entry.length());
                raf.writeInt(entry.type());
            }

            // Go back and write real table offset
            raf.seek(tableOffsetPos);
            raf.writeLong(tableOffset);
        }
    }
}
