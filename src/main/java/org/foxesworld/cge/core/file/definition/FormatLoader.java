package org.foxesworld.cge.core.file.definition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.foxesworld.cge.core.file.definition.FileFormatDefinition;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class FormatLoader {
    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Загружает JSON-описание формата по указанному пути.
     *
     * @param jsonPath путь к JSON-файлу (например, "resources/cgtdef.json")
     * @return объект FileFormatDefinition
     * @throws IOException если файл не найден или не удалось прочитать
     */
    public static FileFormatDefinition loadFromJson(String jsonPath) {
        try (Reader reader = new FileReader(jsonPath)) {
            return GSON.fromJson(reader, FileFormatDefinition.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}