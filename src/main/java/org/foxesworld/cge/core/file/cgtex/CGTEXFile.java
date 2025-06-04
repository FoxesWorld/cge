package org.foxesworld.cge.core.file.cgtex;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.AbstractFile;
import org.foxesworld.cge.core.file.FileReader;
import org.foxesworld.cge.core.file.cgs.CGSFile;
import org.foxesworld.cge.core.file.definition.FieldDefinition;
import org.foxesworld.cge.core.file.definition.FileFormatDefinition;
import org.foxesworld.cge.core.file.definition.FileStructureLoader;
import org.foxesworld.cge.core.file.definition.JsonFileStructureLoader;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CGTEXFile extends AbstractFile {

    private final List<TextureEntry> entries = new ArrayList<>();
    private CGTEXMetadata metadata;
    private static final Logger logger = LogManager.getLogger(CGTEXFile.class);

    public CGTEXFile(File file, String mode) {
        super(file, mode);
        setMAGIC("CGTX");
        setVERSION(1);

        try {
            FileStructureLoader loader = new JsonFileStructureLoader(
                    CGSFile.class.getClassLoader().getResourceAsStream("cgtex.json")
            );
            FileFormatDefinition format = loader.loadFormatDefinition("CGTEX");
            setFormatDefinition(format);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load CGTEX format", e);
        }
    }

    @Override
    protected void onEntryRead(Map<String, Object> entry) {
        entries.add(TextureEntry.fromMap(entry));
    }

    @Override
    public FileReader readFile() {
        return null;
    }

    @Override
    public void readFileNew() throws IOException {
        if (formatDefinition == null) {
            throw new IllegalStateException("Format definition not loaded");
        }

        logger.debug("Reading file using format: {}", formatDefinition);

        Map<String, Object> headerMap = new LinkedHashMap<>();
        for (FieldDefinition field : formatDefinition.getHeader()) {
            Object value = readField(field, headerMap);
            headerMap.put(field.getName(), value);
            logger.debug("Header field: {} = {}", field.getName(), value);
        }
        logger.debug("Header values: {}", headerMap);

        String magic       = (String) headerMap.get("magic");
        int version        = (Integer) headerMap.get("version");
        int textureCount   = (Integer) headerMap.get("textureCount");
        long dataOffset    = getRaf().getFilePointer();
        long fileSize      = getFile().length();

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

            int width      = (Integer) entryMap.get("width");
            int height     = (Integer) entryMap.get("height");
            String name    = (String)  entryMap.get("name");
            byte format    = (Byte)    entryMap.get("format");
            int dataLength = (Integer) entryMap.get("dataLength");
            byte[] data    = (byte[])  entryMap.get("data");

            if (name == null || name.isEmpty()) {
                logger.warn("Empty texture name at index {}", i);
                name = "UnnamedTexture_" + i;
            }
            if (dataLength < 0) {
                throw new IOException("Invalid dataLength (" + dataLength + ") for texture index " + i);
            }

            TextureEntry entry = new TextureEntry(width, height, name, format, data);
            entries.add(entry);

            logger.debug("Texture[{}]: name={} size={}x{} format={}",
                    i, entry.getName(), entry.getWidth(), entry.getHeight(), entry.getFormat());
        }
        logger.debug("================= CGTEX FILE READ END =================");
    }

    public List<TextureEntry> getEntries() {
        return entries;
    }

    public CGTEXMetadata getMetadata() {
        return metadata;
    }
}
