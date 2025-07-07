package org.foxesworld.cge.modules.player.inventory;

/**
 * Represents the definition of an item type. This is an immutable data object.
 */
public class Item {
    private final String id;
    private final String name;
    private final int maxStackSize;

    public Item(String id, String name, int maxStackSize) {
        this.id = id;
        this.name = name;
        this.maxStackSize = maxStackSize;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }
}