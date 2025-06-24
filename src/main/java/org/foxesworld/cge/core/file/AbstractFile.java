package org.foxesworld.cge.core.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.definition.FieldDefinition;
import org.foxesworld.cge.core.file.definition.FileFormatDefinition;
import org.foxesworld.cge.core.file.definition.FileStructureLoader;
import org.foxesworld.cge.core.file.definition.JsonFileStructureLoader;
import org.foxesworld.cge.core.file.extensions.cgs.CGSFile;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

/**
 * Abstract base class for file handling with format definition support.
 * @param <M> Metadata type.
 */
public abstract class AbstractFile<M extends Metadata> implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger(AbstractFile.class);
    protected final HexFormat HEX = HexFormat.of();
    protected M metadata;
    protected final File file;
    protected FileFormatDefinition formatDefinition;

    private String MAGIC;
    private int VERSION;
    private ByteOrder BYTE_ORDER = LITTLE_ENDIAN;

    protected final FileReader fileReader;

    /**
     * Constructs a new AbstractFile with the given {@code File} and mode.
     *
     * @param file the file to open
     * @param mode the access mode (e.g., "r", "rw")
     * @param formatDefinition name  of json-structure
     */
    protected AbstractFile(File file, String mode, String formatDefinition) {
        this.file = file;
        this.fileReader = new FileReader(this, mode);
        this.loadFormatDefinition(formatDefinition);
    }

    private void loadFormatDefinition(String definition) {
        try {
            FileStructureLoader loader = new JsonFileStructureLoader(
                    Objects.requireNonNull(
                            CGSFile.class.getClassLoader().getResourceAsStream(
                                    "config/fileformats/" + definition.toLowerCase() + ".json"
                            ), "Config file not found: " + definition
                    )
            );
            setFormatDefinition(loader.loadFormatDefinition(definition));
        } catch (IOException | NullPointerException e) {
            logger.error("Failed to load {} format: {}", definition, e.getMessage(), e);
            throw new FileFormatException("Failed to load " + definition + " format", e);
        }
    }

    /**
     * Checks and validates file header (magic/version).
     */
    @Deprecated
    protected void verifyHeader() {
        fileReader.seek(0);
        byte[] magicBytes = fileReader.readBytes(MAGIC.length());
        String fileMagic = new String(magicBytes, StandardCharsets.US_ASCII);
        if (!fileMagic.equals(MAGIC)) {
            logger.error("Invalid file magic: expected '{}', got '{}'", MAGIC, fileMagic);
            throw new FileFormatException("Invalid file magic: expected '" + MAGIC + "', got '" + fileMagic + "'");
        }
        int ver = Short.toUnsignedInt(fileReader.readShort());
        if (ver != VERSION) {
            logger.error("Unsupported version: expected {}, got {}", VERSION, ver);
            throw new FileFormatException("Unsupported version: expected " + VERSION + ", got " + ver);
        }
        fileReader.seek(fileReader.position() + 2); // skip 2 bytes
    }

    protected abstract void readFileNew() throws IOException;
    protected abstract void onEntryRead(Map<String, Object> entry);

    /**
     * Reads a field from the file according to its definition.
     */
    protected Object readField(FieldDefinition field, Map<String, Object> context) throws IOException {
        BYTE_ORDER = field.getByteOrder();
        fileReader.setByteOrder(BYTE_ORDER);
        switch (field.getType()) {
            case "byte":      return fileReader.readByte();
            case "ushort":    return Short.toUnsignedInt(fileReader.readShort());
            case "int":
            case "int32":     return readInt(field);
            case "uint32":    return Integer.toUnsignedLong(readInt(field));
            case "long":
            case "int64":     return fileReader.readLong();
            case "uint64": {
                long signed = fileReader.readLong();
                BigInteger ui64 = BigInteger.valueOf(signed);
                if (signed < 0) ui64 = ui64.add(BigInteger.ONE.shiftLeft(64));
                return ui64;
            }
            case "float":     return fileReader.readFloat();
            case "double":    return fileReader.readDouble();
            case "length":    return fileReader.size();
            case "string": {
                Integer length = field.getLength();
                if (length == null) {
                    Object fieldLen = context.get(field.getLengthField());
                    if (fieldLen == null)
                        throw new FileFormatException("String length field not found in context: " + field.getLengthField());
                    length = (int) fieldLen;
                }
                return fileReader.readString(length);
            }
            case "byteArray": {
                Integer arrLen = field.getLength();
                if (arrLen == null) {
                    Object fieldLen = context.get(field.getLengthField());
                    if (fieldLen == null)
                        throw new FileFormatException("Byte array length field not found in context: " + field.getLengthField());
                    arrLen = (int) fieldLen;
                }
                return fileReader.readBytes(arrLen);
            }
            case "array": {
                Object countObj = context.get(field.getCountField());
                if (countObj == null)
                    throw new FileFormatException("Array count field not found in context: " + field.getCountField());
                int count = (int) countObj;
                List<Map<String, Object>> elements = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Map<String, Object> elementContext = new HashMap<>();
                    for (FieldDefinition subField : field.getElement().getFields()) {
                        Object value = readField(subField, elementContext);
                        elementContext.put(subField.getName(), value);
                    }
                    elements.add(elementContext);
                }
                return elements;
            }
            default:
                logger.error("Unsupported type: {}", field.getType());
                throw new FileFormatException("Unsupported type: " + field.getType());
        }
    }

    /**
     * Writes a variable-length string (with length prefix).
     */
    public void writeVariableLengthString(String value) {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        fileReader.getMappedBuffer().putInt(valueBytes.length);
        fileReader.getMappedBuffer().put(valueBytes);
    }

    public void seek(int position) {
        fileReader.seek(position);
    }

    public byte[] readBytes(int length) {
        return fileReader.readBytes(length);
    }

    protected void writeBytes(byte[] data) {
        fileReader.getMappedBuffer().put(data);
    }

    public int readInt(FieldDefinition field) {
        if (field.getSeek() != null && field.getSeek().contains("->")) {
            String[] seekOption = field.getSeek().split("->");
            if ("header".equals(seekOption[0])) {
                fileReader.seek((int) metadata.getTableOffset());
            }
        }
        return fileReader.readInt();
    }

    protected void writeInt(int value) {
        fileReader.getMappedBuffer().putInt(value);
    }

    public String readString(int maxLength) {
        int len = fileReader.readInt();
        if (len < 0 || len > maxLength) {
            logger.error("Invalid string length: {}", len);
            throw new FileFormatException("Invalid string length: " + len);
        }
        return fileReader.readString(len);
    }

    protected void writeString(String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        fileReader.getMappedBuffer().putInt(data.length);
        fileReader.getMappedBuffer().put(data);
    }

    public File getFile() {
        return file;
    }

    public void setMAGIC(String MAGIC) {
        this.MAGIC = MAGIC;
    }

    public void setVERSION(int VERSION) {
        this.VERSION = VERSION;
    }

    public void setBYTE_ORDER(ByteOrder BYTE_ORDER) {
        this.BYTE_ORDER = BYTE_ORDER;
        fileReader.setByteOrder(BYTE_ORDER);
    }

    public String getMAGIC() {
        return MAGIC;
    }

    public int getVERSION() {
        return VERSION;
    }

    public ByteOrder getBYTE_ORDER() {
        return BYTE_ORDER;
    }

    public void setFormatDefinition(FileFormatDefinition formatDefinition) {
        this.formatDefinition = formatDefinition;
    }

    public FileFormatDefinition getFormatDefinition() {
        return formatDefinition;
    }

    public M getMetadata() {
        return metadata;
    }

    public long size() {
        return fileReader.size();
    }

    public FileReader getFileReader() {
        return fileReader;
    }

    @Override
    public void close() throws IOException {
        fileReader.close();
    }
}