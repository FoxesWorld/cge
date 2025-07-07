package org.foxesworld.cge.modules.player.inventory;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Manages the player's inventory, including a hotbar and main storage.
 */
public class Inventory {
    private final ItemStack[] hotbar;
    // private final ItemStack[] mainInventory; // Can be added later
    private int selectedHotbarSlot = 0;
    private Consumer<Inventory> listener; // To notify HUD about changes

    public Inventory(int hotbarSize) {
        this.hotbar = new ItemStack[hotbarSize];
    }

    /**
     * Tries to add an item stack to the inventory.
     * First tries to stack with existing items in the hotbar, then finds an empty slot.
     * @param stackToAdd The ItemStack to add.
     * @return true if the item was successfully added, false otherwise.
     */
    public boolean addItem(ItemStack stackToAdd) {
        // 1. Try to stack with existing items in the hotbar
        for (ItemStack slot : hotbar) {
            if (slot != null && slot.getItem().getId().equals(stackToAdd.getItem().getId())) {
                int remainder = slot.add(stackToAdd.getCount());
                stackToAdd.setCount(remainder);
                if (remainder == 0) {
                    notifyListener();
                    return true;
                }
            }
        }

        // 2. Find an empty slot in the hotbar
        for (int i = 0; i < hotbar.length; i++) {
            if (hotbar[i] == null) {
                hotbar[i] = stackToAdd;
                notifyListener();
                return true;
            }
        }

        // No space in hotbar (could extend to main inventory later)
        return false;
    }

    public void selectNextHotbarSlot() {
        selectedHotbarSlot = (selectedHotbarSlot + 1) % hotbar.length;
        notifyListener();
    }

    public void selectPreviousHotbarSlot() {
        selectedHotbarSlot--;
        if (selectedHotbarSlot < 0) {
            selectedHotbarSlot = hotbar.length - 1;
        }
        notifyListener();
    }

    public ItemStack getSelectedItem() {
        return hotbar[selectedHotbarSlot];
    }

    public ItemStack[] getHotbarItems() {
        return Arrays.copyOf(hotbar, hotbar.length);
    }

    public int getSelectedHotbarSlot() {
        return selectedHotbarSlot;
    }

    public void setListener(Consumer<Inventory> listener) {
        this.listener = listener;
    }

    private void notifyListener() {
        if (listener != null) {
            listener.accept(this);
        }
    }
}