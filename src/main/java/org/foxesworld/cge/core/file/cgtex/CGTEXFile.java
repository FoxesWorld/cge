package org.foxesworld.cge.core.file.cgtex;

import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.cgtex.reader.CGTEXFileReader;
import org.foxesworld.cge.core.file.cgtex.writer.CGTEXFileWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CGTEXFile — контейнер для хранения нескольких DXT-текстур в одном файле .cgtex.
 * Наследует AbstractFile, но фактически делегирует работу по чтению/записи
 * в CGTEXFileReader/CGTEXFileWriter.
 */
public class CGTEXFile extends AbstractFile {

    /**
     * Plain-Data container для одной текстуры.
     */
    protected final String MAGIC = "CGTX";
    protected final int VERSION = 1;
    public static class TextureData {
        public final int    width;
        public final int    height;
        public final byte   format;
        public final byte[] compressedData;

        public TextureData(int width, int height, byte format, byte[] compressedData) {
            if (compressedData == null) {
                throw new IllegalArgumentException("compressedData cannot be null");
            }
            this.width          = width;
            this.height         = height;
            this.format         = format;
            this.compressedData = compressedData;
        }
    }

    /**
     * Открывает .cgtex файл в заданном режиме ("r" или "rw").
     */
    public CGTEXFile(File file, String mode) {
        super(file, mode);
    }

    /**
     * Делегирует запись списка текстур CGTEXFileWriter.
     */
    public void writeTextures(List<TextureData> textures) throws IOException {
        // Используем собственный File, но открываем новый writer
        try (CGTEXFileWriter writer = new CGTEXFileWriter(getFile())) {
            for (TextureData td : textures) {
                //writer.addTexture(td.width, td.height, td.format, td.compressedData);
            }
            writer.writeToFile();
        }
    }

    /**
     * Делегирует чтение списка текстур CGTEXFileReader.
     */
    public List<TextureData> readTextures() throws IOException {
        List<TextureData> result = new ArrayList<>();
        try (CGTEXFileReader reader = new CGTEXFileReader(getFile())) {
            for (TextureEntry entry : reader.getTextures()) {
                result.add(new TextureData(
                        entry.getWidth(),
                        entry.getHeight(),
                        entry.getFormat(),
                        entry.getCompressedData()
                ));
            }
        }
        return result;
    }

    /**
     * Возвращает связанный File.
     */
    public File getFile() {
        return super.getFile();
    }
}
