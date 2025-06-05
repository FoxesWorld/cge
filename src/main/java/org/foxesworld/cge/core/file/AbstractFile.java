package org.foxesworld.cge.core.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.file.definition.FieldDefinition;
import org.foxesworld.cge.core.file.definition.FileFormatDefinition;
import org.foxesworld.cge.core.io.RawByteParser;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

/**
 * Provides common functionality for reading and writing structured binary files.
 *
 * @param <M> the type of metadata associated with this file
 */
public abstract class AbstractFile<M extends Metadata> {
    private static final Logger logger = LogManager.getLogger(AbstractFile.class);

    protected final HexFormat HEX = HexFormat.of();
    protected M metadata;
    protected final RandomAccessFile raf;
    private String MAGIC;
    private int VERSION;
    private ByteOrder BYTE_ORDER = LITTLE_ENDIAN;
    private final File file;
    private final FileReader fileReader;
    protected FileFormatDefinition formatDefinition;
    @Deprecated
    private final RawByteParser rawByteParser;

    /**
     * Constructs a new AbstractFile with the given {@code File} and mode.
     *
     * @param file the file to open
     * @param mode the access mode (e.g., "r", "rw")
     */
    protected AbstractFile(File file, String mode) {
        this.file = file;
        this.fileReader = new FileReader(this, mode);
        this.raf = this.fileReader.getRaf();
        this.rawByteParser = new RawByteParser(this.fileReader.getFileBytes());
    }

    /**
     * Parses the entire file according to the defined structure.
     *
     * @throws IOException if an I/O error occurs
     */
    protected abstract void readFileNew() throws IOException;

    /**
     * Called for each parsed entry in the file.
     *
     * @param entry a map of field names to values for the current entry
     */
    protected abstract void onEntryRead(Map<String, Object> entry);

    /**
     * Reads a single field from the file according to the provided definition.
     *
     * @param field   the definition of the field to read
     * @param context a map of previously read field values in the current scope
     * @return the parsed field value
     * @throws IOException if an I/O error occurs
     */
    protected Object readField(FieldDefinition field, Map<String, Object> context) throws IOException {
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

    /**
     * Writes a variable-length UTF-8 string to the file.
     *
     * @param value the string to write
     * @throws IOException if an I/O error occurs
     */
    public void writeVariableLengthString(String value) throws IOException {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        int length = valueBytes.length;
        raf.writeInt(length);
        raf.write(valueBytes);
    }

    /**
     * Positions the file pointer at the given absolute byte offset.
     *
     * @param position the position to seek to
     * @throws IOException if an I/O error occurs
     */
    public void seek(long position) throws IOException {
        raf.seek(position);
    }

    /**
     * Reads a byte array of the specified length from the current file position.
     *
     * @param length the number of bytes to read
     * @return the byte array read
     * @throws IOException if an I/O error occurs
     */
    public byte[] readBytes(int length) throws IOException {
        byte[] data = new byte[length];
        raf.readFully(data);
        return data;
    }

    /**
     * Writes the given byte array to the file at the current position.
     *
     * @param data the data to write
     * @throws IOException if an I/O error occurs
     */
    protected void writeBytes(byte[] data) throws IOException {
        raf.write(data);
    }

    /**
     * Reads a 16-bit signed integer from the file using the given byte order.
     *
     * @param order the byte order to use
     * @return the short value read
     * @throws IOException if an I/O error occurs
     */
    private short readShort(ByteOrder order) throws IOException {
        byte[] buf = new byte[2];
        raf.readFully(buf);
        ByteBuffer bb = ByteBuffer.wrap(buf).order(order);
        return bb.getShort();
    }

    /**
     * Reads a 32-bit signed integer from the file using the given field definition.
     *
     * @param field the field definition that may contain a seek directive
     * @return the integer value read
     * @throws IOException if an I/O error occurs
     */
    public int readInt(FieldDefinition field) throws IOException {
        if (field.getSeek() != null && field.getSeek().contains("->")) {
            String[] seekOption = field.getSeek().split("->");
            if (seekOption[0].equals("header")) {
                raf.seek(metadata.getTableOffset());
            }
        }
        return raf.readInt();
    }

    /**
     * Writes a 32-bit signed integer to the file.
     *
     * @param value the integer value to write
     * @throws IOException if an I/O error occurs
     */
    protected void writeInt(int value) throws IOException {
        raf.writeInt(value);
    }

    /**
     * Reads a 64-bit signed integer from the file.
     *
     * @return the long value read
     * @throws IOException if an I/O error occurs
     */
    public long readLong() throws IOException {
        return raf.readLong();
    }

    /**
     * Writes a 64-bit signed integer to the file.
     *
     * @param value the long value to write
     * @throws IOException if an I/O error occurs
     */
    protected void writeLong(long value) throws IOException {
        raf.writeLong(value);
    }

    /**
     * Reads a UTF-8 string prefixed by its length (32-bit int).
     *
     * @param maxLength the maximum allowed string length
     * @return the string read
     * @throws IOException if an I/O error occurs or the length is invalid
     */
    public String readString(int maxLength) throws IOException {
        int len = raf.readInt();
        if (len < 0 || len > maxLength) {
            throw new IOException("Invalid string length: " + len);
        }
        byte[] data = new byte[len];
        raf.readFully(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Writes a UTF-8 string prefixed by its length (32-bit int).
     *
     * @param s the string to write
     * @throws IOException if an I/O error occurs
     */
    protected void writeString(String s) throws IOException {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        raf.writeInt(data.length);
        raf.write(data);
    }

    /**
     * Returns the underlying {@link File} object.
     *
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * Sets the expected magic identifier for this file type.
     *
     * @param MAGIC the magic string to set
     */
    public void setMAGIC(String MAGIC) {
        this.MAGIC = MAGIC;
    }

    /**
     * Sets the expected version number for this file type.
     *
     * @param VERSION the version number to set
     */
    public void setVERSION(int VERSION) {
        this.VERSION = VERSION;
    }

    /**
     * Sets the byte order used for multi-byte reads and writes.
     *
     * @param BYTE_ORDER the byte order to use
     */
    public void setBYTE_ORDER(ByteOrder BYTE_ORDER) {
        this.BYTE_ORDER = BYTE_ORDER;
    }

    /**
     * Returns the magic identifier for this file.
     *
     * @return the magic string
     */
    public String getMAGIC() {
        return MAGIC;
    }

    /**
     * Returns the version number for this file.
     *
     * @return the version number
     */
    public int getVERSION() {
        return VERSION;
    }

    /**
     * Returns the byte order used for multi-byte operations.
     *
     * @return the byte order
     */
    public ByteOrder getBYTE_ORDER() {
        return BYTE_ORDER;
    }

    /**
     * Sets the format definition describing header and entry structures.
     *
     * @param formatDefinition the format definition to set
     */
    public void setFormatDefinition(FileFormatDefinition formatDefinition) {
        this.formatDefinition = formatDefinition;
    }

    /**
     * Returns the metadata associated with this file.
     *
     * @return the metadata object
     */
    public M getMetadata() {
        return metadata;
    }

    /**
     * Returns the {@link FileReader} used to access the file bytes.
     *
     * @return the FileReader
     */
    public FileReader getFileReader() {
        return fileReader;
    }
}
