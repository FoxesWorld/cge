package org.foxesworld.cge.core.cgs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Перечисление типов чанков в CGS-файле, с возможностью определения специфичных параметров.
 */
public enum ChunkType {
    GEOMETRY,
    PHYSICS,
    TERRAIN,
    LIGHTING,
    MATERIALS,
    CAMERAS,
    NAVMESH,
    CUSTOM;

    public static ChunkType fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Unknown chunk type ordinal: " + ordinal);
        }
        return values()[ordinal];
    }
}