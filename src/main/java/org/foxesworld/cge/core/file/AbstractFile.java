package org.foxesworld.cge.core.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.definition.FieldDefinition;
import org.foxesworld.cge.core.file.definition.FileFormatDefinition;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

/**
 * Base file handler: manages opening/closing RandomAccessFile and provides
 * common utility methods for reading/writing bytes, strings and primitive types.
 */
public abstract class AbstractFile implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger(AbstractFile.class);
    protected final RandomAccessFile raf;
    private String MAGIC;
    private int VERSION;
    private int MAX_NAME_LENGTH = 4096;
    private ByteOrder BYTE_ORDER = LITTLE_ENDIAN;
    private final File file;

    protected FileFormatDefinition formatDefinition;

    protected AbstractFile(File file, String mode, FileFormatDefinition formatDefinition) {
        this.file = file;
        this.raf = openRandomAccess(file, mode);
        this.formatDefinition = formatDefinition;
    }

    protected AbstractFile(File file, String mode) {
        this.file = file;
        this.raf = openRandomAccess(file, mode);
    }

    public void readFileNew() throws IOException {
        if (formatDefinition == null) {
            throw new IllegalStateException("Format definition not loaded");
        }

        logger.debug("Reading file using format: {}", formatDefinition);

        // 1. Считываем всё из header в headerMap
        Map<String, Object> headerMap = new LinkedHashMap<>();
        for (FieldDefinition field : formatDefinition.getHeader()) {
            Object value = readField(field, headerMap);
            headerMap.put(field.getName(), value);
            logger.debug("Header field: {} = {}", field.getName(), value);
        }

        // 2. Извлекаем необходимые параметры
        Integer textureCount = (Integer) headerMap.get("textureCount");
        Integer dataOffset    = (Integer) headerMap.get("dataOffset");

        if (textureCount == null) {
            throw new IOException("Header does not contain 'textureCount'");
        }
        if (dataOffset == null) {
            throw new IOException("Header does not contain 'dataOffset'");
        }

        // 3. Перемещаемся к смещению dataOffset
        raf.seek(dataOffset);

        // 4. Читаем каждую запись (entry) в соответствии с форматDefinition.getEntry()
        for (int i = 0; i < textureCount; i++) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            for (FieldDefinition field : formatDefinition.getEntry()) {
                Object value = readField(field, entryMap);
                entryMap.put(field.getName(), value);
                if (field.getName() != "data")
                logger.debug("Entry[{}] field: {} = {}", i, field.getName(), value);
            }
            onEntryRead(entryMap);
        }
    }


    protected abstract void onEntryRead(Map<String, Object> entry);

    private Object readField(FieldDefinition field) throws IOException {
        return readField(field, new LinkedHashMap<>());
    }

    protected Object readField(FieldDefinition field, Map<String, Object> context) throws IOException {
        ByteOrder order = field.getByteOrder() != null ? field.getByteOrder() : BYTE_ORDER;

        switch (field.getType()) {
            case "byte" -> {
                return raf.readByte();
            }
            case "ushort" -> {
                return raf.readUnsignedShort();
            }
            case "int" -> {
                return readInt(order);
            }
            case "long" -> {
                return raf.readLong();
            }
            case "length" -> {
                return  raf.length();
            }
            case "string" -> {
                int length = field.getLength() != null ? field.getLength() : (int) context.get(field.getLengthField());
                byte[] strBytes = new byte[length];
                raf.readFully(strBytes);
                return new String(strBytes, StandardCharsets.UTF_8);
            }
            case "byteArray" -> {
                int arrLen = field.getLength() != null ? field.getLength() : (int) context.get(field.getLengthField());
                byte[] bytes = new byte[arrLen];
                raf.readFully(bytes);
                return bytes;
            }
            default -> throw new IllegalArgumentException("Unsupported type: " + field.getType());
        }
    }

    private short readShort(ByteOrder order) throws IOException {
        byte[] buf = new byte[2];
        raf.readFully(buf);
        ByteBuffer bb = ByteBuffer.wrap(buf).order(order);
        return bb.getShort();
    }

    protected abstract FileReader readFile();

    private RandomAccessFile openRandomAccess(File f, String mode) {
        try {
            return new RandomAccessFile(f, mode);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Cannot open file: " + f.getAbsolutePath(), e);
        }
    }

    public void writeVariableLengthString(String value) throws IOException {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        int length = valueBytes.length;
        raf.writeInt(length);
        raf.write(valueBytes);
    }

    public void seek(long position) throws IOException {
        raf.seek(position);
    }

    public byte[] readBytes(int length) throws IOException {
        byte[] data = new byte[length];
        raf.readFully(data);
        return data;
    }

    protected void writeBytes(byte[] data) throws IOException {
        raf.write(data);
    }

    private int readInt(ByteOrder order) throws IOException {
        byte[] buf = new byte[4];
        raf.readFully(buf);
        ByteBuffer bb = ByteBuffer.wrap(buf).order(order);
        return bb.getInt();
    }

    public int readInt() throws IOException {
        return raf.readInt();
    }

    protected void writeInt(int value) throws IOException {
        raf.writeInt(value);
    }

    public long readLong() throws IOException {
        return raf.readLong();
    }

    protected void writeLong(long value) throws IOException {
        raf.writeLong(value);
    }

    public String readString(int maxLength) throws IOException {
        int len = raf.readInt();
        if (len < 0 || len > maxLength) {
            throw new IOException("Invalid string length: " + len);
        }
        byte[] data = new byte[len];
        raf.readFully(data);
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    protected void writeString(String s) throws IOException {
        byte[] data = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        raf.writeInt(data.length);
        raf.write(data);
    }

    public File getFile() {
        return file;
    }

    public RandomAccessFile getRaf() {
        return raf;
    }

    @Override
    public void close() throws IOException {
        try {
            raf.close();
            logger.info("Closing {}", this.file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setMAGIC(String MAGIC) {
        this.MAGIC = MAGIC;
    }

    public void setVERSION(int VERSION) {
        this.VERSION = VERSION;
    }

    public void setMAX_NAME_LENGTH(int MAX_NAME_LENGTH) {
        this.MAX_NAME_LENGTH = MAX_NAME_LENGTH;
    }

    public void setBYTE_ORDER(ByteOrder BYTE_ORDER) {
        this.BYTE_ORDER = BYTE_ORDER;
    }

    public String getMAGIC() {
        return MAGIC;
    }

    public int getVERSION() {
        return VERSION;
    }

    public int getMAX_NAME_LENGTH() {
        return MAX_NAME_LENGTH;
    }

    public ByteOrder getBYTE_ORDER() {
        return BYTE_ORDER;
    }

    public void setFormatDefinition(FileFormatDefinition formatDefinition) {
        this.formatDefinition = formatDefinition;
    }
}