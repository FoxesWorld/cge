package org.foxesworld.cge.core.file.cgtex.writer;

import org.foxesworld.cge.core.file.FileWriter;
import org.foxesworld.cge.core.file.cgtex.CGTEXFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.cgtex.TextureEntry;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Writer for CGTEX files containing compressed textures (e.g., DXT).
 */
public class CGTEXFileWriter extends FileWriter {
    private static final Logger logger = LogManager.getLogger(CGTEXFileWriter.class);
    private final File file;
    private final List<TextureEntry> textures = new ArrayList<>();
    private final CGTEXFile cgtexFile;
    private final RandomAccessFile raf;

    public CGTEXFileWriter(CGTEXFile cgtexFile) {
        this.cgtexFile = cgtexFile;
        this.file = cgtexFile.getFile();
        this.raf = cgtexFile.getRaf();
    }

    public void addTexture(TextureEntry textureEntry){
        this.textures.add(textureEntry);
    }

    /**
     * Write the CGTEX file with all added textures.
     */
    public void writeFile() throws IOException {
        if (textures.isEmpty()) {
            throw new IllegalStateException("No textures to write");
        }

        raf.setLength(0);  // Очищаем файл перед записью
        logger.info("Writing CGTEX: {}", file.getAbsolutePath());

        // Запись заголовка
        raf.seek(0);
        raf.writeBytes(this.cgtexFile.getMAGIC());           // 4 байта для MAGIC
        raf.writeInt(this.cgtexFile.getVERSION());           // 4 байта для версии
        raf.writeInt(textures.size());                      // 4 байта для количества текстур

        // Резервируем 8 байтов для dataOffset
        long dataOffsetPos = raf.getFilePointer();
        raf.writeLong(0L);                               // Записываем 0 как placeholder для dataOffset

        long dataOffset = raf.getFilePointer();

        // Запись текстур
        for (int i = 0; i < textures.size(); i++) {
            var tex = textures.get(i);
            logTextureMetadata(i, tex);

            // Запись данных текстуры
            raf.writeShort(tex.getWidth());
            raf.writeShort(tex.getHeight());

            // Запись имени текстуры с динамической длиной
            cgtexFile.writeVariableLengthString(tex.getName());

            raf.writeByte(tex.getFormat());  // Формат текстуры
            raf.writeInt(tex.getCompressedData().length);  // Длина сжатиых данных
            raf.write(tex.getCompressedData());  // Сами данные текстуры
        }

        // Вставляем фактическое значение dataOffset
        raf.seek(dataOffsetPos);
        raf.writeLong(dataOffset);

        logger.info("CGTEX written successfully, dataOffset={}, textures={}", dataOffset, textures.size());
    }

    private void logTextureMetadata(int index, TextureEntry tex) {
        logger.info("Texture [{}] Metadata:", index);
        logger.info("  Name: {}", tex.getName());
        logger.info("  Dimensions: {}x{}", tex.getWidth(), tex.getHeight());
        logger.info("  Format: {}", tex.getFormat());
        logger.info("  Data Length: {} bytes", tex.getCompressedData().length);
    }

    public File getFile() {
        return file;
    }
}
