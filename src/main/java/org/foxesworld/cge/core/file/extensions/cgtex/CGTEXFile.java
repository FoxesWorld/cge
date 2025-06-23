package org.foxesworld.cge.core.file.extensions.cgtex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.definition.FieldDefinition;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * CGTEXFile handles the parsing of .cgtex files used for textures in Calista Game Engine.
 * Extends {@link AbstractFile} with support for metadata and texture entry parsing.
 */
public class CGTEXFile extends AbstractFile<CGTEXMetadata> {
    private static final Logger logger = LoggerFactory.getLogger(CGTEXFile.class);

    // Константы для конфигурирования
    private static final String CONFIG_FORMAT_NAME = "CGTEX";
    private static final String EXPECTED_MAGIC = "CGTX";
    private static final int SUPPORTED_VERSION = 1;

    private final List<TextureEntry> entries = Collections.synchronizedList(new ArrayList<>());

    /**
     * Constructs a new CGTEXFile instance.
     *
     * @param file the CGTEX file to open
     * @param mode the mode to open the file in (e.g., "r", "rw")
     */
    public CGTEXFile(File file, String mode) {
        super(file, mode, CONFIG_FORMAT_NAME);
        setMAGIC(EXPECTED_MAGIC);
        setVERSION(SUPPORTED_VERSION);
    }

    /**
     * Called when an entry map has been read; converts it to {@link TextureEntry}.
     *
     * @param entry a map representing one entry
     */
    @Override
    protected void onEntryRead(Map<String, Object> entry) {
        if (entry == null) {
            logger.warn("Null entry passed to onEntryRead");
            return;
        }
        try {
            TextureEntry texEntry = TextureEntry.fromMap(entry);
            if (texEntry != null) {
                entries.add(texEntry);
            } else {
                logger.warn("Failed to parse TextureEntry from entry map: {}", entry);
            }
        } catch (Exception ex) {
            logger.error("Exception while converting entry map to TextureEntry: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Reads the CGTEX file by interpreting the structure definition.
     *
     * @throws IOException if an error occurs while reading
     */
    @Override
    public void readFileNew() throws IOException {
        if (formatDefinition == null) {
            logger.error("Format definition not loaded");
            throw new IllegalStateException("Format definition not loaded");
        }

        Map<String, Object> headerMap = new LinkedHashMap<>();
        for (FieldDefinition field : formatDefinition.getHeader()) {
            Object value = readField(field, headerMap);
            headerMap.put(field.getName(), value);
        }

        String magic = (String) headerMap.get("magic");
        int version = (Integer) headerMap.get("version");
        int textureCount = (Integer) headerMap.get("textureCount");
        long dataOffset = fileReader.getMappedBuffer().position();
        long fileSize = file.length();

        // Проверка заголовка
        if (!EXPECTED_MAGIC.equals(magic)) {
            logger.error("Unexpected CGTEX magic: '{}', expected '{}'", magic, EXPECTED_MAGIC);
            throw new IOException("Invalid CGTEX magic: " + magic);
        }
        if (version != SUPPORTED_VERSION) {
            logger.error("Unsupported CGTEX version: {}, expected {}", version, SUPPORTED_VERSION);
            throw new IOException("Unsupported CGTEX file version: " + version);
        }
        if (textureCount < 0) {
            logger.error("Negative textureCount in CGTEX: {}", textureCount);
            throw new IOException("Negative textureCount in CGTEX header");
        }

        metadata = new CGTEXMetadata(magic, version, textureCount, dataOffset, fileSize);

        entries.clear();
        for (int i = 0; i < textureCount; i++) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            for (FieldDefinition field : formatDefinition.getEntry()) {
                Object value = readField(field, entryMap);
                entryMap.put(field.getName(), value);
            }

            try {
                validateAndAddEntry(entryMap, i);
            } catch (IOException e) {
                logger.error("Error reading texture entry at index {}: {}", i, e.getMessage());
                // Не прерываем чтение всех текстур, если только одна из них невалидна.
                // Можно раскомментировать следующую строку, если нужно прерывать полностью:
                // throw e;
            }
        }

        logger.info("CGTEX file '{}' successfully read. Entries: {}", file.getName(), entries.size());
    }

    /**
     * Validates and adds a texture entry from the parsed entry map.
     *
     * @param entryMap the entry map parsed from the file
     * @param index    index of the texture entry (for logging)
     * @throws IOException if the data is invalid
     */
    private void validateAndAddEntry(Map<String, Object> entryMap, int index) throws IOException {
        // Все проверки с логированием
        Integer width = safeGetInt(entryMap, "width", index);
        Integer height = safeGetInt(entryMap, "height", index);
        String name = safeGetString(entryMap, "name", index);
        Byte format = safeGetByte(entryMap, "format", index);
        Integer dataLength = safeGetInt(entryMap, "dataLength", index);
        byte[] data = (byte[]) entryMap.get("data");

        if (name == null || name.isEmpty()) {
            logger.warn("Empty texture name at index {}", index);
            name = "UnnamedTexture_" + index;
        }

        if (data == null || dataLength == null || data.length != dataLength) {
            logger.error("Invalid texture data at index {}: dataLength={}, actual={}", index,
                    dataLength, data == null ? -1 : data.length);
            throw new IOException("Invalid texture data at index " + index);
        }
        if (width == null || height == null || format == null) {
            logger.error("Texture entry missing required fields at index {}", index);
            throw new IOException("Texture entry missing required fields at index " + index);
        }

        TextureEntry entry = new TextureEntry(width, height, name, format, data);
        entries.add(entry);

        logger.debug("Texture[{}]: name='{}', size={}x{}, format={}", index, name, width, height, format);
    }

    private Integer safeGetInt(Map<String, Object> map, String key, int idx) {
        Object v = map.get(key);
        if (v instanceof Integer) return (Integer) v;
        logger.warn("Entry[{}]: field '{}' is not Integer or missing, was: {}", idx, key, v);
        return null;
    }

    private String safeGetString(Map<String, Object> map, String key, int idx) {
        Object v = map.get(key);
        if (v instanceof String) return (String) v;
        logger.warn("Entry[{}]: field '{}' is not String or missing, was: {}", idx, key, v);
        return null;
    }

    private Byte safeGetByte(Map<String, Object> map, String key, int idx) {
        Object v = map.get(key);
        if (v instanceof Byte) return (Byte) v;
        logger.warn("Entry[{}]: field '{}' is not Byte or missing, was: {}", idx, key, v);
        return null;
    }

    /**
     * Returns the list of parsed texture entries.
     *
     * @return list of {@link TextureEntry}
     */
    public List<TextureEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
}
