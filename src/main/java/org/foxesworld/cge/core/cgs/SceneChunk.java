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
        return data;
    }

    public void cleanup() {
        // Detach from scenegraph, physics, etc.
    }

    public int getType() {
        return entry.type();
    }

    public int getId() {
        return entry.id();
    }
}
