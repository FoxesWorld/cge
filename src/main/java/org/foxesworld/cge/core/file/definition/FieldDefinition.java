package org.foxesworld.cge.core.file.definition;

import com.google.gson.annotations.SerializedName;

import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

/**
 * Definition of a single field within a structured binary file.
 * Supports primitive types, arrays, byte order, length/count references, and seek directives.
 */
public class FieldDefinition {

    private String name;
    private String type;
   private String seek;
   private Integer length;
   private ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
   private String lengthField;
    private String countField;
    private ElementDefinition element;

    public FieldDefinition() {
        // Default constructor for JSON deserialization
    }

    /**
     * Fluent builder for manual creation.
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public String getSeek() { return seek; }
    public Integer getLength() { return length; }
    public ByteOrder getByteOrder() { return byteOrder; }
    public String getLengthField() { return lengthField; }
    public String getCountField() { return countField; }
    public ElementDefinition getElement() { return element; }

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                (seek != null ? ", seek='" + seek + '\'' : "") +
                (length != null ? ", length=" + length : "") +
                (lengthField != null ? ", lengthField='" + lengthField + '\'' : "") +
                (countField != null ? ", countField='" + countField + '\'' : "") +
                ", element='" + element + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldDefinition)) return false;
        FieldDefinition that = (FieldDefinition) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(type, that.type) &&
                Objects.equals(seek, that.seek) &&
                Objects.equals(length, that.length) &&
                byteOrder == that.byteOrder &&
                Objects.equals(lengthField, that.lengthField) &&
                Objects.equals(countField, that.countField) &&
                Objects.equals(element, that.element);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, seek, length, byteOrder, lengthField, countField, element);
    }

    /**
     * Builder for FieldDefinition.
     */
    public static class Builder {
        private final FieldDefinition fd = new FieldDefinition();

        public Builder name(String name) {
            fd.name = Objects.requireNonNull(name, "Field name is required");
            return this;
        }

        public Builder type(String type) {
            fd.type = Objects.requireNonNull(type, "Field type is required");
            return this;
        }

        public Builder seek(String seek) {
            fd.seek = seek;
            return this;
        }

        public Builder length(int length) {
            fd.length = length;
            return this;
        }

        public Builder byteOrder(ByteOrder order) {
            fd.byteOrder = order;
            return this;
        }

        public Builder lengthField(String field) {
            fd.lengthField = field;
            return this;
        }

        public Builder countField(String field) {
            fd.countField = field;
            return this;
        }

        public Builder element(ElementDefinition element) {
            fd.element = element;
            return this;
        }

        public FieldDefinition build() {
            // Validation
            Objects.requireNonNull(fd.name, "Field name is required");
            Objects.requireNonNull(fd.type, "Field type is required");
            if ("array".equals(fd.type)) {
                Objects.requireNonNull(fd.element, "ElementDefinition required for arrays");
                Objects.requireNonNull(fd.countField, "countField is required for arrays");
            }
            return fd;
        }
    }

    /**
     * Definition of a complex element within an array.
     */
    public static class ElementDefinition {
        @SerializedName("fields")
        private List<FieldDefinition> fields;

        public ElementDefinition() {}

        public List<FieldDefinition> getFields() { return fields; }
        public void setFields(List<FieldDefinition> fields) { this.fields = fields; }

        @Override
        public String toString() {
            return "ElementDefinition{fields=" + fields + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ElementDefinition)) return false;
            ElementDefinition that = (ElementDefinition) o;
            return Objects.equals(fields, that.fields);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fields);
        }
    }
}
