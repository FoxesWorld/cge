package org.foxesworld.cge.core.file.cgtex.writer;

import org.foxesworld.cge.core.file.cgtex.CGTEXFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writer for CGTEX files containing compressed textures (e.g., DXT).
 */
public class CGTEXFileWriter extends CGTEXFile {
    private static final Logger logger = LogManager.getLogger(CGTEXFileWriter.class);
    private final File file;
    private final List<TextureEntry> textures = new ArrayList<>();

    public record TextureEntry(int width, int height, byte format, byte[] data) {
        public TextureEntry {
            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("Texture data cannot be null or empty");
            }
        }
    }

    public CGTEXFileWriter(File file) {
        super(file, "rw");
        this.file = file;
    }

    /**
     * Add a texture to be written into the CGTEX file.
     * @return index of the newly added texture
     */
    public int addTexture(int width, int height, byte format, byte[] data) {
        var entry = new TextureEntry(width, height, format, data);
        textures.add(entry);
        logger.debug("Queued texture: {}x{}, format={}, size={}", width, height, format, data.length);
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
        raf.writeLong(0L);

        long dataOffset = raf.getFilePointer();

        // Write textures
        for (int i = 0; i < textures.size(); i++) {
            var tex = textures.get(i);
            logTextureInfo(i, tex);
            raf.writeShort(tex.width());
            raf.writeShort(tex.height());
            raf.writeByte(tex.format());
            raf.writeInt(tex.data().length);
            raf.write(tex.data());
        }

        // Patch in the actual data offset
        raf.seek(dataOffsetPos);
        raf.writeLong(dataOffset);

        logger.info("CGTEX written successfully, dataOffset={}, textures={}", dataOffset, textures.size());
    }

    private void logTextureInfo(int index, TextureEntry tex) {
        logger.debug("Texture [{}]: {}x{}, format={}, length={} bytes",
                index, tex.width(), tex.height(), tex.format(), tex.data().length);
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
