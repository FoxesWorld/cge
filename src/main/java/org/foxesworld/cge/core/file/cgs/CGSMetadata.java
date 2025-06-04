package org.foxesworld.cge.core.file.cgs;

import org.foxesworld.cge.core.file.Metadata;

public class CGSMetadata extends Metadata {
    private final String magic;
    private final String sceneName;
    private final int version;
    private int chunkCount;

    public CGSMetadata(String magic, String sceneName, int version, long tableOffset, int chunkCount) {
        this.magic = magic;
        this.sceneName = sceneName;
        this.version = version;
        this.tableOffset = tableOffset;
        this.chunkCount = chunkCount;
    }

    public String getMagic() {
        return magic;
    }

    public String getSceneName() {
        return sceneName;
    }

    public int getVersion() {
        return version;
    }

    public long getTableOffset() {
        return tableOffset;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    @Override
    public String toString() {
        return "CGSMetadata{" +
                "magic='" + magic + '\'' +
                ", sceneName='" + sceneName + '\'' +
                ", version=" + version +
                ", tableOffset=" + tableOffset +
                ", chunkCount=" + chunkCount +
                '}';
    }
}