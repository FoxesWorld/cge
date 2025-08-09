package org.foxesworld.cge.ue.model;

import org.foxesworld.cge.ue.BinaryReader;

import java.io.IOException;

public class FPackageIndex {
    private final int index;

    public FPackageIndex(BinaryReader reader) throws IOException {
        this.index = reader.readInt();
    }

    public boolean isNull() {
        return index == 0;
    }

    public boolean isImport() {
        return index < 0;
    }

    public boolean isExport() {
        return index > 0;
    }

    /** Индекс в Import или Export массиве (с 0-базой) */
    public int getValue() {
        return Math.abs(index) - 1;
    }

    public int getRaw() {
        return index;
    }

    @Override
    public String toString() {
        return (isNull() ? "NULL" : (isImport() ? "Import[" : "Export[")) + getValue() + "]";
    }
}