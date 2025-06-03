package org.foxesworld.cge.core.file.cgtex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Описание одной текстуры внутри CGTEX-файла, включая все mipmap-уровни.
 */
public class TextureEntry {
    private final int width;
    private final int height;
    private final String name;
    private final byte format;
    private final int mipMapCount;
    private final List<byte[]> mipMapLevels; // mipMapLevels.get(0) = base level, затем следующие

    /**
     * Конструктор TextureEntry, учитывающий все уровни mipmap.
     * @param width ширина базового уровня
     * @param height высота базового уровня
     * @param name имя текстуры
     * @param format формат (DXT1, DXT3 и т. д.)
     * @param mipMapCount количество уровней mipmap (включая базовый)
     * @param mipMapLevels список массивов байт для каждого уровня (должен иметь размер mipMapCount)
     */
    public TextureEntry(int width, int height, String name, byte format, int mipMapCount, List<byte[]> mipMapLevels) {
        if (mipMapLevels == null || mipMapLevels.size() != mipMapCount) {
            throw new IllegalArgumentException("Список mipMapLevels должен быть размером " + mipMapCount);
        }
        this.width = width;
        this.height = height;
        this.name = name;
        this.format = format;
        this.mipMapCount = mipMapCount;
        this.mipMapLevels = new ArrayList<>(mipMapLevels);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getName() {
        return name;
    }

    public byte getFormat() {
        return format;
    }

    public int getMipMapCount() {
        return mipMapCount;
    }

    /**
     * @return Нельзя модифицировать внешний список, возвращаем копию.
     */
    public List<byte[]> getMipMapLevels() {
        return Collections.unmodifiableList(mipMapLevels);
    }

    /**
     * Получить байты конкретного уровня (0 — базовый).
     * @param level индекс уровня (0..mipMapCount−1)
     * @return массив байт этого уровня.
     */
    public byte[] getMipMapLevelData(int level) {
        if (level < 0 || level >= mipMapCount) {
            throw new IndexOutOfBoundsException("Уровень выходит за границы: " + level);
        }
        return mipMapLevels.get(level);
    }

    @Override
    public String toString() {
        return String.format("TextureEntry{name='%s', %dx%d, format=%d, mipMapCount=%d}",
                name, width, height, format, mipMapCount);
    }
}
