package org.foxesworld.cge.core.cgs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Класс для чтения и работы с файлами формата CGS (Calista Game Scene),
 * поддерживает метаданные sceneName в заголовке.
 *
 * <p>Структура CGS-файла:
 * <ol>
 *   <li><b>Header</b>:
 *     <ul>
 *       <li>4 байта: MAGIC ("CGS0")</li>
 *       <li>4 байта: версия формата (int)</li>
 *       <li>4 байта: длина имени сцены (int)</li>
 *       <li>n байт: имя сцены (UTF-8)</li>
 *       <li>8 байт: смещение таблицы чанков (long)</li>
 *     </ul>
 *   </li>
 *   <li><b>Data Chunks</b></li>
 *   <li><b>Chunk Table</b>:</li>
 * </ol>
 */
public class CGSFile implements AutoCloseable {
    public static final String MAGIC = "CGS0";
    public static final int SUPPORTED_VERSION = 1;

    private final RandomAccessFile raf;
    private final Map<Integer, ChunkEntry> chunkTable = new HashMap<>();
    private final File sourceFile;
    private String sceneName;

    public CGSFile(File file) throws IOException {
        this.sourceFile = file;
        this.raf = new RandomAccessFile(file, "r");
        parseHeaderAndChunks();
    }

    private void parseHeaderAndChunks() throws IOException {
        raf.seek(0);
        byte[] magicBytes = new byte[4];
        raf.readFully(magicBytes);
        String magic = new String(magicBytes, StandardCharsets.US_ASCII);
        if (!MAGIC.equals(magic)) {
            throw new IOException("Invalid CGS file magic: " + magic);
        }

        int version = raf.readInt();
        if (version != SUPPORTED_VERSION) {
            throw new IOException("Unsupported CGS version: " + version);
        }

        // Чтение имени сцены
        int nameLen = raf.readInt();
        if (nameLen < 0 || nameLen > 1024) {
            throw new IOException("Invalid scene name length: " + nameLen);
        }
        byte[] nameBytes = new byte[nameLen];
        raf.readFully(nameBytes);
        this.sceneName = new String(nameBytes, StandardCharsets.UTF_8);

        long chunkTableOffset = raf.readLong();
        raf.seek(chunkTableOffset);

        int chunkCount = raf.readInt();
        for (int i = 0; i < chunkCount; i++) {
            int id = raf.readInt();
            long offset = raf.readLong();
            int length = raf.readInt();
            int rawType = raf.readInt();
            ChunkEntry entry = new ChunkEntry(id, offset, length, rawType);
            chunkTable.put(id, entry);
        }
    }

    public String getSceneName() {
        return sceneName;
    }

    public SceneChunk loadChunk(int chunkId) throws IOException {
        ChunkEntry entry = chunkTable.get(chunkId);
        if (entry == null) {
            throw new IllegalArgumentException("No such chunk: " + chunkId);
        }
        byte[] data = new byte[entry.length()];
        raf.seek(entry.offset());
        raf.readFully(data);
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        return new SceneChunk(entry, buffer);
    }

    public Collection<ChunkEntry> getAllChunks() {
        return Collections.unmodifiableCollection(chunkTable.values());
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}