package org.foxesworld.cge.core.cgs.parser;

import org.foxesworld.cge.core.cgs.file.CGSFile;
import org.foxesworld.cge.core.cgs.ChunkEntry;
import org.foxesworld.cge.core.cgs.SceneChunk;
import org.foxesworld.cge.core.cgs.file.CGSMetadata;
import org.foxesworld.cge.core.cgs.ChunkType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.HexFormat;

/**
 * CGS file reader using the abstract base for consistent formatting.
 */
public class CGSFileReader extends CGSFile {
    private static final Logger logger = LogManager.getLogger(CGSFileReader.class);
    private static final HexFormat HEX = HexFormat.of();
    private final Map<Integer, ChunkEntry> chunkTable = new HashMap<>();
    private final CGSMetadata metadata;

    public CGSFileReader(File file) throws IOException {
        super(file, "r");
        logger.info("================ CGS FILE READ START ================");
        logger.info("Opening file: {}", file.getAbsolutePath());

        // Delegate header parsing
        var header = readHeader();
        this.metadata = new CGSMetadata(header.getMagic(), header.getSceneName(), header.getVersion(), header.getTableOffset(), -1);
        logger.info("Header Parsed: Magic='{}', Version={}, SceneName='{}', TableOffset={}",
                metadata.getMagic(), metadata.getVersion(), metadata.getSceneName(), metadata.getTableOffset());

        // Read chunk table
        raf.seek(header.getTableOffset());
        int chunkCount = raf.readInt();
        metadata.setChunkCount(chunkCount);
        logger.info("Chunk Table: Count={}", metadata.getChunkCount());

        for (int i = 0; i < chunkCount; i++) {
            int id = raf.readInt();
            long offset = raf.readLong();
            int length = raf.readInt();
            ChunkType type = ChunkType.values()[raf.readInt()];
            ChunkEntry entry = new ChunkEntry(id, offset, length, type);
            chunkTable.put(id, entry);
            logger.debug(" Entry[{}]: {}", i, entry);
        }
        logger.info("================= CGS FILE READ END =================");
    }

    public CGSMetadata getMetadata() {
        return metadata;
    }

    public Collection<ChunkEntry> getChunkEntries() {
        return Collections.unmodifiableCollection(chunkTable.values());
    }

    /**
     * Reads raw data for chunk, logs hex, wraps in LITTLE_ENDIAN ByteBuffer.
     */
    public SceneChunk readChunk(int id) throws IOException {
        logger.info("-- Reading Chunk id={} --", id);
        ChunkEntry entry = chunkTable.get(id);
        if (entry == null) {
            logger.error("Chunk id not found: {}", id);
            throw new IllegalArgumentException("Chunk id not found: " + id);
        }
        raf.seek(entry.offset());
        byte[] data = new byte[entry.length()];
        raf.readFully(data);

        // Log raw hex data
        logger.debug("Chunk {} raw data (hex): {}", id, HEX.formatHex(data));
        logger.info("Chunk id={} Read: {} bytes", id, data.length);

        // Ensure consistent LITTLE_ENDIAN ordering
        ByteBuffer buf = ByteBuffer.wrap(data).order(BYTE_ORDER);
        return new SceneChunk(entry, buf);
    }

    @Override
    public void close() throws IOException {
        logger.info("Closing CGS file: {}", getFile().getAbsolutePath());
        super.close();
    }
}
