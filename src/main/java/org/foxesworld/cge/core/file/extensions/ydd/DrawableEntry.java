package org.foxesworld.cge.core.file.extensions.ydd;

import java.util.Map;

/**
 * Represents a drawable entry in a YDD file.
 */
public class DrawableEntry {
    public final String name;
    public final int nameHash;
    public final long drawablePointer;

    public DrawableEntry(String name, int nameHash, long drawablePointer) {
        this.name = name;
        this.nameHash = nameHash;
        this.drawablePointer = drawablePointer;
    }

    /**
     * Constructs a DrawableEntry from a map of fields.
     *
     * @param map field-value map
     * @return populated DrawableEntry
     */
    public static DrawableEntry fromMap(Map<String, Object> map) {
        String name = (String) map.get("name");
        int nameHash = (Integer) map.get("nameHash");
        long pointer = (Long) map.get("drawablePointer");
        return new DrawableEntry(name, nameHash, pointer);
    }

    @Override
    public String toString() {
        return "DrawableEntry{name='%s', nameHash=0x%x, pointer=0x%x}".formatted(name, nameHash, drawablePointer);
    }
}
