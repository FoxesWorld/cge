package org.foxesworld.cge.core.file.extensions.cgmat;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.definition.FieldDefinition;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public class CGMATFile extends AbstractFile<CGMATMetadata> {
    private static final Logger logger = LogManager.getLogger(CGMATFile.class);
    private final List<String> texturePaths = new java.util.ArrayList<>();

    public CGMATFile(File file, String mode) {
        super(file, mode, "CGMAT");
        setMAGIC("CMTL");
        setVERSION(1);
    }

    @Override
    public void readFileNew() throws IOException {
        if (formatDefinition == null) {
            throw new IllegalStateException("Format definition not loaded");
        }
        logger.debug("Reading CGMAT file using format: {}", formatDefinition);

        // Read header fields
        Map<String, Object> headerMap = new LinkedHashMap<>();
        for (FieldDefinition field : formatDefinition.getHeader()) {
            Object value = readField(field, headerMap);
            headerMap.put(field.getName(), value);
            logger.debug("Header field: {} = {}", field.getName(), value);
        }

        // Verify magic and version
        String magic = (String) headerMap.get("magic");
        int version = ((Number) headerMap.get("version")).intValue();
        if (!magic.equals(getMAGIC())) {
            throw new IOException("Invalid magic: expected '" + getMAGIC() + "', got '" + magic + "'");
        }
        if (version != getVERSION()) {
            throw new IOException("Unsupported version: expected " + getVERSION() + ", got " + version);
        }

        // Read properties
        Map<String, Object> propsMap = new LinkedHashMap<>();
        for (FieldDefinition field : formatDefinition.getEntry()) {
            Object value = readField(field, propsMap);
            propsMap.put(field.getName(), value);
            logger.debug("Property field: {} = {}", field.getName(), value);
        }

        // Determine texture count
        int textureCount = ((Number) propsMap.get("textureCount")).intValue();

        // Initialize metadata with header and properties
        metadata = new CGMATMetadata(new Gson().toJson(headerMap), propsMap, Collections.unmodifiableList(texturePaths));

        // Read entries
        for (int i = 0; i < textureCount; i++) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            for (FieldDefinition field : formatDefinition.getEntry()) {
                Object value = readField(field, entryMap);
                entryMap.put(field.getName(), value);
                logger.debug("Entry[{}] field: {} = {}", i, field.getName(), value);
            }
            // Use readField for path
            String path = (String) entryMap.get("path");
            texturePaths.add(path);
        }

        logger.debug("=========== CGMAT FILE READ COMPLETE ===========");
    }

    @Override
    protected void onEntryRead(Map<String, Object> entry) {
        // Not used
    }

    public List<String> getTexturePaths() {
        return Collections.unmodifiableList(texturePaths);
    }

}