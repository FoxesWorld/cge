package org.foxesworld.cge.core.cgs.parser.types;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.cgs.SceneChunk;
import org.foxesworld.cge.core.cgs.parser.ChunkParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Читает TERRAIN-чанки: целочисленная ширина/высота, затем width*height float-значений высот.
 */
public class TerrainParser implements ChunkParser {
    @Override
    public Spatial parse(CalistaGameEngine calistaGameEngine, SceneChunk chunk) {
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


        // Ресайзим
       ResizedHeightmap resized = resizeHeightmapToPowerOfTwoPlusOne(heights, width, height);

    // Создаём TerrainQuad
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

    /**
     * Приводит массив высот к размеру (2^n + 1) x (2^n + 1),
     * дополняя недостающие значения нулями.
     *
     * @param original исходный heightmap размером width x height
     * @param width ширина исходного heightmap
     * @param height высота исходного heightmap
     * @return новый массив высот правильного размера
     */
    public static ResizedHeightmap resizeHeightmapToPowerOfTwoPlusOne(float[] original, int width, int height) {
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
                    resized[y * targetSize + x] = original[y * width + x];
                } else {
                    resized[y * targetSize + x] = 0f; // Заполняем недостающие значения
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
