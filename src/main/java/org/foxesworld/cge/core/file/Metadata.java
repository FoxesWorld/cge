package org.foxesworld.cge.core.file;

public abstract class Metadata {
    protected long tableOffset;
    protected String magic;
    protected int version;

    public long getTableOffset() {
        return tableOffset;
    }
}
