package org.foxesworld.cge.modules.player.modules;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.Vector3f;
import com.jme3.bullet.control.CharacterControl;
import org.foxesworld.cge.modules.player.PlayerContext;
import org.foxesworld.cge.modules.player.modules.PlayerSubModule;

/**
 * Пример модульного управления движением игрока.
 */
public class MovementModule implements PlayerSubModule, ActionListener {
    private PlayerContext ctx;
    private boolean forward, back, left, right, sprint, jump;
    private final Vector3f walkDirection = new Vector3f();

    @Override
    public void onAttach(PlayerContext context) {
        this.ctx = context;
        InputManager input = ctx.getInput();
        input.addMapping("MoveForward", new KeyTrigger(KeyInput.KEY_W));
        input.addMapping("MoveBack", new KeyTrigger(KeyInput.KEY_S));
        input.addMapping("MoveLeft", new KeyTrigger(KeyInput.KEY_A));
        input.addMapping("MoveRight", new KeyTrigger(KeyInput.KEY_D));
        input.addMapping("Jump", new KeyTrigger(KeyInput.KEY_SPACE));
        input.addMapping("Sprint", new KeyTrigger(KeyInput.KEY_LSHIFT));
        input.addListener(this, "MoveForward", "MoveBack", "MoveLeft", "MoveRight", "Jump", "Sprint");
    }

    @Override
    public void onDetach() {
        InputManager input = ctx.getInput();
        input.deleteMapping("MoveForward");
        input.deleteMapping("MoveBack");
        input.deleteMapping("MoveLeft");
        input.deleteMapping("MoveRight");
        input.deleteMapping("Jump");
        input.deleteMapping("Sprint");
        input.removeListener(this);
    }

    @Override
    public void update(float tpf) {
        walkDirection.set(0, 0, 0);
        float speed = sprint ? ctx.getSprintSpeed() : ctx.getWalkSpeed();
        if (forward)  walkDirection.addLocal(0, 0, -1);
        if (back)     walkDirection.addLocal(0, 0,  1);
        if (left)     walkDirection.addLocal(-1, 0, 0);
        if (right)    walkDirection.addLocal(1, 0, 0);
        walkDirection.normalizeLocal().multLocal(speed);

        CharacterControl character = ctx.getCharacter();
        character.setWalkDirection(walkDirection);

        if (jump) {
            character.jump();
            jump = false;
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "MoveForward": forward = isPressed; break;
            case "MoveBack":    back    = isPressed; break;
            case "MoveLeft":    left    = isPressed; break;
            case "MoveRight":   right   = isPressed; break;
            case "Sprint":      sprint  = isPressed; break;
            case "Jump":        if (isPressed) jump = true; break;
        }
    }
}