package org.foxesworld.cge.core.cgs.writer;

import org.foxesworld.cge.core.cgs.file.CGSFile;
import org.foxesworld.cge.core.cgs.ChunkEntry;
import org.foxesworld.cge.core.cgs.ChunkType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Расширили хранение: теперь вместе с байтами и метаданными сохраняем
 * и Map<String,Object> с аргументами чанка, чтобы потом показывать их в getChunkSpec.
 */
public class CGSFileWriter extends CGSFile {
    private static final Logger logger = LogManager.getLogger(CGSFileWriter.class);

    private final File file;
    private String sceneName = "";
    private final List<ChunkEntry> chunkEntries    = new ArrayList<>();
    private final List<byte[]>      chunkData       = new ArrayList<>();
    private final List<Map<String, Object>> chunkArgs = new ArrayList<>();
    private long headerTableOffsetPos;

    public CGSFileWriter(File file) {
        super(file, "rw");
        this.file = file;
    }

    public void setSceneName(String sceneName) {
        this.sceneName = sceneName != null ? sceneName : "";
    }

    /**
     * Теперь принимает дополнительно Map<String,Object> attributes
     */
    public void addChunk(int id, ChunkType type, byte[] data, Map<String, Object> attributes) {
        if (data == null) throw new IllegalArgumentException("Chunk data cannot be null");
        logger.debug("Adding chunk id={} type={} size={}", id, type, data.length);
        chunkEntries.add(new ChunkEntry(id, 0, data.length, type));
        chunkData.add(data);
        // сохраняем копию атрибутов (или сам Map)
        chunkArgs.add(new LinkedHashMap<>(attributes));
    }

    public void removeChunk(int index) {
        if (index >= 0 && index < chunkEntries.size()) {
            logger.info("Removing chunk at index {}", index);
            chunkEntries.remove(index);
            chunkData.remove(index);
            chunkArgs.remove(index);
        } else {
            logger.warn("Attempted to remove invalid chunk index: {}", index);
        }
    }

    /**
     * Теперь форматируем spec+c аргументами из chunkArgs.get(index)
     */
    public String getChunkSpec(int index) {
        if (index >= 0 && index < chunkEntries.size()) {
            ChunkEntry e = chunkEntries.get(index);
            Map<String,Object> attrs = chunkArgs.get(index);

            StringBuilder sb = new StringBuilder();
            sb.append("Chunk ID: ").append(e.id()).append("\n");
            sb.append("Type: ").append(e.type()).append("\n");
            sb.append("Length: ").append(e.length()).append(" bytes\n");
            sb.append("Offset: ").append(e.offset()).append("\n");
            sb.append("Attributes:\n");
            for (var en : attrs.entrySet()) {
                sb.append("  ").append(en.getKey())
                        .append(": ").append(en.getValue())
                        .append("\n");
            }
            return sb.toString();
        }
        return null;
    }

    public int getChunksCount() {
        return chunkEntries.size();
    }

    public ChunkEntry getChunkEntry(int index) {
        if (index >= 0 && index < chunkEntries.size()) {
            return chunkEntries.get(index);
        }
        return null;
    }

    public void writeToFile() throws IOException {
        raf.setLength(0);
        logger.info("Writing CGS: {}", file.getAbsolutePath());

        headerTableOffsetPos = writeHeader(sceneName);

        for (int i = 0; i < chunkEntries.size(); i++) {
            ChunkEntry entry = chunkEntries.get(i);
            byte[] data = chunkData.get(i);
            long offset = raf.getFilePointer();

            logByteData(data, entry.id());

            raf.write(data);
            // обновляем offset в entry
            chunkEntries.set(i, new ChunkEntry(entry.id(), offset, data.length, entry.type()));
            logger.debug("Wrote chunk id={} at offset={}, len={}", entry.id(), offset, data.length);
        }

        // запись таблицы чанков...
        long tableOffset = raf.getFilePointer();
        raf.writeInt(chunkEntries.size());
        for (ChunkEntry entry : chunkEntries) {
            raf.writeInt(entry.id());
            raf.writeLong(entry.offset());
            raf.writeInt(entry.length());
            raf.writeInt(entry.type().ordinal());
        }
        updateHeaderOffset(headerTableOffsetPos, tableOffset);
        logger.info("Finished CGS write, tableOffset={}", tableOffset);
    }

    private void logByteData(byte[] data, int chunkId) {
        StringBuilder hex = new StringBuilder();
        for (byte b : data) hex.append(String.format("%02X ", b));
        logger.debug("Writing chunk id={} data: {}", chunkId, hex.toString().trim());
    }

    public File getFile() {
        return file;
    }
}