package org.foxesworld.cge.core.file.extensions.cgs;


/**
 * Enum for explicit chunk types in the CGS format.
 */
public enum ChunkType {
    HEIGHTMAP(1),
    OBJECTS(2),
    PLANES(3),
    UNKNOWN(-1);

    private final int typeValue;

    ChunkType(int typeValue) {
        this.typeValue = typeValue;
    }

    public int getTypeValue() {
        return typeValue;
    }

    public static ChunkType fromInt(int typeValue) {
        for (ChunkType t : values()) {
            if (t.typeValue == typeValue) return t;
        }
        return UNKNOWN;
    }
}