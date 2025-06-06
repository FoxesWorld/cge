package org.foxesworld.cge.core.file.extensions.cgmat;

import org.foxesworld.cge.core.file.Metadata;

public class CGMATMetadata extends Metadata {
    private final String magic;
    private final int version;
    private final int materialCount;
    private final long dataOffset;
    private final long fileSize;

    public CGMATMetadata(String magic, int version, int materialCount, long dataOffset, long fileSize) {
        this.magic = magic;
        this.version = version;
        this.materialCount = materialCount;
        this.dataOffset = dataOffset;
        this.fileSize = fileSize;
    }

    public String getMagic() {
        return magic;
    }

    public int getVersion() {
        return version;
    }

    public int getMaterialCount() {
        return materialCount;
    }

    public long getDataOffset() {
        return dataOffset;
    }

    public long getFileSize() {
        return fileSize;
    }

    @Override
    public String toString() {
        return "CGMATMetadata{" +
                "magic='" + magic + '\'' +
                ", version=" + version +
                ", materialCount=" + materialCount +
                ", dataOffset=" + dataOffset +
                ", fileSize=" + fileSize +
                '}';
    }
}
