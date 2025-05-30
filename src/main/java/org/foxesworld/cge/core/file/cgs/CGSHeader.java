package org.foxesworld.cge.core.file.cgs;

import org.foxesworld.cge.core.file.cgs.parser.CGSFileReader;

/**
 * Simple POJO for CGS header data.
 */
public class CGSHeader {
    final int version;
    final String sceneName;
    final String magic;
    final long tableOffset;

    public CGSHeader(CGSFileReader cgsFileReader) {
        this.version = cgsFileReader.getThisFile().getVERSION();
        this.sceneName = cgsFileReader.getSceneName();
        this.magic = cgsFileReader.getThisFile().getMAGIC();
        this.tableOffset = cgsFileReader.getTableOffset();
    }

    public int getVersion() {
        return version;
    }

    public String getMagic() {
        return magic;
    }

    public String getSceneName() {
        return sceneName;
    }

    public long getTableOffset() {
        return tableOffset;
    }
}
