package org.foxesworld.cge.core.file.extensions.ydd;

import org.foxesworld.cge.core.file.Metadata;

public class YDDMetadata extends Metadata {
    public final String magic;
    public final long version;
    public final long rootBlockPointer;
    public final long fileSize;

    public YDDMetadata(String magic, long version, long rootBlockPointer, long fileSize) {
        this.magic = magic;
        this.version = version;
        this.rootBlockPointer = rootBlockPointer;
        this.fileSize = fileSize;
    }
}
