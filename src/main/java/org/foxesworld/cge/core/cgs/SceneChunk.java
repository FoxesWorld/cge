package org.foxesworld.cge.core.cgs;

import java.nio.ByteBuffer;

public class SceneChunk {
    private final ChunkEntry entry;
    private final ByteBuffer data;

    public SceneChunk(ChunkEntry entry, ByteBuffer data) {
        this.entry = entry;
        this.data = data;
    }

    public ChunkEntry getEntry() {
        return entry;
    }

    public ByteBuffer getData() {
        return data.duplicate().order(data.order()); // безопасное повторное чтение
    }

    public void cleanup() {
        // TODO: Detach from scenegraph, physics, etc.
    }

    public int getType() {
        return entry.type().ordinal(); // или вернуть ChunkType напрямую
    }

    public int getId() {
        return entry.id();
    }
}