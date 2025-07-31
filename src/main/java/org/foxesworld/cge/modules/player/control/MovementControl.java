package org.foxesworld.cge.modules.player.control;

import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import org.foxesworld.cge.modules.inputManager.InputManagerModule;
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
 * <p>
 * Input is handled by polling the state of actions from the central {@link InputManagerModule},
 * making controls fully configurable via external files.
 */
public final class MovementControl extends AbstractControl {

    /**
     * A listener interface for receiving key movement events from the {@link MovementControl}.
     * This serves as the primary communication channel to other systems, such as an animation controller.
     */
    public interface MovementListener {
        void onJumpStart();
        void onLanding(float fallHeight);
        void onMove(float targetSpeed);
        void onStep();
    }

    private static final float WALK_STEP_INTERVAL_SECONDS = 0.5f;
    private static final float SPRINT_STEP_INTERVAL_SECONDS = 0.35f;

    private final Player player;
    private final CharacterControl character;
    private final InputManager input;
    private final InputManagerModule inputModule;
    private final float walkSpeed;
    private final float sprintSpeed;
    private final float smoothFactor;

    private final Vector3f currentVel = new Vector3f();
    private final Vector3f desiredVel = new Vector3f();
    private final Vector3f camDir = new Vector3f();
    private final Vector3f camLeft = new Vector3f();
    private final Vector3f tempDir = new Vector3f();

    private boolean wasInAir = false;
    private float lastY = 0f;
    private float peakY = 0f;
    private float timeSinceLastStep = 0f;
    private RawInputListener rawListener;
    private MovementListener movementListener;

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
        this.inputModule = player.getEngine().getModuleManager().getModule(InputManagerModule.class);

        if (this.inputModule == null) {
            throw new IllegalStateException("InputManagerModule not found. It must be registered before the Player module.");
        }

        this.walkSpeed = movementConfig.getWalkSpeed();
        this.sprintSpeed = movementConfig.getSprintSpeed();
        this.smoothFactor = FastMath.clamp(movementConfig.getSmoothing(), 0f, 1f);

        registerRawInput();
    }

    @Override
    protected void controlUpdate(float tpf) {
        if (spatial == null) return;

        updateMovement(tpf);
        updateViewDirection();
        handleLanding();
        handleFootsteps(tpf);
        updateHud();
    }

    /**
     * Calculates and applies the character's movement vector based on input and smoothing.
     */
    private void updateMovement(float tpf) {
        if (inputModule.isActionActive("jump") && character.onGround()) {
            character.jump();
            peakY = spatial.getWorldTranslation().y;
            if (movementListener != null) {
                movementListener.onJumpStart();
            }
        }

        tempDir.set(0, 0, 0);
        if (inputModule.isActionActive("move_forward")) tempDir.z += 1f;
        if (inputModule.isActionActive("move_backward")) tempDir.z -= 1f;
        if (inputModule.isActionActive("strafe_left")) tempDir.x -= 1f;
        if (inputModule.isActionActive("strafe_right")) tempDir.x += 1f;

        float targetSpeed = isSprinting() ? sprintSpeed : walkSpeed;

        if (tempDir.lengthSquared() > 0) {
            tempDir.normalizeLocal();
            player.getCam().getDirection(camDir).setY(0).normalizeLocal();
            player.getCam().getLeft(camLeft).setY(0).normalizeLocal();

            desiredVel.set(0, 0, 0);
            desiredVel.addLocal(camDir.mult(tempDir.z));
            desiredVel.addLocal(camLeft.mult(-tempDir.x));
            desiredVel.normalizeLocal().multLocal(targetSpeed);
        } else {
            desiredVel.set(0, 0, 0);
            targetSpeed = 0;
        }

        if (movementListener != null) {
            movementListener.onMove(targetSpeed);
        }

        float alpha = 1f - FastMath.pow(1f - smoothFactor, tpf * 60f);
        currentVel.interpolateLocal(desiredVel, alpha);
        character.setWalkDirection(currentVel);
    }

    /**
     * Orients the character model to face the direction of movement.
     */
    private void updateViewDirection() {
        if (currentVel.lengthSquared() > 1e-4f) {
            Vector3f viewDir = new Vector3f(currentVel.x, 0, currentVel.z).normalizeLocal();
            character.setViewDirection(viewDir);
        }
    }

    /**
     * Handles rhythmic footstep events based on a time interval.
     */
    private void handleFootsteps(float tpf) {
        if (character.onGround() && isMoving()) {
            timeSinceLastStep += tpf;
            float requiredInterval = isSprinting() ? SPRINT_STEP_INTERVAL_SECONDS : WALK_STEP_INTERVAL_SECONDS;

            if (timeSinceLastStep >= requiredInterval) {
                if (movementListener != null) {
                    movementListener.onStep();
                }
                timeSinceLastStep -= requiredInterval;
            }
        } else {
            timeSinceLastStep = 0f;
        }
    }

    /**
     * Detects when the character lands after a fall and notifies the listener.
     */
    private void handleLanding() {
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
    }

    /**
     * Updates the player's HUD with the current speed.
     */
    private void updateHud() {
        if (player.getPlayerHud() != null) {
            player.getPlayerHud().setPlayerSpeed(getCurrentSpeed());
        }
    }

    /**
     * Registers a raw input listener for handling debug keys.
     */
    private void registerRawInput() {
        if (rawListener != null) return;
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
    }

    private void unregisterRawInput() {
        if (rawListener == null) return;
        input.removeRawInputListener(rawListener);
        rawListener = null;
    }

    @Override
    public void setSpatial(Spatial spatial) {
        super.setSpatial(spatial);
        if (spatial == null) {
            unregisterRawInput();
        }
    }

    private void onRawKeyR() {
        System.out.println("RawInputListener: R was pressed!");
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {}

    public float getCurrentSpeed() {
        return currentVel.length();
    }

    public boolean isMoving() {
        return getCurrentSpeed() > 1e-4f;
    }

    public boolean isWalking() {
        return isMoving() && !isSprinting();
    }

    public boolean isSprinting() {
        return isMoving() && inputModule.isActionActive("sprint");
    }

    public void setMovementListener(MovementListener movementListener) {
        this.movementListener = movementListener;
    }

    public PlayerState getPlayerState() {
        return new PlayerState(isMoving(), isSprinting(), !character.onGround(), getCurrentSpeed(), currentVel.clone(), spatial.getWorldTranslation().clone());
    }
}