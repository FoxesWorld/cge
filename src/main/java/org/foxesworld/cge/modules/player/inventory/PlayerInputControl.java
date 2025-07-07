package org.foxesworld.cge.modules.player.inventory;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.scene.control.AbstractControl;
import org.foxesworld.cge.modules.player.Player;

/**
 * Handles player inputs not related to movement, like inventory and interaction.
 */
public class PlayerInputControl extends AbstractControl implements ActionListener, AnalogListener {

    private static final String INTERACT = "Player_Interact";
    private static final String SCROLL_WHEEL = "Player_ScrollWheel";

    private final InputManager inputManager;
    private final PlayerInteractionControl interactionControl;
    private final Inventory inventory;

    public PlayerInputControl(Player player) {
        this.inputManager = player.getInput();
        this.interactionControl = player.getInteractionControl();
        this.inventory = player.getInventory();
        setupKeys();
    }

    private void setupKeys() {
        inputManager.addMapping(INTERACT, new KeyTrigger(KeyInput.KEY_E));
        inputManager.addMapping(SCROLL_WHEEL, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false),
                new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

        inputManager.addListener(this, INTERACT);
        inputManager.addListener(this, SCROLL_WHEEL);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (name.equals(INTERACT) && isPressed) {
            interactionControl.interact();
        }
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        if (name.equals(SCROLL_WHEEL)) {
            if (value > 0) {
                inventory.selectPreviousHotbarSlot();
            } else {
                inventory.selectNextHotbarSlot();
            }
        }
    }

    public void cleanup() {
        inputManager.removeListener(this);
        inputManager.deleteMapping(INTERACT);
        inputManager.deleteMapping(SCROLL_WHEEL);
    }

    @Override
    protected void controlUpdate(float tpf) {}

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {}
}