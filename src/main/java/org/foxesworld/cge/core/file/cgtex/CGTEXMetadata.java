package org.foxesworld.cge.core.file.cgtex;

/**
 * Расширенный POJO для метаданных CGTEX файла.
 */
public class CGTEXMetadata {
    private final String magic;       // "CGTX"
    private final int version;
    private int textureCount;
    private long dataOffset;          // смещение начала блока с текстурами
    private long fileSize;            // общий размер файла

    public CGTEXMetadata(String magic, int version, int textureCount, long dataOffset, long fileSize) {
        this.magic = magic;
        this.version = version;
        this.textureCount = textureCount;
        this.dataOffset = dataOffset;
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

    public void setTextureCount(int textureCount) {
        this.textureCount = textureCount;
    }

    public long getDataOffset() {
        return dataOffset;
    }

    public void setDataOffset(long dataOffset) {
        this.dataOffset = dataOffset;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public String toString() {
        return "CGTEXMetadata{" +
                "magic='" + magic + '\'' +
                ", version=" + version +
                ", textureCount=" + textureCount +
                ", dataOffset=" + dataOffset +
                ", fileSize=" + fileSize +
                '}';
    }
}
