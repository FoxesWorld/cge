package org.foxesworld.cge.core.cgs;

public enum CGSChunkType {
    GEOMETRY(0),
    PHYSICS(1),
    NAVIGATION(2),
    SCRIPT(3);

    private final int id;

    CGSChunkType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static CGSChunkType fromId(int id) {
        for (CGSChunkType type : values()) {
            if (type.id == id) return type;
        }
        throw new IllegalArgumentException("Unknown chunk type: " + id);
    }
}
