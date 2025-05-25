package org.foxesworld.cge.core.cgs.parser;

import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.cgs.SceneChunk;

import java.util.Map;
import java.util.HashMap;
import java.util.function.Function;

public abstract class ChunkParser {

    // Абстрактный метод, который должны реализовать все парсеры
    public abstract Spatial parse(CalistaGameEngine calistaGameEngine, SceneChunk chunk, Map<String, String> params);

    /**
     * Универсальный метод для парсинга аргумента с проверкой его типа.
     *
     * @param name    имя параметра
     * @param params  карта параметров
     * @param <T>     ожидаемый тип
     * @return значение аргумента, приведенное к типу T
     */
    protected <T> T parseArg(String name, Map<String, String> params) {
        String value = params.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Argument '" + name + "' is missing in parameters.");
        }

        // Карта типов с функциями для их обработки
        Map<String, Function<String, Object>> typeParsers = createTypeParsers();

        // Определяем тип аргумента, передаем его в соответствующий парсер
        return (T) typeParsers.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name) || isCompatibleType(entry.getKey(), name))
                .map(entry -> entry.getValue().apply(name))
                .findFirst()
                .orElse(name); // Если не нашли тип, возвращаем строку как есть
    }

    /**
     * Функция для создания мапы с парсерами для разных типов.
     * @return мапа с парсерами
     */
    private Map<String, Function<String, Object>> createTypeParsers() {
        Map<String, Function<String, Object>> parsers = new HashMap<>();

        parsers.put("Boolean", value -> Boolean.valueOf(value));
        parsers.put("Integer", value -> Integer.valueOf(value));

        // Можно добавить дополнительные парсеры для других типов, например:
        parsers.put("Float", value -> Float.valueOf(value));
        parsers.put("Double", value -> Double.valueOf(value));

        // Для строк оставляем их как есть
        parsers.put("String", value -> value);

        return parsers;
    }

    /**
     * Проверка совместимости типов, если парсер не может точно определить тип.
     * Например, строка может быть целым числом или булевым значением.
     */
    private boolean isCompatibleType(String key, String value) {
        if ("Boolean".equalsIgnoreCase(key)) {
            return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
        }
        if ("Integer".equalsIgnoreCase(key)) {
            try {
                Integer.parseInt(value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
}
