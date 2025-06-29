package org.foxesworld.cge.modules.terrain;

// Пример того, как может выглядеть ваш TerrainConfig.java
public class TerrainConfig {

    // Новые поля для процедурной генерации
    private int size = 129; // 2^n + 1, например 65, 129, 257
    private float patchSize = 16f; // Размер одной ячейки сетки
    private float heightScale = 8f; // Максимальная высота холмов
    private float noiseScale = 0.8f; // Масштаб шума (частота холмов)
    private float textureScale = 128f; // Масштаб текстуры (тайлинг)

    // Getters для всех полей
    public int getSize() { return size; }
    public float getPatchSize() { return patchSize; }
    public float getHeightScale() { return heightScale; }
    public float getNoiseScale() { return noiseScale; }
    public float getTextureScale() { return textureScale; }
}