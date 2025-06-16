package org.foxesworld.cge.modules.player;

import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.*;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.control.AbstractControl;

import static java.lang.Math.max;

/**
 * MovementControl handles first-person character motion with smooth
 * acceleration, deceleration, and jump/landing callbacks. It relies on
 * BetterCharacterControl for robust physics and collision handling.
 */
public class MovementControl extends AbstractControl implements ActionListener {

    /** Listener for jump start and landing events. */
    public interface JumpListener {
        /** Called when a jump is initiated. */
        void onJumpStart();
        /** Called when the character lands, with the peak height relative to takeoff. */
        void onLanding(float peakHeight);
    }

    private static final String MAP_FORWARD   = "MoveForward";
    private static final String MAP_BACKWARD  = "MoveBackward";
    private static final String MAP_LEFT      = "MoveLeft";
    private static final String MAP_RIGHT     = "MoveRight";
    private static final String MAP_JUMP      = "Jump";
    private static final String MAP_SPRINT    = "Sprint";

    private final Player player;
    private final CharacterControl character;
    private final InputManager input;

    private final float walkSpeed;
    private final float sprintSpeed;
    private final float acceleration;
    private final float deceleration;
    private final float smoothFactor;

    private boolean forward, backward, left, right, sprint;
    private JumpListener jumpListener;

    private final Vector3f currentVel = new Vector3f();
    private final Vector3f desiredVel = new Vector3f();
    private final Vector3f camDir      = new Vector3f();
    private final Vector3f camLeft     = new Vector3f();
    private final Vector3f tempDir     = new Vector3f();

    private boolean wasInAir;
    private float lastY;
    private float peakY;

    /**
     * @param player        Player node providing camera and input
     * @param walkSpeed     Movement speed when walking
     * @param sprintSpeed   Movement speed when sprinting
     * @param acceleration  Rate of speed increase (units/s²)
     * @param deceleration  Rate of speed decrease (units/s²)
     * @param smoothFactor  Interpolation factor for velocity smoothing (0=no smoothing, 1=instant)
     */
    public MovementControl(Player player,
                           float walkSpeed,
                           float sprintSpeed,
                           float acceleration,
                           float deceleration,
                           float smoothFactor) {
        this.player        = player;
        this.character     = player.getCharacter();
        this.input         = player.getInput();
        this.walkSpeed     = walkSpeed;
        this.sprintSpeed   = sprintSpeed;
        this.acceleration  = acceleration;
        this.deceleration  = deceleration;
        this.smoothFactor  = FastMath.clamp(smoothFactor, 0f, 1f);

        registerInput();
    }

    private void registerInput() {
        input.addMapping(MAP_FORWARD,  new KeyTrigger(KeyInput.KEY_W));
        input.addMapping(MAP_BACKWARD, new KeyTrigger(KeyInput.KEY_S));
        input.addMapping(MAP_LEFT,     new KeyTrigger(KeyInput.KEY_A));
        input.addMapping(MAP_RIGHT,    new KeyTrigger(KeyInput.KEY_D));
        input.addMapping(MAP_JUMP,     new KeyTrigger(KeyInput.KEY_SPACE));
        input.addMapping(MAP_SPRINT,   new KeyTrigger(KeyInput.KEY_LSHIFT));
        input.addListener(this,
                MAP_FORWARD, MAP_BACKWARD, MAP_LEFT,
                MAP_RIGHT, MAP_JUMP, MAP_SPRINT);
    }

    @Override
    protected void controlUpdate(float tpf) {
        // Determine desired direction based on input
        tempDir.set(0, 0, 0);
        if (forward)  tempDir.z += 1f;
        if (backward) tempDir.z -= 1f;
        if (left)     tempDir.x += 1f;
        if (right)    tempDir.x -= 1f;

        if (!tempDir.equals(Vector3f.ZERO)) {
            tempDir.normalizeLocal();
            player.getCam().getDirection(camDir).setY(0).normalizeLocal();
            player.getCam().getLeft(camLeft).setY(0).normalizeLocal();
            desiredVel.set(camDir).multLocal(tempDir.z)
                    .addLocal(camLeft.multLocal(tempDir.x));
            desiredVel.multLocal(sprint ? sprintSpeed : walkSpeed);
        } else {
            desiredVel.set(0, 0, 0);
        }

        // Smoothly interpolate current velocity toward desired
        float alpha = 1f - FastMath.pow(1f - smoothFactor, tpf * 60f);
        currentVel.interpolateLocal(desiredVel, alpha);
        character.setWalkDirection(currentVel);

        // Update facing direction if moving
        if (currentVel.lengthSquared() > 1e-4f) {
            Vector3f viewDir = currentVel.normalize();
            character.setViewDirection(new Vector3f(viewDir.x, 0, viewDir.z));
        }

        // Jump and landing detection
        boolean inAir = !character.onGround();
        float posY = spatial.getWorldTranslation().y;

        if (inAir) {
            if (posY > lastY) {
                peakY = max(peakY, posY);
            }
        }
        if (wasInAir && !inAir && jumpListener != null) {
            jumpListener.onLanding(peakY - lastY);
            peakY = 0f;
        }
        wasInAir = inAir;
        lastY    = posY;

        // Update HUD speed display
        player.getPlayerHud().setPlayerSpeed(1f);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case MAP_FORWARD   -> forward  = isPressed;
            case MAP_BACKWARD  -> backward = isPressed;
            case MAP_LEFT      -> left     = isPressed;
            case MAP_RIGHT     -> right    = isPressed;
            case MAP_SPRINT    -> sprint   = isPressed;
            case MAP_JUMP      -> {
                if (isPressed && character.onGround()) {
                    character.jump();
                    if (jumpListener != null) {
                        jumpListener.onJumpStart();
                    }
                }
            }
        }
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {
        // Not used
    }

    /** Registers a listener for jump start and landing events. */
    public void setJumpListener(JumpListener listener) {
        this.jumpListener = listener;
    }

    /** @return current horizontal speed in world units per second. */
    public float getCurrentSpeed() {
        return currentVel.length();
    }

    /** @return true if the character is currently moving. */
    public boolean isMoving() {
        return getCurrentSpeed() > 1e-4f;
    }
}
