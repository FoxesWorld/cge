package org.foxesworld.cge.core.file;

import java.io.RandomAccessFile;

public abstract class FileReader {

    private AbstractFile thisFile;
    protected final RandomAccessFile raf;
    public FileReader(AbstractFile abstractFile){
        this.thisFile = abstractFile;
        this.raf = abstractFile.getRaf();
    }
    public AbstractFile getThisFile() {
        return thisFile;
    }
}
