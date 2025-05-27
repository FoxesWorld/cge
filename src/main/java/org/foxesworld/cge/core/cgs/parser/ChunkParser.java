package org.foxesworld.cge.core.cgs.parser;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.cgs.SceneChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Базовый парсер чанка. Позволяет задавать описание полей чанка
 * и автоматически читать значения из {@link ByteBuffer} в порядке определения.
 */
public abstract class ChunkParser {
    private static final Logger logger = LoggerFactory.getLogger(ChunkParser.class);

    /**
     * Описание аргументов чанка: имя поля → тип для чтения.
     * Тип должен соответствовать ключу в {@link #TYPE_READERS}.
     */
    private Map<String, String> fieldDefinitions = new LinkedHashMap<>();

    /**
     * Результат чтения полей: имя поля → прочитанное значение.
     */
    private final LinkedHashMap<String, Object> fieldValues = new LinkedHashMap<>();

    /**
     * Статические функции для чтения значений из ByteBuffer по типу.
     */
    protected final Map<String, Function<ByteBuffer, Object>> TYPE_READERS = Map.of(
            "Float",   buf -> buf.getFloat(),
            "Integer", buf -> buf.getInt(),
            "Boolean", buf -> buf.get() == 1,
            "Color",   buf -> new ColorRGBA(buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat())
    );

    /**
     * Читает все заранее определённые поля из буфера в {@link #fieldValues}.
     * Буфер должен быть подготовлен (позиция, порядок байт).
     *
     * @param buf источник данных
     */
    protected void readFields(ByteBuffer buf) {
        fieldValues.clear();
        logger.debug("Reading fields from buffer ({} bytes): {}",
                buf.remaining(), dumpBufferHex(buf));

        for (var entry : fieldDefinitions.entrySet()) {
            String name = entry.getKey();
            String type = entry.getValue();
            Object value = TYPE_READERS.get(type).apply(buf);
            fieldValues.put(name, value);
            logger.debug("Field '{}' = {} ({})", name, value, type);
        }

        logger.debug("Completed reading fields: {}", fieldValues);
    }

    /**
     * Основной метод парсинга чанка.
     * Рекомендуется при реализации:
     * <ol>
     *   <li>Установить порядок байт: {@code chunk.getData().order(...)}.</li>
     *   <li>Прочитать счётчик или заголовок, если есть.</li>
     *   <li>Для каждого элемента вызвать {@link #readFields(ByteBuffer)}.</li>
     *   <li>Построить и вернуть объект {@link Spatial}.</li>
     * </ol>
     *
     * @param engine движок CGE
     * @param chunk  данные чанка
     * @param params дополнительные параметры
     * @return результирующий {@link Spatial}
     */
    public abstract Spatial parse(CalistaGameEngine engine, SceneChunk chunk, Map<String, String> params);

    /**
     * Возвращает карту определений полей (для наследников).
     */
    protected Map<String, String> getFieldDefinitions() {
        return fieldDefinitions;
    }

    /**
     * Возвращает карту значений полей после чтения.
     */
    protected Map<String, Object> getFieldValues() {
        return fieldValues;
    }

    public void setFieldDefinitions(Map<String, String> fieldDefinitions) {
        this.fieldDefinitions = fieldDefinitions;
    }

    private String dumpBufferHex(ByteBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.slice().get(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}