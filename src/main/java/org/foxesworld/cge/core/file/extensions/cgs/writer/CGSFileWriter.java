package org.foxesworld.cge.core.file.extensions.cgs.writer;

import org.foxesworld.cge.core.file.extensions.cgs.CGSFile;
import org.foxesworld.cge.core.file.extensions.cgs.ChunkEntry;
import org.foxesworld.cge.core.file.extensions.cgs.ChunkType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Расширили хранение: теперь вместе с байтами и метаданными сохраняем
 * и Map<String,Object> с аргументами чанка, чтобы потом показывать их в getChunkSpec.
 */
public class CGSFileWriter extends CGSFile {
    private static final Logger logger = LogManager.getLogger(CGSFileWriter.class);

    private final File file;
    private String sceneName = "";
    private final List<ChunkEntry> chunkEntries = new ArrayList<>();
    private final List<byte[]> chunkData = new ArrayList<>();
    private final List<Map<String, Object>> chunkArgs = new ArrayList<>();
    private long headerTableOffsetPos;

    public CGSFileWriter(File file) {
        super(file, "rw");
        this.file = file;
    }

    public void setSceneName(String sceneName) {
        this.sceneName = sceneName != null ? sceneName : "";
    }

    public void addChunk(int id, ChunkType type, byte[] data, Map<String, Object> attributes) {
        if (data == null) throw new IllegalArgumentException("Chunk data cannot be null");
        logger.debug("Adding chunk id={} type={} size={}", id, type, data.length);
        chunkEntries.add(new ChunkEntry(id, 0, data.length, type));
        chunkData.add(data);
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

    public String getChunkSpec(int index) {
        if (index >= 0 && index < chunkEntries.size()) {
            ChunkEntry e = chunkEntries.get(index);
            Map<String, Object> attrs = chunkArgs.get(index);

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
        RandomAccessFile raf = getFileReader().getRaf();
        raf.setLength(0);
        logger.info("Writing CGS: {}", file.getAbsolutePath());

        headerTableOffsetPos = writeHeader(sceneName);

        for (int i = 0; i < chunkEntries.size(); i++) {
            ChunkEntry entry = chunkEntries.get(i);
            byte[] data = chunkData.get(i);
            long offset = raf.getFilePointer();

            logByteData(data, entry.id());

            raf.write(data);
            chunkEntries.set(i, new ChunkEntry(entry.id(), offset, data.length, entry.type()));
            logger.debug("Wrote chunk id={} at offset={}, len={}", entry.id(), offset, data.length);
        }

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

    public long writeHeader(String sceneName) throws IOException {
        RandomAccessFile raf = getFileReader().getRaf();
        raf.seek(0);

        raf.write(getMAGIC().getBytes(StandardCharsets.US_ASCII));
        raf.writeInt(getVERSION());
        writeString(raf, sceneName);

        long placeholder = raf.getFilePointer();
        raf.writeLong(0L);  // placeholder for table offset
        return placeholder;
    }

    public void updateHeaderOffset(long placeholderPos, long offset) throws IOException {
        RandomAccessFile raf = getFileReader().getRaf();
        raf.seek(placeholderPos);
        raf.writeLong(offset);
    }

    private void writeString(RandomAccessFile raf, String str) throws IOException {
        byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
        raf.writeInt(strBytes.length);
        raf.write(strBytes);
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
