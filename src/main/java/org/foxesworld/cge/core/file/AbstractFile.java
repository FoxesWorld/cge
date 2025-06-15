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
                    CGSFile.class.getClassLoader().getResourceAsStream("config/fileformats/" + definition.toLowerCase() + ".json")
            );
            setFormatDefinition(loader.loadFormatDefinition(definition));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + definition + " format", e);
        }
    }

    @Deprecated
    protected void verifyHeader() {
        fileReader.seek(0);
        byte[] magicBytes = new byte[MAGIC.length()];
        fileReader.readBytes(magicBytes.length);
        System.arraycopy(fileReader.readBytes(magicBytes.length), 0, magicBytes, 0, magicBytes.length);
        String fileMagic = new String(magicBytes, StandardCharsets.US_ASCII);
        if (!fileMagic.equals(MAGIC)) {
            throw new RuntimeException("Invalid file magic: expected '" + MAGIC + "', got '" + fileMagic + "'");
        }
        int ver = Short.toUnsignedInt(fileReader.readShort());
        if (ver != VERSION) {
            throw new RuntimeException("Unsupported version: expected " + VERSION + ", got " + ver);
        }
        fileReader.seek(fileReader.position() + 2); // skip 2 bytes
    }

    protected abstract void readFileNew() throws IOException;
    protected abstract void onEntryRead(Map<String, Object> entry);

    protected Object readField(FieldDefinition field, Map<String, Object> context) throws IOException {
        BYTE_ORDER = field.getByteOrder();
        fileReader.setByteOrder(BYTE_ORDER);
        return switch (field.getType()) {
            case "byte" -> fileReader.readByte();
            case "ushort" -> Short.toUnsignedInt(fileReader.readShort());
            case "int", "int32" -> readInt(field);
            case "uint32" -> Integer.toUnsignedLong(readInt(field));
            case "long", "int64" -> fileReader.readLong();
            case "uint64" -> {
                long signed = fileReader.readLong();
                BigInteger ui64 = BigInteger.valueOf(signed);
                if (signed < 0) {
                    ui64 = ui64.add(BigInteger.ONE.shiftLeft(64));
                }
                yield ui64;
            }
            case "float" -> fileReader.readFloat();
            case "double" -> fileReader.readDouble();
            case "length" -> fileReader.size();
            case "string" -> {
                int length = field.getLength() != null
                        ? field.getLength()
                        : (int) context.get(field.getLengthField());
                yield fileReader.readString(length);
            }
            case "byteArray" -> {
                int arrLen = field.getLength() != null
                        ? field.getLength()
                        : (int) context.get(field.getLengthField());
                yield fileReader.readBytes(arrLen);
            }
            case "array" -> {
                int count = (int) context.get(field.getCountField());
                List<Map<String, Object>> elements = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Map<String, Object> elementContext = new HashMap<>();
                    for (FieldDefinition subField : field.getElement().getFields()) {
                        Object value = readField(subField, elementContext);
                        elementContext.put(subField.getName(), value);
                    }
                    elements.add(elementContext);
                }
                yield elements;
            }
            default -> throw new IllegalArgumentException("Unsupported type: " + field.getType());
        };
    }

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
            if (seekOption[0].equals("header")) {
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
            throw new RuntimeException("Invalid string length: " + len);
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