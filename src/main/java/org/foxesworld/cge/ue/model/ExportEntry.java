package org.foxesworld.cge.ue.model;

/**
 * Упрощённая модель ExportEntry:
 * хранит ключевые поля: classIndex, superIndex, templateIndex, objectName, serialSize, serialOffset
 */
public class ExportEntry {
    public final FName classIndex; // может быть FName или int, тут упрощаем
    public final FName superIndex;
    public final FName templateIndex;
    public final FName objectName;
    public final long serialSize;
    public final long serialOffset;
    public final int objectFlags;

    public ExportEntry(FName classIndex, FName superIndex, FName templateIndex, FName objectName,
                       long serialSize, long serialOffset, int objectFlags) {
        this.classIndex = classIndex;
        this.superIndex = superIndex;
        this.templateIndex = templateIndex;
        this.objectName = objectName;
        this.serialSize = serialSize;
        this.serialOffset = serialOffset;
        this.objectFlags = objectFlags;
    }

    @Override
    public String toString() {
        return "Export{" + objectName + ", size=" + serialSize + ", off=" + serialOffset + "}";
    }
}