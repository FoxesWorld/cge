package org.foxesworld.cge.core.cgs.parser;

import org.foxesworld.cge.core.cgs.ChunkEntry;

import java.io.*;
import java.util.*;

public class CGSFileParser {

    public static final String MAGIC = "CGS0";

    public static ParsedCGSFile parse(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            // Чтение заголовка
            byte[] magicBytes = new byte[4];
            raf.readFully(magicBytes);
            String magic = new String(magicBytes);
            if (!MAGIC.equals(magic)) {
                throw new IOException("Invalid CGS file: magic header mismatch");
            }

            int version = raf.readInt();
            long chunkTableOffset = raf.readLong();

            // Переход к таблице чанков
            raf.seek(chunkTableOffset);
            int chunkCount = raf.readInt();

            Map<Integer, ChunkEntry> chunkTable = new LinkedHashMap<>();
            for (int i = 0; i < chunkCount; i++) {
                int chunkId = raf.readInt();
                long offset = raf.readLong();
                int length = raf.readInt();
                int type = raf.readInt();

                ChunkEntry entry = new ChunkEntry(chunkId, offset, length, type);
                chunkTable.put(chunkId, entry);
            }

            return new ParsedCGSFile(file);
        }
    }
}
