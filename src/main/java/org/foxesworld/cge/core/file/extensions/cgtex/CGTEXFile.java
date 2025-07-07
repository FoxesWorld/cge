package org.foxesworld.cge.core.file.extensions.cgtex;

import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.definition.FieldDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Handles the parsing of .cgtex files, a custom binary format for texture atlases
 * in the Calista Game Engine. This class extends {@link AbstractFile} to leverage
 * generic file reading capabilities based on a format definition.
 *
 * <h3>File Structure:</h3>
 * <ol>
 *     <li><b>Header:</b> Contains magic string, version, and the total number of textures.</li>
 *     <li><b>Texture Entries:</b> A list of entries, each describing a single texture's metadata and raw pixel data.</li>
 * </ol>
 *
 * <h3>Thread Safety:</h3>
 * This class is designed to be thread-safe for reading. The internal list of entries is synchronized.
 * The {@link #getEntries()} method returns an immutable snapshot of the texture entries,
 * making it safe to iterate over even if the file is reloaded in another thread.
 */
public class CGTEXFile extends AbstractFile<CGTEXMetadata> {
    private static final Logger logger = LoggerFactory.getLogger(CGTEXFile.class);

    // --- Format Constants ---
    private static final String CONFIG_FORMAT_NAME = "CGTEX";
    private static final String EXPECTED_MAGIC = "CGTX";
    private static final int SUPPORTED_VERSION = 1;

    private final List<TextureEntry> entries = Collections.synchronizedList(new ArrayList<>());

    /**
     * Constructs a new CGTEXFile instance.
     *
     * @param file The CGTEX file to open.
     * @param mode The mode to open the file in (e.g., "r" for read, "rw" for read/write).
     */
    public CGTEXFile(File file, String mode) {
        super(file, mode, CONFIG_FORMAT_NAME);
        setMAGIC(EXPECTED_MAGIC);
        setVERSION(SUPPORTED_VERSION);
    }

    /**
     * Reads and parses the entire CGTEX file.
     * This method reads the header, validates it, and then reads all texture entries sequentially.
     * It is designed to be resilient, logging errors for invalid entries but continuing to parse the rest of the file.
     *
     * @throws IOException           if a critical I/O error occurs or if the file header is invalid.
     * @throws IllegalStateException if the format definition required for parsing is not loaded.
     */
    @Override
    public void readFile() throws IOException {
        if (formatDefinition == null) {
            throw new IllegalStateException("CGTEX format definition not loaded. Cannot parse file.");
        }

        // --- Read Header ---
        Map<String, Object> headerMap = new LinkedHashMap<>();
        for (FieldDefinition field : formatDefinition.getHeader()) {
            Object value = readField(field, headerMap);
            headerMap.put(field.getName(), value);
        }

        // --- Validate Header ---
        String magic = (String) headerMap.get("magic");
        int version = (Integer) headerMap.get("version");
        int textureCount = (Integer) headerMap.get("textureCount");

        if (!EXPECTED_MAGIC.equals(magic)) {
            throw new IOException("Invalid CGTEX magic string. Expected '" + EXPECTED_MAGIC + "', but found '" + magic + "'.");
        }
        if (version != SUPPORTED_VERSION) {
            throw new IOException("Unsupported CGTEX file version. Expected " + SUPPORTED_VERSION + ", but found " + version + ".");
        }
        if (textureCount < 0) {
            throw new IOException("Invalid textureCount in CGTEX header: " + textureCount);
        }

        long dataOffset = getFileReader().getMappedBuffer().position();
        long fileSize = file.length();
        this.metadata = new CGTEXMetadata(magic, version, textureCount, dataOffset, fileSize);
        logger.debug("CGTEX header parsed successfully: {}", metadata);

        // --- Read Texture Entries ---
        entries.clear();
        for (int i = 0; i < textureCount; i++) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            try {
                for (FieldDefinition field : formatDefinition.getEntry()) {
                    Object value = readField(field, entryMap);
                    entryMap.put(field.getName(), value);
                }
                validateAndAddEntry(entryMap, i);
            } catch (Exception e) {
                logger.error("Failed to read or validate texture entry at index {}. Skipping. Error: {}", i, e.getMessage());
                // Log error but continue parsing the rest.
            }
        }

        logger.info("CGTEX file '{}' successfully read. Loaded {}/{} entries.", file.getName(), entries.size(), textureCount);
    }

    /**
     * Validates a raw entry map, converts it to a {@link TextureEntry}, and adds it to the list.
     *
     * @param entryMap The map of key-value pairs parsed for a single entry.
     * @param index    The index of the entry, for logging purposes.
     * @throws IOException if the entry data is critically invalid (e.g., mismatched data length).
     */
    private void validateAndAddEntry(Map<String, Object> entryMap, int index) throws IOException {
        String name = safeGetString(entryMap, "name", index);
        Integer width = safeGetInt(entryMap, "width", index);
        Integer height = safeGetInt(entryMap, "height", index);
        Byte format = safeGetByte(entryMap, "format", index);
        Integer dataLength = safeGetInt(entryMap, "dataLength", index);
        byte[] data = (byte[]) entryMap.get("data");

        // Validate required fields
        if (width == null || height == null || format == null || dataLength == null || data == null) {
            throw new IOException("Entry missing one or more required fields (width, height, format, dataLength, data).");
        }
        if (width <= 0 || height <= 0) {
            throw new IOException("Invalid texture dimensions: " + width + "x" + height);
        }
        if (data.length != dataLength) {
            throw new IOException("Mismatched data length. Header says " + dataLength + ", but actual data size is " + data.length + ".");
        }
        if (name == null || name.isBlank()) {
            logger.warn("Texture entry at index {} has a missing or empty name. Assigning a default name.", index);
            name = "UnnamedTexture_" + UUID.randomUUID().toString().substring(0, 8);
        }

        TextureEntry entry = new TextureEntry(width, height, name, format, data);
        entries.add(entry);
        logger.debug("Added texture entry [{}]: {}", index, entry);
    }

    /**
     * Returns an immutable snapshot of the parsed texture entries.
     * This method is thread-safe.
     *
     * @return A new, unmodifiable list of {@link TextureEntry}.
     */
    public List<TextureEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * Part of the AbstractFile contract but overridden by the custom logic in {@link #readFile()}.
     * This fallback simply reuses validation logic for partial or iterative reads.
     *
     * @param entry A map representing one entry, read by the parent class.
     */
    @Override
    protected void onEntryRead(Map<String, Object> entry) {
        logger.debug("onEntryRead callback invoked. Entry: {}", entry);
        try {
            // Use -1 to indicate an unknown index in logs.
            validateAndAddEntry(entry, -1);
        } catch (IOException e) {
            logger.error("Failed to process entry via onEntryRead callback: {}", e.getMessage());
        }
    }

    // --- Safe Type Casting Helpers ---
    private Integer safeGetInt(Map<String, Object> map, String key, int idx) {
        Object v = map.get(key);
        if (v instanceof Integer) {
            return (Integer) v;
        }
        if (v != null) {
            logger.warn("Entry[{}]: Field '{}' expected Integer but was {}.", idx, key, v.getClass().getSimpleName());
        }
        return null;
    }

    private String safeGetString(Map<String, Object> map, String key, int idx) {
        Object v = map.get(key);
        if (v instanceof String) {
            return (String) v;
        }
        if (v != null) {
            logger.warn("Entry[{}]: Field '{}' expected String but was {}.", idx, key, v.getClass().getSimpleName());
        }
        return null;
    }

    private Byte safeGetByte(Map<String, Object> map, String key, int idx) {
        Object v = map.get(key);
        if (v instanceof Byte) {
            return (Byte) v;
        }
        if (v != null) {
            logger.warn("Entry[{}]: Field '{}' expected Byte but was {}.", idx, key, v.getClass().getSimpleName());
        }
        return null;
    }
}