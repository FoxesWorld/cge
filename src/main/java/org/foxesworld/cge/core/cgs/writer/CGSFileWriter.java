package org.foxesworld.cge.core.cgs.writer;

import org.foxesworld.cge.core.cgs.file.AbstractCGSFile;
import org.foxesworld.cge.core.cgs.ChunkEntry;
import org.foxesworld.cge.core.cgs.ChunkType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CGS file writer using the abstract base for consistent formatting.
 */
public class CGSFileWriter extends AbstractCGSFile {
    private static final Logger logger = LogManager.getLogger(CGSFileWriter.class);
    private final  File file;
    private String sceneName = "";
    private final List<ChunkEntry> chunkEntries = new ArrayList<>();
    private final List<byte[]> chunkData = new ArrayList<>();
    private long headerTableOffsetPos;

    public CGSFileWriter(File file) {
        super(file, "rw");
        this.file = file;
    }

    public void setSceneName(String sceneName) {
        this.sceneName = sceneName != null ? sceneName : "";
    }

    public void addChunk(int id, ChunkType type, byte[] data) {
        if (data == null) throw new IllegalArgumentException("Chunk data cannot be null");
        logger.debug("Adding chunk id={} type={} size={}", id, type, data.length);
        chunkEntries.add(new ChunkEntry(id, 0, data.length, type));
        chunkData.add(data);
    }

    public void writeToFile() throws IOException {
        // Truncate and prepare
        raf.setLength(0);
        logger.info("Writing CGS: {}", file.getAbsolutePath());

        // Header
        headerTableOffsetPos = writeHeader(sceneName);

        // Data Chunks
        for (int i = 0; i < chunkEntries.size(); i++) {
            ChunkEntry e = chunkEntries.get(i);
            byte[] data = chunkData.get(i);
            long offset = raf.getFilePointer();
            raf.write(data);
            chunkEntries.set(i, new ChunkEntry(e.id(), offset, data.length, e.type()));
            logger.debug("Wrote chunk id={} at offset={}, len={}", e.id(), offset, data.length);
        }

        // Chunk Table
        long tableOffset = raf.getFilePointer();
        raf.writeInt(chunkEntries.size());
        for (ChunkEntry e : chunkEntries) {
            raf.writeInt(e.id());
            raf.writeLong(e.offset());
            raf.writeInt(e.length());
            raf.writeInt(e.type().ordinal());
            logger.debug("TableEntry id={} offset={} length={} type={} (ord={})",
                    e.id(), e.offset(), e.length(), e.type(), e.type().ordinal());
        }

        // Update header
        updateHeaderTableOffset(headerTableOffsetPos, tableOffset);
        logger.info("Finished CGS write, tableOffset={}", tableOffset);
    }

    public File getFile() {
        return file;
    }
}