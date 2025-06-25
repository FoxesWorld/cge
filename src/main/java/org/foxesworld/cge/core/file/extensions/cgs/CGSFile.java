package org.foxesworld.cge.core.file.extensions.cgs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.definition.FieldDefinition;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Handler for CGS files used in Calista Game Engine.
 * Supports reading CGS headers, metadata, and scene chunks.
 * Updated for the new CGS format (v1, little-endian, explicit chunk types).
 */
public class CGSFile extends AbstractFile<CGSMetadata> {
    private static final Logger logger = LogManager.getLogger(CGSFile.class);
    private final Map<Integer, ChunkEntry> chunkTable = new LinkedHashMap<>();

    public CGSFile(File file, String mode) {
        super(file, mode, "CGS");
        setMAGIC("CGS0");
        setVERSION(1);
        setBYTE_ORDER(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Called when a chunk entry has been parsed. Populates the chunk table.
     * Now supports explicit chunk type mapping (int type field, not enum ordinal).
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void onEntryRead(Map<String, Object> entry) {
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) entry.get("chunks");
        if (chunks != null) {
            for (Map<String, Object> chunkMap : chunks) {
                int id = ((Number) chunkMap.get("id")).intValue();
                long offset = ((Number) chunkMap.get("offset")).longValue();
                int length = ((Number) chunkMap.get("length")).intValue();
                int typeValue = ((Number) chunkMap.get("type")).intValue();
                ChunkType type = ChunkType.fromInt(typeValue);
                chunkTable.put(id, new ChunkEntry(id, offset, length, type));
            }
        } else {
            logger.warn("No chunks found in entry: {}", entry);
        }
    }

    /**
     * Reads and parses the CGS file header and chunk entries.
     * Now expects the new format: chunk type is explicit (int) and fields are little-endian.
     */
    @Override
    public void readFileNew() throws IOException {
        if (formatDefinition == null) {
            throw new IllegalStateException("Format definition not loaded");
        }

        Map<String, Object> headerMap = new LinkedHashMap<>();
        for (FieldDefinition field : formatDefinition.getHeader()) {
            Object value = readField(field, headerMap);
            headerMap.put(field.getName(), value);
            logger.debug("Header field: {} = {}", field.getName(), value);
        }

        String magic = (String) headerMap.get("magic");
        int version = ((Number) headerMap.get("version")).intValue();
        String sceneName = (String) headerMap.get("sceneName");
        long tableOffset = ((Number) headerMap.get("tableOffset")).longValue();

        // Move to chunk table offset
        getFileReader().getMappedBuffer().position(Math.toIntExact(tableOffset));
        int chunkCount = getFileReader().getMappedBuffer().getInt();

        metadata = new CGSMetadata(magic, sceneName, version, tableOffset, chunkCount);

        for (int i = 0; i < chunkCount; i++) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            for (FieldDefinition field : formatDefinition.getEntry()) {
                Object value = readField(field, entryMap);
                entryMap.put(field.getName(), value);
                logger.debug("Entry[{}] field: {} = {}", i, field.getName(), value);
            }
            onEntryRead(entryMap);
        }
    }

    /**
     * Reads a scene chunk from the file based on its chunk ID.
     * The returned ByteBuffer is little-endian and positioned at the start of the chunk.
     *
     * @param id the chunk ID
     * @return a SceneChunk instance
     * @throws IllegalArgumentException if chunk ID is invalid
     */
    public SceneChunk readChunk(int id) {
        ChunkEntry entry = chunkTable.get(id);
        if (entry == null) {
            logger.error("Chunk id not found: {}", id);
            throw new IllegalArgumentException("Chunk id not found: " + id);
        }

        getFileReader().getMappedBuffer().position((int) entry.offset());

        ByteBuffer chunkBuffer = getFileReader().getMappedBuffer().slice();
        chunkBuffer.limit(entry.length());
        chunkBuffer.order(getBYTE_ORDER());

        logger.debug("Chunk id={} prepared (offset={}, length={}, type={})", id, entry.offset(), entry.length(), entry.type());

        // Do not read to byte[]: keep ByteBuffer for zero-copy reading (for floats/ints etc)
        chunkBuffer.position(0);
        return new SceneChunk(entry, chunkBuffer);
    }

    public Collection<ChunkEntry> getChunkTable() {
        return chunkTable.values();
    }
}