package org.foxesworld.cge.core.cgs.parser;

import org.foxesworld.cge.core.cgs.CGSFile;
import org.foxesworld.cge.core.cgs.ChunkEntry;
import org.foxesworld.cge.core.cgs.SceneChunk;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ParsedCGSFile {
    private final CGSFile cgsFile;
    private final Map<Integer, SceneChunk> loadedChunks = new HashMap<>();

    public ParsedCGSFile(File file) throws IOException {
        this.cgsFile = new CGSFile(file);
    }

    public SceneChunk getChunk(int chunkId) throws IOException {
        if (loadedChunks.containsKey(chunkId)) {
            return loadedChunks.get(chunkId);
        }
        SceneChunk chunk = cgsFile.loadChunk(chunkId);
        loadedChunks.put(chunkId, chunk);
        return chunk;
    }

    public Collection<SceneChunk> loadAllChunks() throws IOException {
        List<SceneChunk> all = new ArrayList<>();
        for (ChunkEntry entry : cgsFile.getAllChunks()) {
            SceneChunk chunk = cgsFile.loadChunk(entry.id());
            loadedChunks.put(entry.id(), chunk);
            all.add(chunk);
        }
        return all;
    }

    public Collection<ChunkEntry> getChunkEntries() {
        return cgsFile.getAllChunks();
    }

    public SceneChunk getFirstChunkOfType(int type) throws IOException {
        for (ChunkEntry entry : cgsFile.getAllChunks()) {
            if (entry.type() == type) {
                return getChunk(entry.id());
            }
        }
        return null;
    }

    public void close() throws IOException {
        cgsFile.close();
    }
}
