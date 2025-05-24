package org.foxesworld.cge.core.cgs;

import java.util.Objects;

/**
 * Описание записи чанк-таблицы CGS-файла.
 */
public record ChunkEntry(int id, long offset, int length, ChunkType type) {
    public ChunkEntry(int id, long offset, int length, int rawType) {
        this(id, offset, length, ChunkType.fromOrdinal(rawType));
    }

    public ChunkEntry {
        Objects.requireNonNull(type, "ChunkType cannot be null");
    }
}

