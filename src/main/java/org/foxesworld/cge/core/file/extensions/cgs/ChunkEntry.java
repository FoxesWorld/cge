package org.foxesworld.cge.core.file.extensions.cgs;

public class ChunkEntry {
    private final int id;
    private final long offset;
    private final int length;
    private final ChunkType type;

    public ChunkEntry(int id, long offset, int length, ChunkType type) {
        this.id = id;
        this.offset = offset;
        this.length = length;
        this.type = type;
    }

    public int id() { return id; }
    public long offset() { return offset; }
    public int length() { return length; }
    public ChunkType type() { return type; }
}