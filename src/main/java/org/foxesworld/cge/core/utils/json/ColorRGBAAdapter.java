package org.foxesworld.cge.core.utils.json;

import com.google.gson.*;
import com.jme3.math.ColorRGBA;

import java.lang.reflect.Type;

/**
 * Адаптер для сериализации и десериализации объектов ColorRGBA
 * в JSON-формате с помощью библиотеки GSON.
 *
 * Поддерживает формат:
 * [R, G, B, A]
 * Пример: [1.0, 0.5, 0.2, 1.0]
 */
public class ColorRGBAAdapter implements JsonSerializer<ColorRGBA>, JsonDeserializer<ColorRGBA> {

    /**
     * Сериализация ColorRGBA в JSON-массив из 4 чисел: [R, G, B, A].
     *
     * @param src     объект ColorRGBA
     * @param typeOfSrc тип объекта
     * @param context сериализационный контекст
     * @return JsonArray [r, g, b, a]
     */
    @Override
    public JsonElement serialize(ColorRGBA src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray array = new JsonArray();
        array.add(src.r);
        array.add(src.g);
        array.add(src.b);
        array.add(src.a);
        return array;
    }

    /**
     * Десериализация JSON-массива [r, g, b, a] в объект ColorRGBA.
     *
     * @param json JSON-массив
     * @param typeOfT тип объекта
     * @param context десериализационный контекст
     * @return объект ColorRGBA
     * @throws JsonParseException если структура некорректна
     */
    @Override
    public ColorRGBA deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (!json.isJsonArray()) {
            throw new JsonParseException("ColorRGBA must be a JSON array");
        }

        JsonArray array = json.getAsJsonArray();
        if (array.size() != 4) {
            throw new JsonParseException("ColorRGBA array must contain exactly 4 elements (r, g, b, a)");
        }

        float r = array.get(0).getAsFloat();
        float g = array.get(1).getAsFloat();
        float b = array.get(2).getAsFloat();
        float a = array.get(3).getAsFloat();

        return new ColorRGBA(r, g, b, a);
    }
}
