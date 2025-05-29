package org.foxesworld.cge.core.file.cgs;

import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.cgs.parser.CGSFileReader;
import org.foxesworld.cge.core.file.cgs.writer.CGSFileWriter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;

/**
 * CGS-specific file handler: reads/writes CGS header and chunk table using
 * AbstractFile utilities.
 */
public class CGSFile extends AbstractFile {
    private final int MAX_NAME_LENGTH = 4096;
    protected final ByteOrder BYTE_ORDER    = ByteOrder.LITTLE_ENDIAN;

    public CGSFile(File file, String mode) {
        super(file, mode);
        setMAGIC("CGS0");
        setVERSION(1);
    }

    /**
     * Writes the CGS header; returns position to backfill the chunk table offset.
     */

    public CGSFileReader readFile(){
        try {
            return new CGSFileReader(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeFile(){

    }

    public int getMAX_NAME_LENGTH() {
        return MAX_NAME_LENGTH;
    }

    public ByteOrder getBYTE_ORDER() {
        return BYTE_ORDER;
    }
}
