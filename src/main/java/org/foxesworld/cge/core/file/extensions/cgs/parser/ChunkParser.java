package org.foxesworld.cge.core.file.extensions.cgs.parser;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.file.extensions.cgs.SceneChunk;
import org.foxesworld.cge.core.file.extensions.cgs.ChunkFieldTypeConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Abstract base class for parsing CGS chunk data.
 * Provides automatic field reading from a ByteBuffer based on type definitions.
 */
public abstract class ChunkParser {
    private static final HexFormat HEX = HexFormat.of();
    private static final Logger logger = LoggerFactory.getLogger(ChunkParser.class);

    /** Field definitions: field name → field type. The type must match a key in {@link #TYPE_READERS}. */
    private Map<String, String> fieldDefinitions = new LinkedHashMap<>();

    /** Optional: per-chunk type field configuration if used by subclass. */
    protected Map<String, Map<String, String>> fieldTypes;

    /** Resulting parsed values: field name → value. */
    private final LinkedHashMap<String, Object> fieldValues = new LinkedHashMap<>();

    /** Type readers: functions that read a value from ByteBuffer based on a declared type. */
    protected final Map<String, Function<ByteBuffer, Object>> TYPE_READERS = Map.of(
            "Float", ByteBuffer::getFloat,
            "Integer", ByteBuffer::getInt,
            "Boolean",  buf -> buf.get() == 1,
            "Vector3f", buf -> new com.jme3.math.Vector3f(buf.getFloat(), buf.getFloat(), buf.getFloat()),
            "Color",    buf -> {
                int r = buf.get() & 0xFF;
                int g = buf.get() & 0xFF;
                int b = buf.get() & 0xFF;
                int a = buf.get() & 0xFF;
                return new ColorRGBA(r / 255f, g / 255f, b / 255f, a / 255f);
            },
            "String",   buf -> {
                int len = buf.getShort() & 0xFFFF;
                byte[] bytes = new byte[len];
                buf.get(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            }
    );

    /**
     * Reads all predefined fields from the buffer and stores them in {@link #fieldValues}.
     * The order of reading follows the order in {@link #fieldDefinitions}.
     *
     * @param buf input ByteBuffer (should be positioned and ordered correctly)
     */
    protected void readFields(ByteBuffer buf) {
        fieldValues.clear();
        logger.debug("Reading fields from buffer ({} bytes): {}", buf.remaining(), dumpBufferHex(buf));

        for (var entry : fieldDefinitions.entrySet()) {
            String name = entry.getKey();
            String type = entry.getValue();

            Function<ByteBuffer, Object> reader = TYPE_READERS.get(type);
            if (reader == null) {
                logger.error("Unknown field type: '{}' for field '{}'", type, name);
                continue;
            }

            Object value;
            try {
                value = reader.apply(buf);
            } catch (Exception e) {
                logger.error("Failed to read field '{}': {}", name, e.toString());
                value = null;
            }

            fieldValues.put(name, value);
            logger.debug("Field '{}' = {} ({})", name, value, type);
        }

        logger.debug("Completed reading fields: {}", fieldValues);
    }

    /**
     * Returns the type identifier of the given chunk buffer.
     * Used to dynamically select parsing logic.
     *
     * @param buf chunk data buffer
     * @return chunk type identifier
     */
    protected abstract String getType(ByteBuffer buf);

    /**
     * Main method to parse a SceneChunk into a Spatial object.
     * Recommended steps:
     * <ol>
     *   <li>Set buffer byte order</li>
     *   <li>Read count/header if needed</li>
     *   <li>Call {@link #readFields(ByteBuffer)} for each item</li>
     *   <li>Construct and return a Spatial</li>
     * </ol>
     *
     * @param engine           reference to Calista engine
     * @param chunk            parsed chunk with raw buffer data
     * @param typeConfigLoader field type configs, if needed
     * @return constructed spatial object
     */
    public abstract Spatial parse(CalistaGameEngine engine, SceneChunk chunk, ChunkFieldTypeConfigLoader typeConfigLoader);

    /** Returns the field definitions map (field name → type). */
    protected Map<String, String> getFieldDefinitions() {
        return fieldDefinitions;
    }

    /** Returns the map of parsed field values (after calling {@link #readFields(ByteBuffer)}). */
    protected Map<String, Object> getFieldValues() {
        return fieldValues;
    }

    /** Sets the field definitions used in parsing. */
    public void setFieldDefinitions(Map<String, String> fieldDefinitions) {
        this.fieldDefinitions = fieldDefinitions;
    }

    /** Dumps remaining buffer as a hex string for debugging. */
    protected String dumpBufferHex(ByteBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.slice().get(bytes);
        return HEX.formatHex(bytes);
    }

    /** Reads a short-prefixed UTF-8 string safely with bounds checking. */
    protected String readUTF(ByteBuffer buf) {
        int startPos = buf.position();

        if (buf.remaining() < 2) {
            logger.warn("readUTF: Not enough bytes to read length (pos={}, remaining={})", startPos, buf.remaining());
            return "";
        }

        int len = buf.getShort() & 0xFFFF;

        if (buf.remaining() < len) {
            logger.warn("readUTF: Not enough data to read string of length {} (pos={}, remaining={}, hex={})",
                    len, startPos, buf.remaining(), dumpBufferHex(buf));
            return "";
        }

        byte[] bytes = new byte[len];
        buf.get(bytes);

        String result = new String(bytes, StandardCharsets.UTF_8);
        logger.debug("readUTF: Read string='{}' (length={}, pos={}, newPos={})",
                result, len, startPos, buf.position());

        return result;
    }
}
