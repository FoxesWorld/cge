package org.foxesworld.cge.core.file.cgtex.reader;

import org.foxesworld.cge.core.file.cgtex.CGTEXFile;
import org.foxesworld.cge.core.file.cgtex.CGTEXMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.cgtex.TextureEntry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HexFormat;

/**
 * CGTEX file reader — читает контейнер с DXT текстурами.
 */
public class CGTEXFileReader extends CGTEXFile {
    private static final Logger logger = LogManager.getLogger(CGTEXFileReader.class);
    private static final HexFormat HEX = HexFormat.of();

    private final CGTEXMetadata metadata;
    private final List<TextureEntry> textures = new ArrayList<>();

    public CGTEXFileReader(File file) throws IOException {
        super(file, "r");
        logger.debug("================ CGTEX FILE READ START ================");
        logger.debug("Opening file: {}", file.getAbsolutePath());

        // DEBUG: Вывод полного дампа файла в HEX
        try {
            byte[] allBytes = Files.readAllBytes(file.toPath());
            logger.debug("Full file HEX dump ({} bytes)", allBytes.length);
        } catch (IOException e) {
            logger.warn("Failed to dump full file hex: {}", e.getMessage());
        }

        // Читаем заголовок
        this.metadata = readHeader();
        logger.debug("Header Parsed: {}", metadata);

        // Читаем текстуры
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

            // Проверяем на пустое имя
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

            logger.debug("Texture[{}]: name={} size={} format={}", i, entry.getName(), entry.getWidth()+'x'+entry.getHeight(), entry.getFormat());
        }

        logger.debug("================= CGTEX FILE READ END =================");
    }

    private CGTEXMetadata readHeader() throws IOException {
        raf.seek(0);
        byte[] magicBytes = new byte[4];
        raf.readFully(magicBytes);
        String magic = new String(magicBytes);

        if (!"CGTX".equals(magic)) {
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

    @Override
    public void close() throws IOException {
        logger.info("Closing CGTEX file: {}", getFile().getAbsolutePath());
        super.close();
    }
}
