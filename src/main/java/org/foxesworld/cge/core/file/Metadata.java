package org.foxesworld.cge.core.file;

import java.lang.reflect.Field;

public abstract class Metadata {
    protected long tableOffset;
    protected String magic;
    protected int version;

    public long getTableOffset() {
        return tableOffset;
    }

    public void setTableOffset(long tableOffset) {
        this.tableOffset = tableOffset;
    }

    public Object get(String path) {
        try {
            String[] parts = path.split("->");
            Object current = this;
            for (String part : parts) {
                Field field = current.getClass().getDeclaredField(part);
                field.setAccessible(true);
                current = field.get(current);
                if (current == null) {
                    return null;
                }
            }
            return current;
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve metadata path: " + path, e);
        }
    }
}
