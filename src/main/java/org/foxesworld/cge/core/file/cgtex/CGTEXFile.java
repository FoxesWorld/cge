package org.foxesworld.cge.core.file.cgtex;

import org.foxesworld.cge.core.file.AbstractFile;

import java.io.File;

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

    /**
     * Открывает .cgtex файл в заданном режиме ("r" или "rw").
     */
    public CGTEXFile(File file, String mode) {
        super(file, mode);
    }
}
