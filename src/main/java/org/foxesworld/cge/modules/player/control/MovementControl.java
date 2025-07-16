package org.foxesworld.cge.modules.player.control;

import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import org.foxesworld.cge.modules.player.Player;
import org.foxesworld.cge.modules.player.PlayerState;
import org.foxesworld.cge.modules.player.config.PlayerConfig;

import static java.lang.Math.max;

/**
 * Manages first-person character movement, focusing purely on physics and input.
 * <p>
 * This control is "animation-agnostic". It translates user input into physical
 * motion using a {@link CharacterControl} and reports key events like jumping,
 * landing, and changes in speed via the {@link MovementListener} interface.
 * All animation-related decisions are left to external systems that implement this listener.
 */
public final class MovementControl extends AbstractControl implements ActionListener {

    /**
     * A listener interface for receiving key movement events from the {@link MovementControl}.
     * This serves as the primary communication channel to other systems, such as an animation controller.
     */
    public interface MovementListener {
        void onJumpStart();
        void onLanding(float fallHeight);
        void onMove(float targetSpeed);
        /**
         * Called rhythmically with every step the character takes on the ground.
         * The frequency is determined by a time interval based on speed (walk/sprint).
         */
        void onStep();
    }

    private static final String MAP_FORWARD = "MoveForward";
    private static final String MAP_BACKWARD = "MoveBackward";
    private static final String MAP_LEFT = "MoveLeft";
    private static final String MAP_RIGHT = "MoveRight";
    private static final String MAP_JUMP = "Jump";
    private static final String MAP_SPRINT = "Sprint";

    // Constants for time-based footstep logic
    private static final float WALK_STEP_INTERVAL_SECONDS = 0.5f; // Time between steps when walking
    private static final float SPRINT_STEP_INTERVAL_SECONDS = 0.35f; // Time between steps when sprinting

    private final Player player;
    private final CharacterControl character;
    private final InputManager input;
    private final float walkSpeed;
    private final float sprintSpeed;
    private final float smoothFactor;

    private boolean forward, backward, left, right, sprint;
    private MovementListener movementListener;

    private final Vector3f currentVel = new Vector3f();
    private final Vector3f desiredVel = new Vector3f();
    private final Vector3f camDir = new Vector3f();
    private final Vector3f camLeft = new Vector3f();
    private final Vector3f tempDir = new Vector3f();

    private boolean wasInAir = false;
    private float lastY = 0f;
    private float peakY = 0f;
    private float timeSinceLastStep = 0f; // Timer instead of distance counter

    private boolean inputRegistered = false;
    private boolean rawRegistered = false;
    private RawInputListener rawListener;

    /**
     * Constructs a new MovementControl.
     *
     * @param player         The context providing access to core components like the camera and character.
     * @param movementConfig The configuration object containing speed and smoothing values.
     */
    public MovementControl(Player player, PlayerConfig.MovementConfig movementConfig) {
        this.player = player;
        this.character = player.getCharacter();
        this.input = player.getInput();
        this.walkSpeed = movementConfig.getWalkSpeed();
        this.sprintSpeed = movementConfig.getSprintSpeed();
        this.smoothFactor = FastMath.clamp(movementConfig.getSmoothing(), 0f, 1f);

        registerInput();
        registerRawInput();
    }

    // --- Input registration/unregistration methods (unchanged) ---
    private void registerInput() {
        if (inputRegistered) return;
        inputRegistered = true;
        if (!input.hasMapping(MAP_FORWARD)) input.addMapping(MAP_FORWARD, new KeyTrigger(KeyInput.KEY_W));
        if (!input.hasMapping(MAP_BACKWARD)) input.addMapping(MAP_BACKWARD, new KeyTrigger(KeyInput.KEY_S));
        if (!input.hasMapping(MAP_LEFT)) input.addMapping(MAP_LEFT, new KeyTrigger(KeyInput.KEY_A));
        if (!input.hasMapping(MAP_RIGHT)) input.addMapping(MAP_RIGHT, new KeyTrigger(KeyInput.KEY_D));
        if (!input.hasMapping(MAP_JUMP)) input.addMapping(MAP_JUMP, new KeyTrigger(KeyInput.KEY_SPACE));
        if (!input.hasMapping(MAP_SPRINT)) input.addMapping(MAP_SPRINT, new KeyTrigger(KeyInput.KEY_LSHIFT));
        input.removeListener(this);
        input.addListener(this, MAP_FORWARD, MAP_BACKWARD, MAP_LEFT, MAP_RIGHT, MAP_JUMP, MAP_SPRINT);
    }

    private void registerRawInput() {
        if (rawRegistered) return;
        rawListener = new RawInputListener() {
            @Override public void beginInput() {}
            @Override public void endInput() {}
            @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent evt) {}
            @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent evt) {}
            @Override public void onMouseMotionEvent(com.jme3.input.event.MouseMotionEvent evt) {}
            @Override public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent evt) {}
            @Override public void onTouchEvent(com.jme3.input.event.TouchEvent evt) {}
            @Override public void onKeyEvent(KeyInputEvent evt) {
                if (evt.getKeyCode() == KeyInput.KEY_R && evt.isPressed()) {
                    onRawKeyR();
                    evt.setConsumed();
                }
                if (evt.getKeyCode() == KeyInput.KEY_V && evt.isPressed()) {
                    player.getCamControl().setThirdPerson(!player.getCamControl().isThirdPerson());
                    player.getCamEffectsControl().setThirdPerson(player.getCamControl().isThirdPerson());
                    evt.setConsumed();
                }
            }
        };
        input.addRawInputListener(rawListener);
        rawRegistered = true;
    }

    private void unregisterInput() { if (!inputRegistered) return; inputRegistered = false; input.removeListener(this); }
    private void unregisterRawInput() { if (!rawRegistered) return; input.removeRawInputListener(rawListener); rawRegistered = false; }
    @Override public void setSpatial(Spatial spatial) { super.setSpatial(spatial); if (spatial == null) { unregisterInput(); unregisterRawInput(); } }


    @Override
    protected void controlUpdate(float tpf) {
        if (spatial == null) return;

        // --- Standard movement calculation (unchanged) ---
        tempDir.set(0, 0, 0);
        if (forward) tempDir.z += 1f;
        if (backward) tempDir.z -= 1f;
        if (left) tempDir.x += 1f;
        if (right) tempDir.x -= 1f;

        float targetSpeed = sprint ? sprintSpeed : walkSpeed;
        if (tempDir.lengthSquared() > 0f) {
            tempDir.normalizeLocal();
            player.getCam().getDirection(camDir).setY(0).normalizeLocal();
            player.getCam().getLeft(camLeft).setY(0).normalizeLocal();
            desiredVel.set(camDir).multLocal(tempDir.z).addLocal(camLeft.mult(tempDir.x)).normalizeLocal();
            desiredVel.multLocal(targetSpeed);
        } else {
            desiredVel.set(0, 0, 0);
            targetSpeed = 0f;
        }

        if (movementListener != null) {
            movementListener.onMove(targetSpeed);
        }

        float alpha = 1f - FastMath.pow(1f - smoothFactor, tpf * 60f);
        currentVel.interpolateLocal(desiredVel, alpha);
        character.setWalkDirection(currentVel);

        if (currentVel.lengthSquared() > 1e-4f) {
            Vector3f viewDir = new Vector3f(currentVel.x, 0, currentVel.z).normalizeLocal();
            character.setViewDirection(viewDir);
        }

        // --- NEW: Rhythmic, time-based footstep logic ---
        handleFootsteps(tpf);

        // --- Landing detection logic (unchanged) ---
        boolean inAir = !character.onGround();
        float posY = spatial.getWorldTranslation().y;
        if (inAir) {
            if (posY > lastY) peakY = max(peakY, posY);
        }
        if (wasInAir && !inAir && movementListener != null) {
            movementListener.onLanding(FastMath.abs(peakY - lastY));
            peakY = 0f;
        }
        wasInAir = inAir;
        lastY = posY;

        if (player.getPlayerHud() != null) {
            player.getPlayerHud().setPlayerSpeed(getCurrentSpeed());
        }
    }

    /**
     * Handles footstep events based on a rhythmic time interval.
     */
    private void handleFootsteps(float tpf) {
        if (character.onGround() && isMoving()) {
            // Increment the timer
            timeSinceLastStep += tpf;

            // Determine the required time interval for the next step
            float requiredInterval = isSprinting() ? SPRINT_STEP_INTERVAL_SECONDS : WALK_STEP_INTERVAL_SECONDS;

            // If the timer has exceeded the interval, trigger a step
            if (timeSinceLastStep >= requiredInterval) {
                if (movementListener != null) {
                    movementListener.onStep();
                }
                // Subtract the interval from the timer, preserving any leftover time.
                // This keeps the rhythm consistent even with fluctuating frame rates.
                timeSinceLastStep -= requiredInterval;
            }
        } else {
            // Reset the timer if the player is in the air or has stopped.
            // This ensures the first step after landing or starting to move feels responsive.
            timeSinceLastStep = 0f;
        }
    }

    // --- onAction and other methods (unchanged) ---
    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case MAP_FORWARD -> forward = isPressed;
            case MAP_BACKWARD -> backward = isPressed;
            case MAP_LEFT -> left = isPressed;
            case MAP_RIGHT -> right = isPressed;
            case MAP_SPRINT -> sprint = isPressed;
            case MAP_JUMP -> {
                if (isPressed && character.onGround()) {
                    character.jump();
                    peakY = spatial.getWorldTranslation().y;
                    if (movementListener != null) {
                        movementListener.onJumpStart();
                    }
                }
            }
        }
    }

    private void onRawKeyR() { System.out.println("RawInputListener: R was pressed!"); }
    @Override protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {}
    public float getCurrentSpeed() { return currentVel.length(); }
    public boolean isMoving() { return getCurrentSpeed() > 1e-4f; }
    public boolean isWalking() { return isMoving() && !sprint; }
    public boolean isSprinting() { return isMoving() && sprint; }
    public void setMovementListener(MovementListener movementListener) { this.movementListener = movementListener; }
    public PlayerState getPlayerState() { return new PlayerState(isMoving(), isSprinting(), !character.onGround(), getCurrentSpeed(), currentVel.clone(), spatial.getWorldTranslation().clone()); }
}