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
import java.util.*;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

/**
 * Base file handler: manages opening/closing RandomAccessFile and provides
 * common utility methods for reading/writing bytes, strings and primitive types.
 */
public abstract class AbstractFile<M extends Metadata> implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger(AbstractFile.class);
    protected final HexFormat HEX = HexFormat.of();
    protected M metadata;
    protected final RandomAccessFile raf;
    private String MAGIC;
    private int VERSION;
    private ByteOrder BYTE_ORDER = LITTLE_ENDIAN;
    private final File file;
    private final  FileReader fileReader;

    protected FileFormatDefinition formatDefinition;
    protected AbstractFile(File file, String mode) {
        this.file = file;
        this.fileReader = new FileReader(this, mode);
        this.raf = this.fileReader.getRaf();
    }
    protected abstract void readFileNew() throws IOException;
    protected abstract void onEntryRead(Map<String, Object> entry);

    protected Object readField(FieldDefinition field, Map<String, Object> context) throws IOException {
        //ByteOrder order = field.getByteOrder() != null ? field.getByteOrder() : BYTE_ORDER;
        return switch (field.getType()) {
            case "byte" -> raf.readByte();

            case "ushort" -> raf.readUnsignedShort();

            case "int" -> readInt(field);

            case "long" -> readLong();

            case "length" -> raf.length();

            case "string" -> {
                int length = field.getLength() != null
                        ? field.getLength()
                        : (int) context.get(field.getLengthField());
                byte[] strBytes = new byte[length];
                raf.readFully(strBytes);
                yield new String(strBytes, StandardCharsets.UTF_8);
            }

            case "byteArray" -> {
                int arrLen = field.getLength() != null
                        ? field.getLength()
                        : (int) context.get(field.getLengthField());
                byte[] bytes = new byte[arrLen];
                raf.readFully(bytes);
                yield bytes;
            }

            case "array" -> {
                int count = (int) context.get(field.getCountField());
                List<Map<String, Object>> elements = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                    Map<String, Object> elementContext = new HashMap<>();
                    List<FieldDefinition> fields = field.getElement().getFields();
                    for (FieldDefinition subField : fields) {
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

    private short readShort(ByteOrder order) throws IOException {
        byte[] buf = new byte[2];
        raf.readFully(buf);
        ByteBuffer bb = ByteBuffer.wrap(buf).order(order);
        return bb.getShort();
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

    public int readInt(FieldDefinition field) throws IOException {
        if(field.getSeek() != null) {
            if(field.getSeek().contains("->")){
                String[] seekOption = field.getSeek().split("->");
                if(seekOption[0].equals("header")){
                   raf.seek(metadata.getTableOffset());
                }
            }
        }
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

    @Override
    public void close() {
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

    public void setBYTE_ORDER(ByteOrder BYTE_ORDER) {
        this.BYTE_ORDER = BYTE_ORDER;
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

    public M getMetadata() {
        return metadata;
    }

    public FileReader getFileReader() {
        return fileReader;
    }

    public static class  FileReader {
        private final  AbstractFile abstractFile;
        protected final RandomAccessFile raf;
        private final  String mode;
        FileReader(AbstractFile abstractFile, String mode){
            this.abstractFile = abstractFile;
            this.mode = mode;
            this.raf = this.openRandomAccess(abstractFile.getFile(), this.mode);
        }

        private RandomAccessFile openRandomAccess(File f, String mode) {
            try {
                return new RandomAccessFile(f, mode);
            } catch (FileNotFoundException e) {
                throw new IllegalStateException("Cannot open file: " + f.getAbsolutePath(), e);
            }
        }

        public RandomAccessFile getRaf() {
            return raf;
        }
    }
}