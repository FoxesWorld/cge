package org.foxesworld.cge.ue.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель пакета: name map, import map, export map и доступ к bulk файловым ссылкам.
 */
public class UPackage {
    public List<NameEntry> nameMap = new ArrayList<>();
    public List<ImportEntry> importMap = new ArrayList<>();
    public List<ExportEntry> exportMap = new ArrayList<>();

    // дополнительные поля
    public String packageName;
    public int fileVersion;
    public long headerSize;

    public String lookupName(int idx) {
        if (idx <= 0 || idx > nameMap.size()) return "<unknown:" + idx + ">";
        NameEntry e = nameMap.get(idx - 1); // UE: name index often 1-based
        return e != null ? e.name : ("<null:" + idx + ">");
    }
}