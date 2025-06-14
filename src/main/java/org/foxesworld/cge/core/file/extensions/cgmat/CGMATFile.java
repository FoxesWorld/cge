package org.foxesworld.cge.core.file.extensions.cgmat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.definition.FieldDefinition;
import org.foxesworld.cge.core.file.definition.FileFormatDefinition;
import org.foxesworld.cge.core.file.definition.FileStructureLoader;
import org.foxesworld.cge.core.file.definition.JsonFileStructureLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CGMATFile extends AbstractFile<CGMATMetadata> {
    private static final Logger logger = LogManager.getLogger(CGMATFile.class);
    private final List<MaterialEntry> entries = new ArrayList<>();

    public CGMATFile(File file, String mode) throws IOException {
        super(file, mode, "CGMAT");
        setMAGIC("CGMT");
        setVERSION(1);
        readFileNew();
    }

    @Override
    protected void onEntryRead(Map<String, Object> entry) {
        entries.add(MaterialEntry.fromMap(entry));
    }

    @Override
    public void readFileNew() throws IOException {
        FileFormatDefinition def = getFormatDefinition();
        if (def == null) {
            throw new IllegalStateException("Format definition not loaded");
        }

        logger.debug("Reading CGMAT file using format: {}", def);

        Map<String, Object> headerMap = new LinkedHashMap<>();
        for (FieldDefinition field : def.getHeader()) {
            Object value = readField(field, headerMap);
            headerMap.put(field.getName(), value);
            logger.debug("Header field: {} = {}", field.getName(), value);
        }

        String magic = (String) headerMap.get("magic");
        int version = (Integer) headerMap.get("version");
        int materialCount = (Integer) headerMap.get("materialCount");
        long dataOffset = raf.getFilePointer();
        long fileSize = getFile().length();

        metadata = new CGMATMetadata(magic, version, materialCount, dataOffset, fileSize);

        for (int i = 0; i < materialCount; i++) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            for (FieldDefinition field : def.getEntry()) {
                Object value = readField(field, entryMap);
                entryMap.put(field.getName(), value);
                if (!"data".equals(field.getName())) {
                    logger.debug("Entry[{}] field: {} = {}", i, field.getName(), value);
                }
            }
            try {
                validateAndAddEntry(entryMap, i);
            } catch (IOException e) {
                logger.error("Error reading material entry at index {}: {}", i, e.getMessage());
                throw e;
            }
        }

        logger.debug("=========== CGMAT FILE READ COMPLETE ===========");
    }

    private void validateAndAddEntry(Map<String, Object> entryMap, int index) throws IOException {
        String name = (String) entryMap.get("name");
        int paramCount = (Integer) entryMap.get("paramCount");
        byte[] data = (byte[]) entryMap.get("data");

        if (name == null || name.isEmpty()) {
            logger.warn("Empty material name at index {}", index);
            name = "UnnamedMaterial_" + index;
        }

        if (data == null || data.length == 0) {
            throw new IOException("Invalid material data at index " + index);
        }

        MaterialEntry entry = new MaterialEntry(name, paramCount, data);
        entries.add(entry);

        logger.debug("Material[{}]: name='{}', paramCount={}, dataLength={}",
                index, name, paramCount, data.length);
    }

    public List<MaterialEntry> getEntries() {
        return entries;
    }
}
