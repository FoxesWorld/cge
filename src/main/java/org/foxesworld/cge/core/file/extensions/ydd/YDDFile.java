package org.foxesworld.cge.core.file.extensions.ydd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.definition.FieldDefinition;
import org.foxesworld.cge.core.file.definition.FileFormatDefinition;
import org.foxesworld.cge.core.file.definition.FileStructureLoader;
import org.foxesworld.cge.core.file.definition.JsonFileStructureLoader;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * YDDFile parses .ydd (YDR Drawable Dictionary) files used in RAGE engine.
 * Based on declarative structure loaded from JSON format.
 */
public class YDDFile extends AbstractFile<YDDMetadata> {
    private static final Logger logger = LogManager.getLogger(YDDFile.class);
    private final List<DrawableEntry> drawables = new ArrayList<>();

    /**
     * Constructs a YDD file parser.
     *
     * @param file the YDD file
     * @param mode file mode (e.g. "r")
     */
    public YDDFile(File file, String mode) {
        super(file, mode, "YDD");
        setMAGIC("RSC7");
        setVERSION(1); // or appropriate version detection
    }

    /**
     * Called when an entry map has been read; converts it to DrawableEntry.
     *
     * @param entry a map representing a drawable
     */
    @Override
    protected void onEntryRead(Map<String, Object> entry) {
        drawables.add(DrawableEntry.fromMap(entry));
    }

    /**
     * Reads the YDD file using the defined JSON format structure.
     *
     * @throws IOException on I/O error
     */
    @Override
    public void readFileNew() throws IOException {
        if (formatDefinition == null) {
            throw new IllegalStateException("Format definition not loaded");
        }

        logger.debug("Reading YDD file using format: {}", formatDefinition);

        Map<String, Object> headerMap = new LinkedHashMap<>();
        for (FieldDefinition field : formatDefinition.getHeader()) {
            Object value = readField(field, headerMap);
            headerMap.put(field.getName(), value);
            logger.debug("Header field: {} = {}", field.getName(), value);
        }

        String magic = (String) headerMap.get("magic");
        int version = (Integer) headerMap.get("version");
        long fileSize = getFile().length();
        long rootBlock = (Long) headerMap.get("structurePointer");

        metadata = new YDDMetadata(magic, version, rootBlock, fileSize);

        for (int i = 0; i < formatDefinition.getEntry().size(); i++) {
            FieldDefinition field = formatDefinition.getEntry().get(i);
            if ("drawables".equals(field.getName())) {
                List<Map<String, Object>> drawableList = (List<Map<String, Object>>) readField(field, new HashMap<>());
                for (int j = 0; j < drawableList.size(); j++) {
                    Map<String, Object> drawable = drawableList.get(j);
                    validateAndAddDrawable(drawable, j);
                }
            } else {
                readField(field, new HashMap<>());
            }
        }

        logger.debug("=========== YDD FILE READ COMPLETE ===========");
    }

    /**
     * Validates and adds a drawable entry.
     *
     * @param entry drawable entry map
     * @param index index of entry
     * @throws IOException if entry is invalid
     */
    private void validateAndAddDrawable(Map<String, Object> entry, int index) throws IOException {
        String name = (String) entry.get("name");
        int nameHash = (Integer) entry.get("nameHash");
        long pointer = (Long) entry.get("drawablePointer");

        if (pointer == 0) {
            throw new IOException("Invalid drawable pointer at index " + index);
        }

        if (name == null || name.isBlank()) {
            name = "UnnamedModel_" + index;
            logger.warn("Drawable at index {} has no name, using '{}'", index, name);
        }

        DrawableEntry drawable = new DrawableEntry(name, nameHash, pointer);
        drawables.add(drawable);
        logger.debug("Drawable[{}]: name='{}', nameHash=0x{}, pointer=0x{}", index, name, Integer.toHexString(nameHash), Long.toHexString(pointer));
    }


    /**
     * Returns the list of parsed drawables.
     *
     * @return list of {@link DrawableEntry}
     */
    public List<DrawableEntry> getDrawables() {
        return drawables;
    }
}
