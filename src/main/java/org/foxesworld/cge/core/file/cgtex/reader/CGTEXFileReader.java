package org.foxesworld.cge.core.file.cgtex.reader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.cgtex.CGTEXFile;
import org.foxesworld.cge.core.file.cgtex.CGTEXMetadata;
import org.foxesworld.cge.core.file.cgtex.TextureEntry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Читает CGTEX файл, содержащий DXT текстуры.
 */
public class CGTEXFileReader {
    private static final Logger logger = LogManager.getLogger(CGTEXFileReader.class);
    private final CGTEXFile cgtexFile;
    private final RandomAccessFile raf;
    private final CGTEXMetadata metadata;
    private final List<TextureEntry> textures = new ArrayList<>();

    /**
     * Конструктор для чтения CGTEX файла.
     * @param cgtexFile CGTEXFile, с которым будет работать этот класс.
     * @throws IOException Если произошла ошибка при чтении.
     */
    public CGTEXFileReader(CGTEXFile cgtexFile) throws IOException {
        this.cgtexFile = cgtexFile;
        this.raf = cgtexFile.getRaf();  // Получаем RandomAccessFile из CGTEXFile

        logger.debug("================ CGTEX FILE READ START ================");
        logger.debug("Opening file: {}", cgtexFile.getFile().getAbsolutePath());

        // DEBUG: Вывод полного дампа файла в HEX
        try {
            byte[] allBytes = Files.readAllBytes(cgtexFile.getFile().toPath());
            logger.debug("Full file HEX dump ({} bytes)", allBytes.length);
        } catch (IOException e) {
            logger.warn("Failed to dump full file hex: {}", e.getMessage());
        }

        // Чтение заголовка
        this.metadata = readHeader();
        logger.debug("Header Parsed: {}", metadata);

        // Чтение текстур
        raf.seek(metadata.getDataOffset());
        for (int i = 0; i < metadata.getTextureCount(); i++) {
            int width = raf.readUnsignedShort();
            int height = raf.readUnsignedShort();

            // Чтение длины имени текстуры (4 байта)
            int nameLength = raf.readInt();
            byte[] nameBytes = new byte[nameLength];

            // Чтение имени текстуры
            raf.readFully(nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);

            // Проверка на пустое имя
            if (name.isEmpty()) {
                logger.warn("Empty texture name found at index {}", i);
                name = "UnnamedTexture_" + i; // Назначаем имя по умолчанию
            }

            byte format = raf.readByte();
            int dataLength = raf.readInt();
            byte[] data = new byte[dataLength];

            // Проверка на отрицательную длину данных
            if (dataLength < 0) {
                throw new IOException("Invalid data length for texture: " + dataLength);
            }

            raf.readFully(data);

            TextureEntry entry = new TextureEntry(width, height, name, format, data);
            textures.add(entry);

            logger.debug("Texture[{}]: name={} size={}x{} format={}", i, entry.getName(), entry.getWidth()+'x'+entry.getHeight(), entry.getFormat());
        }

        logger.debug("================= CGTEX FILE READ END =================");
    }

    private CGTEXMetadata readHeader() throws IOException {
        raf.seek(0); // Перемещаем указатель в начало файла
        byte[] magicBytes = new byte[4];
        raf.readFully(magicBytes);
        String magic = new String(magicBytes);

        if (!this.cgtexFile.getMAGIC().equals(magic)) {
            throw new IOException("Invalid CGTEX file magic: " + magic);
        }

        int version = raf.readInt();
        int textureCount = raf.readInt();
        long dataOffset = raf.readLong();
        long fileSize = raf.length();

        return new CGTEXMetadata(magic, version, textureCount, dataOffset, fileSize);
    }

    public CGTEXMetadata getMetadata() {
        return metadata;
    }

    public List<TextureEntry> getTextures() {
        return List.copyOf(textures);
    }
}
