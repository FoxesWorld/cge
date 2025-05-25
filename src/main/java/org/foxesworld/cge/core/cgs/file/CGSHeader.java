package org.foxesworld.cge.core.cgs.file;

/**
 * Simple POJO for CGS header data.
 */
public class CGSHeader {
    final int version;
    final String sceneName;
    final long tableOffset;

    CGSHeader(int version, String sceneName, long tableOffset) {
        this.version = version;
        this.sceneName = sceneName;
        this.tableOffset = tableOffset;
    }

    public int getVersion() {
        return version;
    }

    public String getSceneName() {
        return sceneName;
    }

    public long getTableOffset() {
        return tableOffset;
    }
}
