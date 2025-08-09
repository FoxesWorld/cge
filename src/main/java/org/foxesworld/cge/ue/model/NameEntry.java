package org.foxesworld.cge.ue.model;

/** Запись в NameMap: строка и флаги (int). */
public class NameEntry {
    public final String name;
    public final int flags;

    public NameEntry(String name, int flags) {
        this.name = name;
        this.flags = flags;
    }
}