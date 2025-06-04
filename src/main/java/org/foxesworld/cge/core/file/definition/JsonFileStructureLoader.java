package org.foxesworld.cge.core.file.definition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class JsonFileStructureLoader implements FileStructureLoader {

    private final Map<String, FileFormatDefinition> formats;

    public JsonFileStructureLoader(InputStream jsonInputStream) throws IOException {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ByteOrder.class, new ByteOrderTypeAdapter())
                .create();

        try (InputStreamReader reader = new InputStreamReader(jsonInputStream, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, FileFormatDefinition>>() {}.getType();
            formats = gson.fromJson(reader, type);
        }
    }

    @Override
    public FileFormatDefinition loadFormatDefinition(String formatName) {
        return formats.get(formatName);
    }
}
