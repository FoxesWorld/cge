package org.foxesworld.cge.core.file.definition;

import java.nio.ByteOrder;

public class FieldDefinition {
    private String name;
    private String type;
    private Integer length;
    private ByteOrder byteOrder;
    private String lengthField;

    public FieldDefinition() {}

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Integer getLength() {
        return length;
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public String getLengthField() {
        return lengthField;
    }
}
