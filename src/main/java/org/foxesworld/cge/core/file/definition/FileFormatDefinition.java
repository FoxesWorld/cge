package org.foxesworld.cge.core.file.definition;

import java.util.List;

public class FileFormatDefinition {
    private List<FieldDefinition> header;
    private List<FieldDefinition> entry;

    public List<FieldDefinition> getHeader() { return header; }
    public List<FieldDefinition> getEntry() { return entry; }
}
