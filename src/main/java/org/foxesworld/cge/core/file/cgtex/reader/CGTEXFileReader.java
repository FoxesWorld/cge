package org.foxesworld.cge.core.file.cgtex.reader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.FileReader;
import org.foxesworld.cge.core.file.cgtex.CGTEXFile;
import org.foxesworld.cge.core.file.cgtex.CGTEXMetadata;
import org.foxesworld.cge.core.file.cgtex.TextureEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Класс для чтения CGTEX-файла, содержащего DXT-текстуры,
 * с учётом MipMap-пирамиды (MipMapCount + данные всех уровней).
 */
public class CGTEXFileReader extends FileReader {
    private static final Logger logger = LogManager.getLogger(CGTEXFileReader.class);

    /** Метаданные файла (magic, version, textureCount, dataOffset, fileSize) **/
    private final CGTEXMetadata metadata;

    /** Список прочитанных TextureEntry (каждая со всеми mip-уровнями) **/
    private final List<TextureEntry> textures = new ArrayList<>();

    /**
     * Конструктор для чтения CGTEX-файла.
     *
     * @param cgtexFile CGTEXFile, с которым будет работать этот класс.
     * @throws IOException если произошла ошибка при чтении.
     */
    public CGTEXFileReader(CGTEXFile cgtexFile) throws IOException {
        super(cgtexFile);
        logger.debug("================ CGTEX FILE READ START ================");
        logger.debug("Opening file: {}", cgtexFile.getFile().getAbsolutePath());

        // DEBUG: Вывод полного дампа файла в HEX (просто размер, без самого содержимого,
        // чтобы не утяжелять логи, можно убрать чтение большого массива):
        try {
            byte[] allBytes = Files.readAllBytes(cgtexFile.getFile().toPath());
            logger.debug("Full file size = {} bytes", allBytes.length);
        } catch (IOException e) {
            logger.warn("Failed to dump full file bytes: {}", e.getMessage());
        }

        // 1) Сначала читаем заголовок (magic, version, textureCount, dataOffset, fileSize).
        this.metadata = readHeader();
        logger.debug("Header parsed: {}", metadata);

        // 2) Переходим к блоку данных (offset = metadata.getDataOffset()).
        raf.seek(metadata.getDataOffset());

        // 3) Читаем каждую текстуру в цикле:
        for (int i = 0; i < metadata.getTextureCount(); i++) {
            logger.debug("=== Reading Texture #{} (offset {}) ===", i, raf.getFilePointer());

            // 3.1) Считываем ширину и высоту базового уровня (2+2 байта):
            int width = raf.readUnsignedShort();
            int height = raf.readUnsignedShort();
            logger.debug("  Base Level: width={} px, height={} px", width, height);

            // 3.2) Считываем количество MipMap-уровней (4 байта, int32):
            int mipMapCount = raf.readInt();
            if (mipMapCount <= 0) {
                throw new IOException("Invalid mipMapCount (" + mipMapCount + ") for texture index " + i);
            }
            logger.debug("  MipMapCount = {}", mipMapCount);

            // 3.3) Читаем длину имени (4 байта, int32):
            int nameLength = raf.readInt();
            if (nameLength < 0) {
                throw new IOException("Invalid nameLength (" + nameLength + ") for texture index " + i);
            }
            byte[] nameBytes = new byte[nameLength];
            raf.readFully(nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);
            if (name.isEmpty()) {
                logger.warn("  Empty texture name at index {} — назначаем дефолтное", i);
                name = "UnnamedTexture_" + i;
            }
            logger.debug("  Texture name = '{}'", name);

            // 3.4) Считываем формат (1 байт):
            byte format = raf.readByte();
            logger.debug("  Format byte = {}", format);

            // 3.5) Опционально: выравнивание до 4 байт (если нужно).
            // Например, если (1 байт формат + предыдущее поле nameLength+name) не выровнены:
            long currentOffsetAfterFormat = raf.getFilePointer();
            long align = (currentOffsetAfterFormat % 4);
            if (align != 0) {
                long padding = 4 - align;
                raf.skipBytes((int) padding);
                logger.debug("  Skipped {} pad-bytes to align to 4 bytes", padding);
            }

            // 3.6) Подготавливаем список для байтов каждого уровня:
            List<byte[]> mipMapLevels = new ArrayList<>(mipMapCount);

            // 3.7) Для каждого уровня (0..mipMapCount−1) читаем DataLength + собственно Data:
            for (int level = 0; level < mipMapCount; level++) {
                // 3.7.1) Считываем длину блока данных этого уровня (4 байта):
                int dataLength = raf.readInt();
                if (dataLength < 0) {
                    throw new IOException("Invalid dataLength (" + dataLength +
                            ") at texture #" + i + ", mip level " + level);
                }
                logger.debug("    Level {}: DataLength = {} bytes", level, dataLength);

                // 3.7.2) Выделяем буфер и читаем данные в него:
                byte[] levelData = new byte[dataLength];
                raf.readFully(levelData);
                mipMapLevels.add(levelData);
                logger.debug("    Level {}: Data read ({} bytes) at offset {}",
                        level, dataLength, raf.getFilePointer() - dataLength);
            }

            // 3.8) Собираем TextureEntry и добавляем в список:
            TextureEntry entry = new TextureEntry(width, height, name, format, mipMapCount, mipMapLevels);
            textures.add(entry);

            logger.info("Texture[{}] parsed: {}", i, entry.toString());
        }

        logger.debug("================= CGTEX FILE READ END =================");
    }

    /**
     * Читает и возвращает метаданные (magic, version, textureCount, dataOffset, fileSize).
     * @return экземпляр CGTEXMetadata
     * @throws IOException при ошибках чтения или неверном magic
     */
    private CGTEXMetadata readHeader() throws IOException {
        raf.seek(0); // возвращаемся в начало

        // 1) Считываем 4 байта magic
        byte[] magicBytes = new byte[4];
        raf.readFully(magicBytes);
        String magic = new String(magicBytes, StandardCharsets.US_ASCII);

        // 2) Проверяем magic (например, "CGTX")
        if (!this.getThisFile().getMAGIC().equals(magic)) {
            throw new IOException("Invalid CGTEX file magic: " + magic);
        }
        logger.debug("Magic = '{}'", magic);

        // 3) Считываем версию (4 байта, int32)
        int version = raf.readInt();
        logger.debug("Version = {}", version);

        // 4) Считываем количество текстур (4 байта, int32)
        int textureCount = raf.readInt();
        if (textureCount < 0) {
            throw new IOException("Invalid textureCount: " + textureCount);
        }
        logger.debug("TextureCount = {}", textureCount);

        // 5) Считываем смещение до блока данных (8 байт, int64)
        long dataOffset = raf.readLong();
        if (dataOffset < 0 || dataOffset > raf.length()) {
            throw new IOException("Invalid dataOffset: " + dataOffset);
        }
        logger.debug("DataOffset = {}", dataOffset);

        // 6) Получаем общий размер файла
        long fileSize = raf.length();
        logger.debug("FileSize = {}", fileSize);

        return new CGTEXMetadata(magic, version, textureCount, dataOffset, fileSize);
    }

    /** @return метаданные */
    public CGTEXMetadata getMetadata() {
        return metadata;
    }

    /** @return «немодифицируемый» список всех TextureEntry */
    public List<TextureEntry> getTextures() {
        return List.copyOf(textures);
    }
}
