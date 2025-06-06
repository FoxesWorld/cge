package org.foxesworld.cge.core.file.extensions.cgtex;

import org.foxesworld.cge.core.file.Metadata;

public class CGTEXMetadata extends Metadata {
    private int textureCount;
    private long fileSize;

    public CGTEXMetadata(String magic, int version, int textureCount, long tableOffset, long fileSize) {
        this.magic = magic;
        this.version = version;
        this.textureCount = textureCount;
        this.tableOffset = tableOffset;
        this.fileSize = fileSize;
    }

    public String getMagic() {
        return magic;
    }

    public int getVersion() {
        return version;
    }
    public int getTextureCount() {
        return textureCount;
    }
    public long getTableOffset() {
        return tableOffset;
    }
    public long getFileSize() {
        return fileSize;
    }

    @Override
    public String toString() {
        return "CGTEXMetadata{" +
                "magic='" + magic + '\'' +
                ", version=" + version +
                ", textureCount=" + textureCount +
                ", tableOffset=" + tableOffset +
                ", fileSize=" + fileSize +
                '}';
    }
}
