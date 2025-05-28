package org.foxesworld.cge.core.file.cgtex.reader;

import org.foxesworld.cge.core.file.cgtex.CGTEXFile;
import org.foxesworld.cge.core.file.cgtex.CGTEXMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.cgtex.TextureEntry;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
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
        logger.info("================ CGTEX FILE READ START ================");
        logger.info("Opening file: {}", file.getAbsolutePath());

        // DEBUG: Вывод полного дампа файла в HEX
        try {
            byte[] allBytes = Files.readAllBytes(file.toPath());
            logger.debug("Full file HEX dump ({} bytes):\n{}", allBytes.length, HEX.formatHex(allBytes));
        } catch (IOException e) {
            logger.warn("Failed to dump full file hex: {}", e.getMessage());
        }

        // Читаем заголовок
        this.metadata = readHeader();
        logger.info("Header Parsed: {}", metadata);

        // Читаем текстуры
        raf.seek(metadata.getDataOffset());
        for (int i = 0; i < metadata.getTextureCount(); i++) {
            int width = raf.readUnsignedShort();
            int height = raf.readUnsignedShort();
            byte format = raf.readByte();
            int dataLength = raf.readInt();
            byte[] data = new byte[dataLength];
            raf.readFully(data);

            TextureEntry entry = new TextureEntry(width, height, format, data);
            textures.add(entry);

            logger.debug("Texture[{}]: {}", i, entry);
        }

        logger.info("================= CGTEX FILE READ END =================");
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
