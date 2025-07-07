package org.foxesworld.cge.modules.player.inventory;

/**
 * Represents a stack of a particular Item.
 */
public class ItemStack {
    private final Item item;
    private int count;

    public ItemStack(Item item, int count) {
        this.item = item;
        this.count = count;
    }

    public Item getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    /**
     * Increases the stack size by a given amount.
     * @param amount The number of items to add.
     * @return The number of items that could not be added (e.g., if the stack is full).
     */
    public int add(int amount) {
        int canAdd = item.getMaxStackSize() - this.count;
        int toAdd = Math.min(amount, canAdd);
        this.count += toAdd;
        return amount - toAdd; // Return the remainder
    }
}