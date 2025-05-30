package org.foxesworld.cge.core.file.cgs.parser;

import org.foxesworld.cge.core.file.FileReader;
import org.foxesworld.cge.core.file.cgs.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.HexFormat;

/**
 * CGS file reader using the abstract base for consistent formatting.
 */
public class CGSFileReader extends FileReader {
    private static final Logger logger = LogManager.getLogger(CGSFileReader.class);
    private static final HexFormat HEX = HexFormat.of();
    private final Map<Integer, ChunkEntry> chunkTable = new HashMap<>();
    private final CGSMetadata metadata;
    String sceneName;
    long tableOffset;
    public CGSFileReader(CGSFile cgsFile) throws IOException {
        super(cgsFile);
        logger.debug("================ CGS FILE READ START ================");
        logger.debug("Opening file: {}", cgsFile.getFile().getAbsolutePath());

        // === DEBUG: Выводим всё содержимое файла в HEX ===
        try {
            byte[] allBytes = Files.readAllBytes(cgsFile.getFile().toPath());
            logger.debug("Full file HEX dump ({} bytes):\n{}", allBytes.length, HEX.formatHex(allBytes));
        } catch (IOException e) {
            logger.warn("Failed to dump full file hex: {}", e.getMessage());
        }

        // Delegate header parsing
        var header = readHeader();
        this.metadata = new CGSMetadata(header.getMagic(), header.getSceneName(), header.getVersion(), header.getTableOffset(), -1);
        logger.debug("Header Parsed: Magic='{}', Version={}, SceneName='{}', TableOffset={}",
                metadata.getMagic(), metadata.getVersion(), metadata.getSceneName(), metadata.getTableOffset());

        // Read chunk table
        raf.seek(header.getTableOffset());
        int chunkCount = raf.readInt();
        metadata.setChunkCount(chunkCount);
        logger.debug("Chunk Table: Count={}", metadata.getChunkCount());

        for (int i = 0; i < chunkCount; i++) {
            int id = raf.readInt();
            long offset = raf.readLong();
            int length = raf.readInt();
            ChunkType type = ChunkType.values()[raf.readInt()];
            ChunkEntry entry = new ChunkEntry(id, offset, length, type);
            chunkTable.put(id, entry);
            logger.debug("  - Entry[{}]: {}", i, entry);
        }

        logger.debug("================= CGS FILE READ END =================");
    }

    public CGSHeader readHeader() throws IOException {
        this.getThisFile().seek(0);
        byte[] magicBytes = this.getThisFile().readBytes(this.getThisFile().getMAGIC().length());
        String magic = new String(magicBytes, StandardCharsets.US_ASCII);
        if (!this.getThisFile().getMAGIC().equals(magic)) {
            throw new IOException("Invalid CGS magic: " + magic);
        }

        int version = this.getThisFile().readInt();
        if (version != this.getThisFile().getVERSION()) {
            throw new IOException("Unsupported CGS version: " + version);
        }

        sceneName = this.getThisFile().readString(this.getThisFile().getMAX_NAME_LENGTH());
        tableOffset = this.getThisFile().readLong();
        return new CGSHeader(this);
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
        logger.debug("-- Reading Chunk id={} --", id);
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
        logger.debug("Chunk id={} Read: {} bytes", id, data.length);

        ByteBuffer buf = ByteBuffer.wrap(data).order(this.getThisFile().getBYTE_ORDER());
        return new SceneChunk(entry, buf);
    }

    public String getSceneName() {
        return sceneName;
    }

    public long getTableOffset() {
        return tableOffset;
    }
}
