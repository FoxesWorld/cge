package org.foxesworld.cge.core.cgs.parser;

import org.foxesworld.cge.core.cgs.file.AbstractCGSFile;
import org.foxesworld.cge.core.cgs.ChunkEntry;
import org.foxesworld.cge.core.cgs.SceneChunk;
import org.foxesworld.cge.core.cgs.file.CGSMetadata;
import org.foxesworld.cge.core.cgs.ChunkType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.HexFormat;

/**
 * CGS file reader using the abstract base for consistent formatting.
 */
public class CGSFileReader extends AbstractCGSFile {
    private static final Logger logger = LogManager.getLogger(CGSFileReader.class);
    private static final HexFormat HEX = HexFormat.of();
    private final Map<Integer, ChunkEntry> chunkTable = new HashMap<>();
    private final CGSMetadata metadata;

    public CGSFileReader(File file) throws IOException {
        super(file, "r");
        logger.info("================ CGS FILE READ START ================");
        logger.info("Opening file: {}", file.getAbsolutePath());

        // --- Parse header manually to capture magic, version, sceneName, tableOffset
        raf.seek(0);
        byte[] magicBytes = new byte[4];
        raf.readFully(magicBytes);
        String magic = new String(magicBytes, StandardCharsets.US_ASCII);
        int version = raf.readInt();
        int nameLen = raf.readInt();
        byte[] nameBytes = new byte[nameLen];
        raf.readFully(nameBytes);
        String sceneName = new String(nameBytes, StandardCharsets.UTF_8);
        long tableOffset = raf.readLong();

        metadata = new CGSMetadata(magic, sceneName, version, tableOffset, -1);
        logger.info("Header Parsed:");
        logger.info("  Magic        : '{}'", metadata.getMagic());
        logger.info("  Version      : {}", metadata.getVersion());
        logger.info("  Scene Name   : '{}'", metadata.getSceneName());
        logger.info("  Table Offset : {}", metadata.getTableOffset());

        // Read chunk count
        raf.seek(tableOffset);
        int chunkCount = raf.readInt();
        metadata.setChunkCount(chunkCount);
        logger.info("Chunk Table:");
        logger.info("Chunk Count  : {}", metadata.getChunkCount());

        // Read chunk entries
        for (int i = 0; i < chunkCount; i++) {
            int id = raf.readInt();
            long offset = raf.readLong();
            int length = raf.readInt();
            int typeOrd = raf.readInt();
            ChunkType type = ChunkType.values()[typeOrd];
            ChunkEntry entry = new ChunkEntry(id, offset, length, type);
            chunkTable.put(id, entry);

            logger.debug("  Entry[{}]: id={}, offset={}, length={}, type={} ",
                    i, id, offset, length, type);
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
        logger.debug("Seeking to offset {} (length {})", entry.offset(), entry.length());
        byte[] data = new byte[entry.length()];
        raf.seek(entry.offset());
        raf.readFully(data);

        String hex = HEX.formatHex(data);
        logger.debug("Chunk {} raw data (hex): {}", id, hex);
        logger.info("Chunk id={} Read: {} bytes", id, data.length);

        // --- Вот здесь поменяли little на big ---
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

        return new SceneChunk(entry, buf);
    }
    @Override
    public void close() throws IOException {
        logger.info("Closing CGS file: {}", getFile().getAbsolutePath());
        super.close();
    }
}
