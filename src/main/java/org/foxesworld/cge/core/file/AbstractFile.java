package org.foxesworld.cge.core.file;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetNotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.file.definition.FieldDefinition;
import org.foxesworld.cge.core.file.definition.FileFormatDefinition;
import org.foxesworld.cge.core.file.definition.FileStructureLoader;
import org.foxesworld.cge.core.file.definition.JsonFileStructureLoader;
import org.foxesworld.cge.core.file.extensions.cgs.CGSFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
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
    protected File file;
    protected FileFormatDefinition formatDefinition;

    private String MAGIC;
    private int VERSION;
    private ByteOrder BYTE_ORDER = LITTLE_ENDIAN;

    private final FileReader fileReader;
    private final FileWriter fileWriter;

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
        this.fileWriter = new FileWriter(this, mode);
        this.loadFormatDefinition(formatDefinition);
    }

    /**
     * Загружает определение формата файла из JSON-конфига с помощью JME AssetManager.
     * @param definition Имя определения формата (без расширения).
     */
    private void loadFormatDefinition(String definition) {
        // 1. Формируем путь к ассету, как это делает JME
        String assetPath = "config/fileformats/" + definition.toLowerCase() + ".json";

        try {
            // 2. Находим ассет по ключу, чтобы получить информацию о нем
            AssetInfo assetInfo = CalistaGameEngine.INSTANCE.getAssetManager().locateAsset(new AssetKey<>(assetPath));

            // 3. Открываем поток в блоке try-with-resources для автоматического закрытия
            try (InputStream stream = assetInfo.openStream()) {
                FileStructureLoader loader = new JsonFileStructureLoader(stream);
                setFormatDefinition(loader.loadFormatDefinition(definition));
            }

        } catch (AssetNotFoundException e) {
            // 4. Это более чистый способ обработки "файл не найден" в JME
            logger.error("Не удалось загрузить формат {}: файл конфигурации не найден по пути '{}'", definition, assetPath, e);
            throw new FileFormatException("Не удалось загрузить формат " + definition + ": файл не найден", e);
        } catch (IOException e) {
            // 5. Обработка ошибок чтения файла
            logger.error("Ошибка ввода-вывода при загрузке формата {}: {}", definition, e.getMessage(), e);
            throw new FileFormatException("Ошибка ввода-вывода при загрузке формата " + definition, e);
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
            logger.error("Unsupported version of {}: expected {}, got {}",MAGIC, VERSION, ver);
            throw new FileFormatException("Unsupported version: expected " + VERSION + ", got " + ver);
        }
        fileReader.seek(fileReader.position() + 2); // skip 2 bytes
    }

    protected abstract void readFile() throws IOException;
    protected abstract void onEntryRead(Map<String, Object> entry);

    /**
     * Reads a field from the file according to its definition.
     */
    protected Object readField(FieldDefinition field, Map<String, Object> context) {
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
     * Writes a field to the file writer's buffer according to its definition.
     * @param field The definition of the field to write.
     * @param value The value to write.
     * @param context A map containing context values (like length fields).
     */
    protected void writeField(FieldDefinition field, Object value, Map<String, Object> context) {
        if (fileWriter == null) throw new IllegalStateException("Cannot write file in the current mode.");

        fileWriter.setByteOrder(field.getByteOrder() != null ? field.getByteOrder() : this.BYTE_ORDER);

        // Обработка специальных случаев
        if (value == null) {
            if (field.getLength() != null && field.getLength() > 0) {
                fileWriter.writeBytes(new byte[field.getLength()]);
            }
            return;
        }

        // Приведение типов для чисел
        Number numValue = (value instanceof Number) ? (Number) value : null;

        switch (field.getType()) {
            case "byte":      fileWriter.writeByte(numValue.byteValue()); break;
            case "ushort":    fileWriter.writeShort(numValue.shortValue()); break;
            case "int":
            case "int32":     fileWriter.writeInt(numValue.intValue()); break;
            case "uint32":    fileWriter.writeInt(numValue.intValue()); break; // Записывается как обычный int
            case "long":
            case "int64":     fileWriter.writeLong(numValue.longValue()); break;
            case "uint64":    fileWriter.writeLong(((BigInteger)value).longValue()); break;
            case "float":     fileWriter.writeFloat(numValue.floatValue()); break;
            case "double":    fileWriter.writeDouble(numValue.doubleValue()); break;
            case "string": {
                // Длина строки должна быть уже записана как отдельное поле
                fileWriter.writeString((String)value);
                break;
            }
            case "bytes":
            case "byteArray": {
                fileWriter.writeBytes((byte[])value);
                break;
            }
            case "array": {
                // Логика записи массива сложнее и требует отдельного метода
                throw new UnsupportedOperationException("Array writing must be handled by the caller method.");
            }
            default:
                throw new FileFormatException("Unsupported write type: " + field.getType());
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

    public FileWriter getFileWriter() {
        return fileWriter;
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