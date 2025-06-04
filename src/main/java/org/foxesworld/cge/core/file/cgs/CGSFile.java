package org.foxesworld.cge.core.file.cgs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.definition.FieldDefinition;
import org.foxesworld.cge.core.file.definition.FileFormatDefinition;
import org.foxesworld.cge.core.file.definition.FileStructureLoader;
import org.foxesworld.cge.core.file.definition.JsonFileStructureLoader;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * CGS-specific file handler, updated to work with the new AbstractFile.
 */
public class CGSFile extends AbstractFile<CGSMetadata> {
    private static final Logger logger = LogManager.getLogger(CGSFile.class);

    private final Map<Integer, ChunkEntry> chunkTable = new LinkedHashMap<>();

    public CGSFile(File file, String mode) {
        super(file, mode);

        setMAGIC("CGS0");
        setVERSION(1);
        //setMAX_NAME_LENGTH(4096);
        setBYTE_ORDER(ByteOrder.LITTLE_ENDIAN);

        try {
            FileStructureLoader loader = new JsonFileStructureLoader(
                    CGSFile.class.getClassLoader().getResourceAsStream("cgs.json")
            );
            FileFormatDefinition format = loader.loadFormatDefinition("CGS");
            setFormatDefinition(format);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load CGS format", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onEntryRead(Map<String, Object> entry) {
        System.out.println(entry);

        List<Map<String, Object>> chunks = (List<Map<String, Object>>) entry.get("chunks");

        if (chunks != null) {
            for (Map<String, Object> chunkMap : chunks) {
                int id      = ((Number) chunkMap.get("id")).intValue();
                long offset = ((Number) chunkMap.get("offset")).longValue();
                int length  = ((Number) chunkMap.get("length")).intValue();
                int typeOrd = ((Number) chunkMap.get("type")).intValue();
                ChunkType type = ChunkType.values()[typeOrd];
                ChunkEntry chunk = new ChunkEntry(id, offset, length, type);
                chunkTable.put(id, chunk);
            }
        } else {
           logger.warn("No chunks found in entry: {} ", entry);
        }
    }

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

        ByteBuffer buf = ByteBuffer.wrap(data).order(this.getBYTE_ORDER());
        return new SceneChunk(entry, buf);
    }


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

        String magic       = (String) headerMap.get("magic");
        int version        = (Integer) headerMap.get("version");
        int sceneNameLen   = (Integer) headerMap.get("sceneNameLength");
        String sceneName   = (String)  headerMap.get("sceneName");
        long tableOffset   = (Long)    headerMap.get("tableOffset");
        long fileSize      = getFile().length();
        System.out.println(headerMap);



        raf.seek(tableOffset);
        int chunkCount = raf.readInt();
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

    public Collection<ChunkEntry> getChunkTable() {
        return chunkTable.values();
    }
}
