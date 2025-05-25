package org.foxesworld.cge.scene;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class ChunkFieldTypeConfigLoader {
    private Map<String, Map<String, String>> chunkFieldTypes;

    public ChunkFieldTypeConfigLoader(InputStream inputStream) throws IOException {
        Gson gson = new Gson();
        this.chunkFieldTypes = gson.fromJson(new InputStreamReader(inputStream), Map.class);
    }

    // Получаем типы полей для определённого типа чанка
    public Map<String, String> getFieldTypesForChunkType(String chunkType) {
        return chunkFieldTypes.getOrDefault(chunkType, Map.of());
    }
}

