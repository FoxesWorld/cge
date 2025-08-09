package org.foxesworld.cge.ue.model;

/** Упрощённая модель ImportEntry. */
public class ImportEntry {
    public final FName classPackage;
    public final FName className;
    public final FName packageName;
    public final FName objectName;
    public final int packageIndex; // raw value

    public ImportEntry(FName classPackage, FName className, FName packageName, FName objectName, int packageIndex) {
        this.classPackage = classPackage;
        this.className = className;
        this.packageName = packageName;
        this.objectName = objectName;
        this.packageIndex = packageIndex;
    }

    @Override
    public String toString() {
        return "Import{" + objectName + "}";
    }
}