package org.foxesworld.cge.core.file.cgs;


public enum ChunkType {
    //GEOMETRY,
    //PHYSICS,
    TERRAIN,
    LIGHTING;
    //MATERIALS,
    //CAMERAS,
    //NAVMESH,
    //CUSTOM;

    public static ChunkType fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Unknown chunk type ordinal: " + ordinal);
        }
        return values()[ordinal];
    }
}