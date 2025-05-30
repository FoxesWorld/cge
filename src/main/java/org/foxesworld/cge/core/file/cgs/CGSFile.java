package org.foxesworld.cge.core.file.cgs;

import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.FileWriter;
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

    public CGSFile(File file, String mode) {
        super(file, mode);
        setMAGIC("CGS0");
        setVERSION(1);
        setMAX_NAME_LENGTH(4096);
        setBYTE_ORDER(ByteOrder.LITTLE_ENDIAN);
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
    public FileWriter writeFile(){
        return null;
    }
}
