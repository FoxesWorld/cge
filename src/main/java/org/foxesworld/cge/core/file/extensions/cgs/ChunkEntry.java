package org.foxesworld.cge.core.file.extensions.cgs;

import java.util.Objects;

public record ChunkEntry(int id, long offset, int length, ChunkType type) {
    public ChunkEntry(int id, long offset, int length, int rawType) {
        this(id, offset, length, ChunkType.fromOrdinal(rawType));
    }

    public ChunkEntry {
        Objects.requireNonNull(type, "ChunkType cannot be null");
    }
}

