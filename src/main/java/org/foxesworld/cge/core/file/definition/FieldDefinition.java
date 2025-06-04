package org.foxesworld.cge.core.file.definition;

import java.nio.ByteOrder;
import java.util.List;

public class FieldDefinition {

    private String name, type, seek;
    private Integer length;
    private ByteOrder byteOrder;
    private String lengthField;
    private String countField;
    private ElementDefinition element;

    public FieldDefinition() {}

    // === Стандартные геттеры ===

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

    public String getCountField() {
        return countField;
    }

    public String getSeek() {
        return seek;
    }

    public ElementDefinition getElement() {
        return element;
    }

    // === Вложенный класс для описания структуры элемента массива ===

    public static class ElementDefinition {
        private List<FieldDefinition> fields;

        public ElementDefinition() {}

        public List<FieldDefinition> getFields() {
            return fields;
        }

        public void setFields(List<FieldDefinition> fields) {
            this.fields = fields;
        }
    }

    // === Сеттеры (если нужно для ручной инициализации, например при создании из кода) ===

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public void setByteOrder(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
    }

    public void setLengthField(String lengthField) {
        this.lengthField = lengthField;
    }

    public void setCountField(String countField) {
        this.countField = countField;
    }

    public void setElement(ElementDefinition element) {
        this.element = element;
    }
}
