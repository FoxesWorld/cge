package org.foxesworld.cge.core.cgs.parser.types;

import com.jme3.material.Material;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.cgs.SceneChunk;
import org.foxesworld.cge.core.cgs.parser.ChunkParser;
import org.foxesworld.cge.core.cgs.ChunkFieldTypeConfigLoader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class TerrainParser extends ChunkParser {

    @Override
    protected String getType(ByteBuffer buf) {
        return null;
    }

    @Override
    public Spatial parse(CalistaGameEngine calistaGameEngine, SceneChunk chunk, ChunkFieldTypeConfigLoader typeConfigLoader) {
        fieldTypes = typeConfigLoader.getSubTypesForChunkType("TERRAIN");
        ByteBuffer buf = chunk.getData();
        buf.rewind();

        int width = buf.getInt();
        int height = buf.getInt();
        Node terrainNode = new Node("TerrainChunk-" + chunk.getId());
        int expected = width * height;

        // Проверка безопасности
        if (buf.remaining() < expected * Float.BYTES) {
            try {
                throw new IOException("Not enough data in terrain chunk: need " + (expected * 4) + " bytes, found " + buf.remaining());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Чтение данных вручную
        float[] heights = new float[expected];
        for (int i = 0; i < expected; i++) {
            heights[i] = buf.getFloat();
        }

        // Извлекаем параметры из params
        int[] heightMap = extractHeightMap(typeConfigLoader.getFieldTypesForChunkSubType("TERRAIN", "FLAT"));
        int detailLevel = extractDetailLevel(typeConfigLoader.getFieldTypesForChunkSubType("TERRAIN", "FLAT"));

        // Используем аргументы для высот и уровня детализации
        ResizedHeightmap resized = resizeHeightmapToPowerOfTwoPlusOne(heights, width, height, heightMap, detailLevel);

        // Создаем TerrainQuad
        TerrainQuad quad = new TerrainQuad(
                "terrain-" + chunk.getId(),
                resized.size - 1, // patchSize
                resized.size,
                resized.heights
        );

        quad.setLocalTranslation(0, 0, 0);

        Material mat = new Material(calistaGameEngine.getAssetManager(),
                "Common/MatDefs/Terrain/Terrain.j3md");
        mat.setBoolean("useTriPlanarMapping", false);
        quad.setMaterial(mat);

        terrainNode.attachChild(quad);
        return terrainNode;
    }

    // Извлекаем heightMap из параметров
    private int[] extractHeightMap(Map<String, String> params) {
        if (params.containsKey("heightMap")) {
            String heightMapStr = params.get("heightMap");
            return convertToIntArray(heightMapStr);
        }
        return new int[0]; // Если не найдено, возвращаем пустой массив
    }

    // Извлекаем detailLevel из параметров
    private int extractDetailLevel(Map<String, String> params) {
        if (params.containsKey("detailLevel")) {
            return Integer.parseInt(params.get("detailLevel"));
        }
        return 1; // Значение по умолчанию
    }

    // Преобразуем строку в массив целых чисел
    private int[] convertToIntArray(String str) {
        String[] split = str.split(",");
        int[] result = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            result[i] = Integer.parseInt(split[i].trim());
        }
        return result;
    }

    public static ResizedHeightmap resizeHeightmapToPowerOfTwoPlusOne(
            float[] original, int width, int height, int[] heightMap, int detailLevel) {
        if (original == null || original.length != width * height) {
            throw new IllegalArgumentException("Invalid heightmap size");
        }

        // Находим ближайший (2^n + 1)
        int targetSize = 1;
        while (targetSize - 1 < Math.max(width, height)) {
            targetSize <<= 1;
        }
        targetSize += 1;

        float[] resized = new float[targetSize * targetSize];
        for (int y = 0; y < targetSize; y++) {
            for (int x = 0; x < targetSize; x++) {
                if (x < width && y < height) {
                    resized[y * targetSize + x] = original[y * width + x] * detailLevel;  // Применение уровня детализации
                } else {
                    resized[y * targetSize + x] = heightMap[y * targetSize + x];  // Заполнение дополнительными значениями
                }
            }
        }

        return new ResizedHeightmap(resized, targetSize);
    }

    /** Контейнер результата */
    public static class ResizedHeightmap {
        public final float[] heights;
        public final int size;

        public ResizedHeightmap(float[] heights, int size) {
            this.heights = heights;
            this.size = size;
        }
    }
}
