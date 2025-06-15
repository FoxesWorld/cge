package org.foxesworld.cge.tmp;

import org.foxesworld.cge.core.file.Metadata;

import java.util.HashMap;
import java.util.Map;

public class MapBasedMetadata extends Metadata {
    protected final Map<String, Object> values = new HashMap<>();

    public void put(String key, Object value) {
        values.put(key, value);
    }

    public Object get(String path) {
        String[] parts = path.split("->");
        Object current = values;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof Metadata meta) {
                current = meta.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    public long getTableOffset() {
        return tableOffset;
    }

    @Override
    public String toString() {
        return "MapBasedMetadata{" +
                "magic='" + magic + '\'' +
                ", version=" + version +
                ", tableOffset=" + tableOffset +
                ", values=" + values +
                '}';
    }
}
