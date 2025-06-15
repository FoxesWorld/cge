package org.foxesworld.cge.core.file.extensions.cgs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.definition.FieldDefinition;
import org.foxesworld.cge.core.file.definition.FileStructureLoader;
import org.foxesworld.cge.core.file.definition.JsonFileStructureLoader;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Handler for CGS files used in Calista Game Engine.
 * Supports reading CGS headers, metadata, and scene chunks.
 */
public class CGSFile extends AbstractFile<CGSMetadata> {
    private static final Logger logger = LogManager.getLogger(CGSFile.class);
    private final Map<Integer, ChunkEntry> chunkTable = new LinkedHashMap<>();

    /**
     * Constructs a new CGSFile handler.
     *
     * @param file the CGS file to open
     * @param mode the mode in which to open the file (e.g., "r" or "rw")
     */
    public CGSFile(File file, String mode) {
        super(file, mode, "CGS");
        setMAGIC("CGS0");
        setVERSION(1);
        setBYTE_ORDER(ByteOrder.LITTLE_ENDIAN);
    }



    /**
     * Called when a chunk entry has been parsed. Populates the chunk table.
     *
     * @param entry parsed entry map
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
                int typeOrdinal = ((Number) chunkMap.get("type")).intValue();
                ChunkType type = ChunkType.values()[typeOrdinal];
                chunkTable.put(id, new ChunkEntry(id, offset, length, type));
            }
        } else {
            logger.warn("No chunks found in entry: {}", entry);
        }
    }

    /**
     * Reads and parses the CGS file header and chunk entries.
     *
     * @throws IOException if reading fails
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
        int version = (Integer) headerMap.get("version");
        String sceneName = (String) headerMap.get("sceneName");
        long tableOffset = (Long) headerMap.get("tableOffset");

        // MMAP: перемещаемся к нужной позиции через buffer.position
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
     *
     * @param id the chunk ID
     * @return a SceneChunk instance
     * @throws IOException if reading fails or chunk ID is invalid
     */
    public SceneChunk readChunk(int id) {
        ChunkEntry entry = chunkTable.get(id);
        if (entry == null) {
            logger.error("Chunk id not found: {}", id);
            throw new IllegalArgumentException("Chunk id not found: " + id);
        }

        // Перемещаемся к нужной позиции
        getFileReader().getMappedBuffer().position((int) entry.offset());

        // Создаём slice для указанного куска, чтобы избежать копирования данных
        ByteBuffer chunkBuffer = getFileReader().getMappedBuffer().slice();
        chunkBuffer.limit(entry.length());
        chunkBuffer.order(getBYTE_ORDER());

        byte[] data = new byte[entry.length()];
        chunkBuffer.get(data);

        logger.debug("Chunk {} raw data (hex): {}", id, HEX.formatHex(data));
        logger.debug("Chunk id={} Read: {} bytes", id, data.length);

        // Если SceneChunk требует ByteBuffer, можно передать slice повторно:
        chunkBuffer.position(0); // Сбросим позицию для чтения в SceneChunk, если нужно
        return new SceneChunk(entry, chunkBuffer);
    }

    /**
     * Returns all chunk entries parsed from the CGS file.
     *
     * @return collection of chunk entries
     */
    public Collection<ChunkEntry> getChunkTable() {
        return chunkTable.values();
    }
}
