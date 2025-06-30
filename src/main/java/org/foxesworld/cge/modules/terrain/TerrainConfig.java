package org.foxesworld.cge.modules.terrain;

// Пример того, как может выглядеть ваш TerrainConfig.java
public class TerrainConfig {

    // Новые поля для процедурной генерации
    private int size = 129; // 2^n + 1, например 65, 129, 257
    private float patchSize = 32f; // Размер одной ячейки сетки
    private float heightScale = 6f; // Максимальная высота холмов
    private float noiseScale = 1.1f; // Масштаб шума (частота холмов)
    private float textureScale = 128f; // Масштаб текстуры (тайлинг)
    private int octaves = 6;
    private float lacunarity = 2.0f;
    private float persistence = 0.5f;

    // Getters для всех полей
    public int getSize() { return size; }
    public float getPatchSize() { return patchSize; }
    public float getHeightScale() { return heightScale; }
    public float getNoiseScale() { return noiseScale; }
    public float getTextureScale() { return textureScale; }
    public int getOctaves() { return octaves; }
    public float getLacunarity() { return lacunarity; }
    public float getPersistence() { return persistence; }
}