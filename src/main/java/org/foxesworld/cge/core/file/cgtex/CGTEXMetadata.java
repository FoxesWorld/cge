package org.foxesworld.cge.core.file.cgtex;

import org.foxesworld.cge.core.file.Metadata;

/**
 * Расширенный POJO для метаданных CGTEX файла.
 */
public class CGTEXMetadata extends Metadata {
    private final String magic;
    private final int version;
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
