package org.foxesworld.cge.core.file.definition;

import java.io.IOException;

public interface FileStructureLoader {
    FileFormatDefinition loadFormatDefinition(String formatName) throws IOException;
}
