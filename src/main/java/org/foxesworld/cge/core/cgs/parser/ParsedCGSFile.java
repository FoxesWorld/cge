package org.foxesworld.cge.core.cgs.parser;

import org.foxesworld.cge.core.cgs.CGSFile;
import org.foxesworld.cge.core.cgs.CGSMetadata;
import org.foxesworld.cge.core.cgs.ChunkEntry;
import org.foxesworld.cge.core.cgs.SceneChunk;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public class ParsedCGSFile implements AutoCloseable {

    private final CGSFile cgsFile;
    private final CGSMetadata metadata;

    public ParsedCGSFile(File file) throws IOException {
        this.cgsFile = new CGSFile(file);

        String magic;
        int version;
        String sceneName;
        long tableOffset;
        int chunkCount;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(0);

            // Чтение MAGIC
            byte[] m = new byte[4];
            raf.readFully(m);
            magic = new String(m, StandardCharsets.US_ASCII);

            // Чтение версии
            version = raf.readInt();

            // Чтение длины и строки имени сцены
            int nameLen = raf.readInt();
            byte[] nameBytes = new byte[nameLen];
            raf.readFully(nameBytes);
            sceneName = new String(nameBytes, StandardCharsets.UTF_8);

            // Чтение смещения таблицы
            tableOffset = raf.readLong();

            // Переход к таблице и чтение количества чанков
            raf.seek(tableOffset);
            chunkCount = raf.readInt();
        }

        this.metadata = new CGSMetadata(magic, sceneName, version, tableOffset, chunkCount);
    }

    public CGSMetadata getMetadata() {
        return metadata;
    }

    public Collection<ChunkEntry> getChunkEntries() {
        return cgsFile.getAllChunks();
    }

    public SceneChunk getChunk(int id) throws IOException {
        return cgsFile.loadChunk(id);
    }

    @Override
    public void close() throws IOException {
        cgsFile.close();
    }
}
