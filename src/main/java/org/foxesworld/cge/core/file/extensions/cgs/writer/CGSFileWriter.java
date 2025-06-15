package org.foxesworld.cge.core.file.extensions.cgs.writer;

import org.foxesworld.cge.core.file.extensions.cgs.CGSFile;
import org.foxesworld.cge.core.file.extensions.cgs.ChunkEntry;
import org.foxesworld.cge.core.file.extensions.cgs.ChunkType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class CGSFileWriter extends CGSFile {
    private static final Logger logger = LogManager.getLogger(CGSFileWriter.class);

    private final File file;
    private String sceneName = "";
    private final List<ChunkEntry> chunkEntries = new ArrayList<>();
    private final List<byte[]> chunkData = new ArrayList<>();
    private final List<Map<String, Object>> chunkArgs = new ArrayList<>();
    private long headerTableOffsetPos;

    // Для записи в mmap файл
    private FileChannel channel;
    private MappedByteBuffer mmapBuffer;
    private long mmapLimit = 64 * 1024 * 1024L; // 64MB — можно увеличить под ваши нужды

    public CGSFileWriter(File file) {
        super(file, "rw");
        this.file = file;
    }

    private void ensureBuffer(long minSize) throws IOException {
        if (channel == null || mmapBuffer == null || mmapLimit < minSize) {
            if (channel != null) channel.close();
            channel = FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
            channel.truncate(minSize); // расширяем файл
            mmapLimit = Math.max(minSize, mmapLimit);
            mmapBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, mmapLimit);
        }
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
        // Предварительно оцениваем размер (header + все чанки + таблица)
        long estimatedSize = estimateFileSize();
        ensureBuffer(estimatedSize);

        mmapBuffer.position(0);
        headerTableOffsetPos = writeHeader(sceneName);

        for (int i = 0; i < chunkEntries.size(); i++) {
            ChunkEntry entry = chunkEntries.get(i);
            byte[] data = chunkData.get(i);
            long offset = mmapBuffer.position();

            logByteData(data, entry.id());

            mmapBuffer.put(data);
            chunkEntries.set(i, new ChunkEntry(entry.id(), offset, data.length, entry.type()));
            logger.debug("Wrote chunk id={} at offset={}, len={}", entry.id(), offset, data.length);
        }

        long tableOffset = mmapBuffer.position();
        mmapBuffer.putInt(chunkEntries.size());
        for (ChunkEntry entry : chunkEntries) {
            mmapBuffer.putInt(entry.id());
            mmapBuffer.putLong(entry.offset());
            mmapBuffer.putInt(entry.length());
            mmapBuffer.putInt(entry.type().ordinal());
        }
        updateHeaderOffset(headerTableOffsetPos, tableOffset);

        channel.truncate(mmapBuffer.position()); // обрезаем файл до актуального размера
        logger.info("Finished CGS write, tableOffset={}", tableOffset);
        channel.force(true);
    }

    private long estimateFileSize() {
        long size = getMAGIC().length() + Integer.BYTES; // MAGIC + version
        size += Integer.BYTES + sceneName.getBytes(StandardCharsets.UTF_8).length; // sceneName (len + bytes)
        size += Long.BYTES; // table offset placeholder
        for (byte[] data : chunkData) size += data.length;
        size += Integer.BYTES; // chunk count
        size += (long) chunkEntries.size() * (Integer.BYTES + Long.BYTES + Integer.BYTES + Integer.BYTES); // table
        return size + 1024; // небольшой запас
    }

    public long writeHeader(String sceneName) throws IOException {
        mmapBuffer.position(0);

        mmapBuffer.put(getMAGIC().getBytes(StandardCharsets.US_ASCII));
        mmapBuffer.putInt(getVERSION());
        writeString(mmapBuffer, sceneName);

        long placeholder = mmapBuffer.position();
        mmapBuffer.putLong(0L);  // placeholder for table offset
        return placeholder;
    }

    public void updateHeaderOffset(long placeholderPos, long offset) throws IOException {
        int curPos = mmapBuffer.position();
        mmapBuffer.position((int) placeholderPos);
        mmapBuffer.putLong(offset);
        mmapBuffer.position(curPos);
    }

    private void writeString(ByteBuffer buffer, String str) {
        byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(strBytes.length);
        buffer.put(strBytes);
    }

    private void logByteData(byte[] data, int chunkId) {
        StringBuilder hex = new StringBuilder();
        for (byte b : data) hex.append(String.format("%02X ", b));
        logger.debug("Writing chunk id={} data: {}", chunkId, hex.toString().trim());
    }

    public File getFile() {
        return file;
    }

    @Override
    public void close() throws IOException {
        if (channel != null) channel.close();
    }
}