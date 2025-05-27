package org.foxesworld.cge.core.cgs.file;

/**
 * Simple POJO for CGS header data.
 */
public class CGSHeader {
    final int version;
    final String sceneName;
    final String magic;
    final long tableOffset;

    CGSHeader(int version, String sceneName, String magic, long tableOffset) {
        this.version = version;
        this.sceneName = sceneName;
        this.magic = magic;
        this.tableOffset = tableOffset;
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
