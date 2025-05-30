package org.foxesworld.cge.core.file.ydr;

import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.FileReader;

import java.io.File;

public class YDRFile extends AbstractFile {
    //TODO
    protected YDRFile(File file, String mode) {
        super(file, mode);
        this.setMAGIC("CGTX");
        this.setVERSION(1);
    }

    @Override
    protected FileReader readFile() {
        return null;
    }

}
