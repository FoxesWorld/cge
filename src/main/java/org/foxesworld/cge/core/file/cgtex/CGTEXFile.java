package org.foxesworld.cge.core.file.cgtex;

import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.cgtex.reader.CGTEXFileReader;
import org.foxesworld.cge.core.file.cgtex.writer.CGTEXFileWriter;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CGTEXFile extends AbstractFile {

    public CGTEXFile(File file, String mode) {
        super(file, mode);
        this.setMAGIC("CGTX");
        this.setVERSION(1);
    }
    @Override
    public CGTEXFileReader readFile() {
        try {
            return new CGTEXFileReader(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeFile(List<TextureEntry> textureEntryList){
        CGTEXFileWriter writer = new CGTEXFileWriter(this);
        for (TextureEntry entry: textureEntryList) {
            writer.addTexture(entry);
        }
        try {
            writer.writeFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
