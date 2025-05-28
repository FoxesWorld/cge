package org.foxesworld.cge.core.file.cgtex.writer;

import org.foxesworld.cge.core.file.cgtex.CGTEXFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.cgtex.TextureEntry;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Writer for CGTEX files containing compressed textures (e.g., DXT).
 */
public class CGTEXFileWriter extends CGTEXFile {
    private static final Logger logger = LogManager.getLogger(CGTEXFileWriter.class);
    private final File file;
    private final List<TextureEntry> textures = new ArrayList<>();

    private static final String MAGIC = "CGTX"; // Магическая строка для CGTEX
    private static final int VERSION = 1;      // Версия формата

    public CGTEXFileWriter(File file) {
        super(file, "rw");
        this.file = file;
    }

    /**
     * Add a texture to be written into the CGTEX file.
     * @return index of the newly added texture
     */
    public int addTexture(int width, int height, String name, byte format, byte[] data) {
        var entry = new TextureEntry(width, height, name, format, data);
        textures.add(entry);
        logger.debug("Queued texture: {}x{}, format={}, size={}, name={}", width, height, format, data.length, name);
        return textures.size() - 1;
    }

    /**
     * Write the CGTEX file with all added textures.
     */
    public void writeToFile() throws IOException {
        if (textures.isEmpty()) {
            throw new IllegalStateException("No textures to write");
        }

        raf.setLength(0);
        logger.info("Writing CGTEX: {}", file.getAbsolutePath());

        // Write header
        raf.seek(0);
        raf.writeBytes(MAGIC);           // 4-byte magic
        raf.writeInt(VERSION);           // 4-byte version
        raf.writeInt(textures.size());   // 4-byte texture count

        // Reserve 8 bytes for dataOffset
        long dataOffsetPos = raf.getFilePointer();
        raf.writeLong(0L);  // Placeholder for data offset

        long dataOffset = raf.getFilePointer();

        // Write textures
        for (int i = 0; i < textures.size(); i++) {
            var tex = textures.get(i);
            logTextureInfo(i, tex);
            raf.writeShort(tex.getWidth());
            raf.writeShort(tex.getHeight());

            // Запись имени текстуры с динамической длиной
            writeVariableLengthString(tex.getName());

            raf.writeByte(tex.getFormat());
            raf.writeInt(tex.getCompressedData().length);
            raf.write(tex.getCompressedData());
        }

        // Patch in the actual data offset
        raf.seek(dataOffsetPos);
        raf.writeLong(dataOffset);

        logger.info("CGTEX written successfully, dataOffset={}, textures={}", dataOffset, textures.size());
    }

    private void logTextureInfo(int index, TextureEntry tex) {
        logger.debug("Texture [{}]: {}x{}, format={}, length={} bytes, name={}",
                index, tex.getWidth(), tex.getHeight(), tex.getFormat(), tex.getCompressedData().length, tex.getName());
    }

    public File getFile() {
        return file;
    }

    public int getTextureCount() {
        return textures.size();
    }

    public TextureEntry getTexture(int index) {
        return (index >= 0 && index < textures.size()) ? textures.get(index) : null;
    }
}
