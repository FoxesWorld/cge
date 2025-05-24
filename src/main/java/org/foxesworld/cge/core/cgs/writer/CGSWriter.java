package org.foxesworld.cge.core.cgs.writer;

import org.foxesworld.cge.core.cgs.ChunkEntry;
import org.foxesworld.cge.core.cgs.ChunkType;
import org.foxesworld.cge.core.cgs.parser.types.LightingParser;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.math.ColorRGBA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CGSWriter {
    public static final String MAGIC = "CGS0";
    private static final Logger logger = LoggerFactory.getLogger(CGSWriter.class);
    public static final int VERSION = 1;

    private String sceneName = "";
    private final List<ChunkEntry> chunkEntries = new ArrayList<>();
    private final List<byte[]> chunkData = new ArrayList<>();

    public void setSceneName(String sceneName) {
        this.sceneName = sceneName != null ? sceneName : "";
    }

    public void addChunk(int id, ChunkType type, byte[] data) {
        if (data == null) {
            logger.error("Attempted to add null data chunk: id={}, type={}", id, type);
            throw new IllegalArgumentException("Chunk data cannot be null");
        }
        if (data.length <= 0) {
            logger.warn("Adding empty data chunk: id={}, type={}", id, type);
        }

        logger.debug("Adding chunk id={} type={} size={} bytes", id, type, data.length);
        if (data.length > 10_000_000) {
            logger.warn("Large chunk detected: id={} size={} bytes", id, data.length);
        }

        chunkEntries.add(new ChunkEntry(id, 0, data.length, type));
        chunkData.add(data);

        // Log first bytes of the chunk
        int previewLength = Math.min(16, data.length);
        byte[] preview = Arrays.copyOf(data, previewLength);
        logger.debug("Chunk {} preview: {}", id, Arrays.toString(preview));
    }

    public void replaceChunk(int id, ChunkType type, byte[] data) {
        for (int i = 0; i < chunkEntries.size(); i++) {
            if (chunkEntries.get(i).id() == id) {
                chunkEntries.set(i, new ChunkEntry(id, 0, data.length, type));
                chunkData.set(i, data);
                logger.debug("Replaced chunk id={} with type={}, size={}", id, type, data.length);
                return;
            }
        }
        logger.info("Chunk id={} not found for replacement, adding as new.", id);
        addChunk(id, type, data);
    }

    public void removeChunk(int id) {
        for (int i = 0; i < chunkEntries.size(); i++) {
            if (chunkEntries.get(i).id() == id) {
                chunkEntries.remove(i);
                chunkData.remove(i);
                logger.info("Removed chunk id={}", id);
                return;
            }
        }
        logger.warn("Tried to remove nonexistent chunk id={}", id);
    }

    public void addLightingChunk(List<com.jme3.light.Light> lights) {
        // Создаем пустой буфер для записи всех данных.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        try {
            // Сначала пишем количество источников света.
            dos.writeInt(lights.size());

            // Для каждого источника света записываем его тип и параметры.
            for (com.jme3.light.Light light : lights) {
                if (light instanceof PointLight) {
                    dos.writeByte(0);  // Тип светильника: Point
                    writePointLight(dos, (PointLight) light);
                } else if (light instanceof DirectionalLight) {
                    dos.writeByte(1);  // Тип светильника: Directional
                    writeDirectionalLight(dos, (DirectionalLight) light);
                } else if (light instanceof SpotLight) {
                    dos.writeByte(2);  // Тип светильника: Spot
                    writeSpotLight(dos, (SpotLight) light);
                } else if (light instanceof AmbientLight) {
                    dos.writeByte(3);  // Тип светильника: Sky (Ambient)
                    writeAmbientLight(dos, (AmbientLight) light);
                } else {
                    logger.warn("Unknown light type: {}", light.getClass().getSimpleName());
                }
            }
            // Записываем все данные в буфер
            byte[] data = baos.toByteArray();
            addChunk(0, ChunkType.LIGHTING, data);  // Добавляем как новый чанк
        } catch (IOException e) {
            logger.error("Error while writing lighting chunk", e);
        }
    }

    private void writePointLight(DataOutputStream dos, PointLight light) throws IOException {
        dos.writeFloat(light.getPosition().x);
        dos.writeFloat(light.getPosition().y);
        dos.writeFloat(light.getPosition().z);
        writeColor(dos, light.getColor());
        dos.writeFloat(light.getRadius());
    }

    private void writeDirectionalLight(DataOutputStream dos, DirectionalLight light) throws IOException {
        dos.writeFloat(light.getDirection().x);
        dos.writeFloat(light.getDirection().y);
        dos.writeFloat(light.getDirection().z);
        writeColor(dos, light.getColor());
    }

    private void writeSpotLight(DataOutputStream dos, SpotLight light) throws IOException {
        dos.writeFloat(light.getPosition().x);
        dos.writeFloat(light.getPosition().y);
        dos.writeFloat(light.getPosition().z);
        writeColor(dos, light.getColor());
        dos.writeFloat(light.getSpotRange());
    }

    private void writeAmbientLight(DataOutputStream dos, AmbientLight light) throws IOException {
        writeColor(dos, light.getColor());
    }

    private void writeColor(DataOutputStream dos, ColorRGBA color) throws IOException {
        dos.writeFloat(color.r);
        dos.writeFloat(color.g);
        dos.writeFloat(color.b);
        dos.writeFloat(color.a);
    }

    public void writeToFile(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(0);
            logger.info("Writing CGS file: {}", file.getAbsolutePath());

            // Header
            raf.writeBytes(MAGIC);
            raf.writeInt(VERSION);

            byte[] nameBytes = sceneName.getBytes(StandardCharsets.UTF_8);
            raf.writeInt(nameBytes.length);
            raf.write(nameBytes);

            long offsetPos = raf.getFilePointer();
            raf.writeLong(0L); // placeholder for chunk table offset

            logger.debug("Header written: magic={}, version={}, sceneName='{}', nameLength={}, tableOffsetPos={}",
                    MAGIC, VERSION, sceneName, nameBytes.length, offsetPos);

            // Data Chunks
            for (int i = 0; i < chunkEntries.size(); i++) {
                ChunkEntry entry = chunkEntries.get(i);
                byte[] data = chunkData.get(i);

                if (data.length != entry.length()) {
                    logger.warn("Chunk {} length mismatch: expected {}, actual {}", entry.id(), entry.length(), data.length);
                }

                long dataOffset = raf.getFilePointer();
                raf.write(data);
                chunkEntries.set(i, new ChunkEntry(entry.id(), dataOffset, data.length, entry.type()));

                logger.debug("Wrote chunk {}: type={}, length={}, offset={}, firstBytes={}",
                        entry.id(), entry.type(), data.length, dataOffset,
                        Arrays.toString(Arrays.copyOf(data, Math.min(8, data.length)))); // добавлена логика для вывода первых байтов
            }

            // Chunk Table
            long tableOffset = raf.getFilePointer();
            raf.writeInt(chunkEntries.size());
            logger.debug("Writing {} chunk table entries at offset {}", chunkEntries.size(), tableOffset);

            for (ChunkEntry entry : chunkEntries) {
                raf.writeInt(entry.id());
                raf.writeLong(entry.offset());
                raf.writeInt(entry.length());
                raf.writeInt(entry.type().ordinal());

                logger.debug("ChunkTable: id={} offset={} length={} type={} (ordinal={})",
                        entry.id(), entry.offset(), entry.length(), entry.type(), entry.type().ordinal());
            }

            // Patch header
            raf.seek(offsetPos);
            raf.writeLong(tableOffset);
            logger.info("Finished writing CGS file, chunk table offset = {}", tableOffset);
        }
    }
}