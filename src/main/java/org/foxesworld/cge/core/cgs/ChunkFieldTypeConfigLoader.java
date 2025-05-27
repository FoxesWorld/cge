package org.foxesworld.cge.core.cgs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class ChunkFieldTypeConfigLoader {
    private final String generalSection = "general";

    private static final Logger logger = LoggerFactory.getLogger(ChunkFieldTypeConfigLoader.class);
    private final Map<String, Map<String, Map<String, String>>> chunkFieldTypes;

    public ChunkFieldTypeConfigLoader(InputStream inputStream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
            Type type = new TypeToken<Map<String, Map<String, Map<String, String>>>>() {}.getType();
            this.chunkFieldTypes = new Gson().fromJson(reader, type);
        }
        logger.info("Loaded {} chunk subtypes", getTotalTypeCount());
    }

    public Set<String> getParentTypes() {
        return chunkFieldTypes.keySet();
    }

    public Set<String> getChildTypes(String parent) {
        Map<String, Map<String, String>> childMap = chunkFieldTypes.get(parent);
        if (childMap == null) return Set.of();
        return childMap.keySet().stream()
                .filter(key -> !key.equals(generalSection))
                .collect(Collectors.toSet());
    }

    public Map<String, String> getAttributes(String parent, String child) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Map<String, String>> childMap = chunkFieldTypes.get(parent);
        if (childMap == null) return result;

        Map<String, String> common = childMap.get(generalSection);
        if (common != null) result.putAll(common);

        Map<String, String> specific = childMap.get(child);
        if (specific != null) result.putAll(specific);

        return result;
    }

    public Map<String, Map<String, String>> getSubTypesForChunkType(String chunkType) {
        Map<String, Map<String, String>> original = chunkFieldTypes.getOrDefault(chunkType, Collections.emptyMap());
        Map<String, String> common = original.getOrDefault(generalSection, Collections.emptyMap());

        Map<String, Map<String, String>> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : original.entrySet()) {
            String key = entry.getKey();
            if (key.equals("__common__")) continue;

            Map<String, String> combined = new LinkedHashMap<>(common);
            combined.putAll(entry.getValue());
            merged.put(key, combined);
        }
        return merged;
    }

    public Map<String, String> getFieldTypesForChunkSubType(String chunkType, String subType) {
        return getAttributes(chunkType, subType);
    }

    public int getTotalTypeCount() {
        return chunkFieldTypes.values().stream()
                .mapToInt(map -> (int) map.keySet().stream().filter(k -> !k.equals(generalSection)).count())
                .sum();
    }
}
