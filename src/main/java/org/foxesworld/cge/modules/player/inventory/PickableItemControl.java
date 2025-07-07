package org.foxesworld.cge.modules.player.inventory;

import com.jme3.scene.control.AbstractControl;

/**
 * Attach this control to any Spatial to make it pickable by the player.
 */
public class PickableItemControl extends AbstractControl {
    private final ItemStack itemStack;

    public PickableItemControl(Item item, int count) {
        this.itemStack = new ItemStack(item, count);
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    @Override
    protected void controlUpdate(float tpf) {
        // This control can be used for animations, like floating or spinning.
        // For now, it's just a data holder.
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {}
}