package org.foxesworld.cge.core.file.cgtex.writer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.FileWriter;
import org.foxesworld.cge.core.file.cgtex.CGTEXFile;
import org.foxesworld.cge.core.file.cgtex.TextureEntry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writer for CGTEX files containing compressed textures (with support for MipMap pyramid).
 */
public class CGTEXFileWriter extends FileWriter {
    private static final Logger logger = LogManager.getLogger(CGTEXFileWriter.class);

    /** Объект CGTEXFile, содержащий файл и основные константы. */
    private final CGTEXFile cgtexFile;

    /** Ссылка на RandomAccessFile для записи. */
    private final RandomAccessFile raf;

    /** Список всех текстур, которые будут записаны в файл. */
    private final List<TextureEntry> textures;

    /**
     * Конструктор.
     * @param cgtexFile экземпляр CGTEXFile, из которого можно получить getFile(), getRaf(), getMAGIC() и getVERSION()
     */
    public CGTEXFileWriter(CGTEXFile cgtexFile) {
        //super(cgtexFile);
        this.cgtexFile = cgtexFile;
        this.raf = cgtexFile.getRaf();
        this.textures = new java.util.ArrayList<>();
    }

    /**
     * Добавить TextureEntry в очередь на запись.
     * @param textureEntry текстура с заполненными параметрами: width, height, name, format, mipMapCount, mipMapLevels
     */
    public void addTexture(TextureEntry textureEntry) {
        this.textures.add(textureEntry);
    }

    /**
     * Записывает CGTEX-файл в соответствии с новой спецификацией,
     * учитывающей поле MipMapCount и данные всех уровней MipMap.
     * @throws IOException если ошибка при записи
     * @throws IllegalStateException если список textures пуст
     */
    public void writeFile() throws IOException {
        if (textures.isEmpty()) {
            throw new IllegalStateException("No textures to write");
        }

        // 1. Очищаем файл (вдруг он уже существовал):
        raf.setLength(0);
        logger.info("=== Writing CGTEX to '{}' ===", cgtexFile.getFile().getAbsolutePath());

        // 2. Запись заголовка: MAGIC (4), VERSION (4), TextureCount (4), placeholder для DataOffset (8)
        raf.seek(0);
        raf.writeBytes(cgtexFile.getMAGIC());                  // 4 байта
        raf.writeInt(cgtexFile.getVERSION());                  // 4 байта
        raf.writeInt(textures.size());                          // 4 байта — число текстур

        long dataOffsetPos = raf.getFilePointer();              // сейчас = 4 + 4 + 4 = 12
        raf.writeLong(0L);                                      // placeholder (8 байт) — сейчас файл размечен до 20 байт

        // 3. Определяем, с какого смещения начинаются записи текстур:
        long dataOffset = raf.getFilePointer(); // ожидаем 20
        logger.debug("DataOffset (actual) = {}", dataOffset);

        // 4. Последовательно записываем каждую TextureEntry:
        for (int i = 0; i < textures.size(); i++) {
            TextureEntry tex = textures.get(i);
            logTextureMetadata(i, tex);

            // 4.1) Ширина и высота базового уровня (2 + 2 байта)
            raf.writeShort((short) tex.getWidth());
            raf.writeShort((short) tex.getHeight());
            logger.debug("  Wrote Width={} Height={}  (offset now {})", tex.getWidth(), tex.getHeight(), raf.getFilePointer());

            // 4.2) Число mipmap-уровней (4 байта)
            int mipMapCount = tex.getMipMapCount();
            if (mipMapCount <= 0) {
                throw new IOException("Invalid mipMapCount (" + mipMapCount + ") for texture index " + i);
            }
            raf.writeInt(mipMapCount);
            logger.debug("  Wrote MipMapCount={}  (offset now {})", mipMapCount, raf.getFilePointer());

            // 4.3) Имя текстуры: NameLength (4 байта) + Name (UTF-8)
            byte[] nameBytes = tex.getName().getBytes(StandardCharsets.UTF_8);
            raf.writeInt(nameBytes.length);
            raf.write(nameBytes);
            logger.debug("  Wrote NameLength={} Name='{}'  (offset now {})", nameBytes.length, tex.getName(), raf.getFilePointer());

            // 4.4) Формат (1 байт)
            raf.writeByte(tex.getFormat());
            logger.debug("  Wrote Format={}  (offset now {})", tex.getFormat(), raf.getFilePointer());

            // 4.5) Padding до 4-байтового выравнивания (опционально, но рекомендуется):
            long curOffset = raf.getFilePointer();
            long padding = (4 - (curOffset % 4)) % 4;
            if (padding > 0) {
                for (int p = 0; p < padding; p++) {
                    raf.writeByte(0);
                }
                logger.debug("  Added {} padding bytes  (offset now {})", padding, raf.getFilePointer());
            }

            // 4.6) Запись данных всех MipMap-уровней:
            List<byte[]> levels = tex.getMipMapLevels();
            if (levels.size() != mipMapCount) {
                throw new IOException("Mismatch between mipMapCount and actual levels list size for texture index " + i);
            }
            long levelOffsetStart = raf.getFilePointer();
            for (int level = 0; level < mipMapCount; level++) {
                byte[] levelData = levels.get(level);
                if (levelData == null) {
                    throw new IOException("Null data for mipmap level " + level + " of texture index " + i);
                }
                // 4.6.1) DataLength (4 байта)
                raf.writeInt(levelData.length);
                // 4.6.2) Data (levelData.length байт)
                raf.write(levelData);
                logger.debug("    Level {}: Wrote DataLength={} bytes (offset_start_of_level_data = {})",
                        level, levelData.length, raf.getFilePointer() - levelData.length);
            }

            logger.info("Texture[{}] completely written (offset_end = {})", i, raf.getFilePointer());
        }

        // 5. «Залатываем» placeholder для DataOffset
        raf.seek(dataOffsetPos);            // возвращаемся в позицию 12
        raf.writeLong(dataOffset);          // записываем фактическое значение (20)
        logger.debug("Patched DataOffset = {} at position {}", dataOffset, dataOffsetPos);

        // 6. Логируем успех и закрываем/флашим (если нужно)
        logger.info("CGTEX written successfully: dataOffset={}, textureCount={}", dataOffset, textures.size());
    }

    /**
     * Логирует базовую информацию о текстуре (имя, размеры, формат, количество уровней).
     */
    private void logTextureMetadata(int index, TextureEntry tex) {
        logger.info("Texture [{}] Metadata:", index);
        logger.info("  Name: {}", tex.getName());
        logger.info("  Dimensions: {}x{}", tex.getWidth(), tex.getHeight());
        logger.info("  Format: {}", tex.getFormat());
        logger.info("  MipMapCount: {}", tex.getMipMapCount());
    }
}
