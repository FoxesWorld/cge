package org.foxesworld.cge.core.file.extensions.cgtex;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.definition.FieldDefinition;
import org.foxesworld.cge.core.file.definition.FileFormatDefinition;
import org.foxesworld.cge.core.file.definition.FileStructureLoader;
import org.foxesworld.cge.core.file.definition.JsonFileStructureLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * CGTEXFile handles the parsing of .cgtex files used for textures in Calista Game Engine.
 * Extends {@link AbstractFile} with support for metadata and texture entry parsing.
 */
public class CGTEXFile extends AbstractFile<CGTEXMetadata> {
    private static final Logger logger = LogManager.getLogger(CGTEXFile.class);
    private final List<TextureEntry> entries = new ArrayList<>();

    /**
     * Constructs a new CGTEXFile instance.
     *
     * @param file the CGTEX file to open
     * @param mode the mode to open the file in (e.g., "r", "rw")
     */
    public CGTEXFile(File file, String mode) {
        super(file, mode);
        setMAGIC("CGTX");
        setVERSION(1);
        loadFormatDefinition();
    }

    /**
     * Loads the file format definition from the JSON descriptor.
     */
    private void loadFormatDefinition() {
        try {
            FileStructureLoader loader = new JsonFileStructureLoader(
                    CGTEXFile.class.getClassLoader().getResourceAsStream("cgtex.json")
            );
            FileFormatDefinition format = loader.loadFormatDefinition("CGTEX");
            setFormatDefinition(format);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load CGTEX format", e);
        }
    }

    /**
     * Called when an entry map has been read; converts it to {@link TextureEntry}.
     *
     * @param entry a map representing one entry
     */
    @Override
    protected void onEntryRead(Map<String, Object> entry) {
        entries.add(TextureEntry.fromMap(entry));
    }

    /**
     * Reads the CGTEX file by interpreting the structure definition.
     *
     * @throws IOException if an error occurs while reading
     */
    @Override
    public void readFileNew() throws IOException {
        if (formatDefinition == null) {
            throw new IllegalStateException("Format definition not loaded");
        }

        logger.debug("Reading CGTEX file using format: {}", formatDefinition);

        Map<String, Object> headerMap = new LinkedHashMap<>();
        for (FieldDefinition field : formatDefinition.getHeader()) {
            Object value = readField(field, headerMap);
            headerMap.put(field.getName(), value);
            logger.debug("Header field: {} = {}", field.getName(), value);
        }

        String magic = (String) headerMap.get("magic");
        int version = (Integer) headerMap.get("version");
        int textureCount = (Integer) headerMap.get("textureCount");
        long dataOffset = getFileReader().getRaf().getFilePointer();
        long fileSize = getFile().length();

        metadata = new CGTEXMetadata(magic, version, textureCount, dataOffset, fileSize);

        for (int i = 0; i < textureCount; i++) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            for (FieldDefinition field : formatDefinition.getEntry()) {
                Object value = readField(field, entryMap);
                entryMap.put(field.getName(), value);
                if (!"data".equals(field.getName())) {
                    logger.debug("Entry[{}] field: {} = {}", i, field.getName(), value);
                }
            }

            try {
                validateAndAddEntry(entryMap, i);
            } catch (IOException e) {
                logger.error("Error reading texture entry at index {}: {}", i, e.getMessage());
                throw e;
            }
        }

        logger.debug("=========== CGTEX FILE READ COMPLETE ===========");
    }

    /**
     * Validates and adds a texture entry from the parsed entry map.
     *
     * @param entryMap the entry map parsed from the file
     * @param index    index of the texture entry (for logging)
     * @throws IOException if the data is invalid
     */
    private void validateAndAddEntry(Map<String, Object> entryMap, int index) throws IOException {
        int width = (Integer) entryMap.get("width");
        int height = (Integer) entryMap.get("height");
        String name = (String) entryMap.get("name");
        byte format = (Byte) entryMap.get("format");
        int dataLength = (Integer) entryMap.get("dataLength");
        byte[] data = (byte[]) entryMap.get("data");

        if (name == null || name.isEmpty()) {
            logger.warn("Empty texture name at index {}", index);
            name = "UnnamedTexture_" + index;
        }

        if (data == null || data.length != dataLength) {
            throw new IOException("Invalid texture data at index " + index);
        }

        TextureEntry entry = new TextureEntry(width, height, name, format, data);
        entries.add(entry);

        logger.debug("Texture[{}]: name='{}', size={}x{}, format={}",
                index, name, width, height, format);
    }

    /**
     * Returns the list of parsed texture entries.
     *
     * @return list of {@link TextureEntry}
     */
    public List<TextureEntry> getEntries() {
        return entries;
    }
}
