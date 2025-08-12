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
        void onStay();
        /**
         * Basic legacy step notification.
         */
        void onStep(float targetSpeed);

        /**
         * Optional richer step notification that indicates which foot "struck".
         * Default implementation forwards to the legacy onStep() for backward compatibility.
         */
        //default void onStep(boolean leftFoot) { onStep(); }
    }

    private static final float WALK_STEP_INTERVAL_SECONDS = 0.50f;
    private static final float SPRINT_STEP_INTERVAL_SECONDS = 0.35f;
    private static final float STEP_INTERVAL_MIN = 0.15f; // clamp for very high speeds
    private static final float STEP_INTERVAL_MAX = 0.8f;  // clamp for very slow speeds
    private static final float MIN_FALL_FOR_LAND_EVENT = 0.25f; // meters

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
    private float fallStartY = 0f; // Y when the body left the ground

    private float timeSinceLastStep = 0f;
    private boolean stepLeft = true; // alternate left/right foot
    private boolean wasMoving = false;

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

        // handle jump input separately for clarity and future extensions (double-jump, variable jump, etc.)
        handleJump();

        updateMovement(tpf);
        updateViewDirection();
        handleLanding();
        handleFootsteps(tpf);
        updateHud();
    }

    /**
     * Separated jump handling: triggers physics jump, records start height and notifies listener.
     * This is extracted to make jump logic easier to extend (double-jump, charge jump, animation hooks).
     */
    private void handleJump() {
        // Only act on the moment the jump action is active while grounded.
        if (inputModule.isActionActive("jump") && character.onGround()) {
            player.getEngine().getSoundManager().play("player.takeoff");
            character.jump();
            // record Y at jump start to be used later for landing/fall distance calculation
            fallStartY = spatial.getWorldTranslation().y;
            if (movementListener != null) {
                movementListener.onJumpStart();
            }
        }
    }

    /**
     * Calculates and applies the character's movement vector based on input and smoothing.
     */
    private void updateMovement(float tpf) {
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
            if(player.getPhysicsHelper().isGrounded()) {
                movementListener.onStay();
            }
        }



        float alpha = 1f - FastMath.pow(1f - smoothFactor, tpf * 60f);
        currentVel.interpolateLocal(desiredVel, alpha);
        character.setWalkDirection(currentVel);

        // detect movement start to trigger an immediate footstep and keep step phase in sync
        boolean movingNow = currentVel.lengthSquared() > 1e-4f;
        if (!wasMoving && movingNow) {
            // trigger a half-interval so the first step feels immediate
            timeSinceLastStep = Math.max(0f, getStepInterval() * 0.5f);
            // optionally fire an immediate short step
            if (movementListener != null && character.onGround()) {
                //movementListener.onStep(stepLeft);
                // keep legacy callback for backward compatibility
                movementListener.onStep(currentVel.length());
                stepLeft = !stepLeft;
            }
        }
        wasMoving = movingNow;
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
     * Returns the dynamic step interval based on current speed. Faster speed => shorter interval.
     */
    private float getStepInterval() {
        float speed = getCurrentSpeed();
        if (speed <= 1e-4f) return STEP_INTERVAL_MAX;
        float base = isSprinting() ? SPRINT_STEP_INTERVAL_SECONDS : WALK_STEP_INTERVAL_SECONDS;
        // scale base interval inversely with speed relative to walkSpeed
        float interval = base * (walkSpeed / Math.max(speed, 1e-4f));
        return FastMath.clamp(interval, STEP_INTERVAL_MIN, STEP_INTERVAL_MAX);
    }

    /**
     * Handles rhythmic footstep events based on a variable interval derived from speed.
     */
    private void handleFootsteps(float tpf) {
        if (character.onGround() && isMoving()) {
            timeSinceLastStep += tpf;
            float requiredInterval = getStepInterval();

            if (timeSinceLastStep >= requiredInterval) {
                if (movementListener != null) {
                    //movementListener.onStep(stepLeft);
                    movementListener.onStep(currentVel.length()); // keep legacy behaviour
                }
                stepLeft = !stepLeft;
                timeSinceLastStep -= requiredInterval;
                // avoid large accumulation when frame spikes occur
                timeSinceLastStep = Math.min(timeSinceLastStep, requiredInterval);
            }
        } else {
            timeSinceLastStep = 0f;
            // reset step phase so starting to walk gives a predictable foot
            // (optional - comment out if you want to preserve phase across small stops)
            // stepLeft = true;
        }
    }

    /**
     * Detects when the character lands after a fall and notifies the listener.
     */
    private void handleLanding() {
        boolean inAir = !character.onGround();
        float posY = spatial.getWorldTranslation().y;

        // just left the ground
        if (inAir && !wasInAir) {
            fallStartY = lastY; // where we were when leaving ground
        }

        // just landed
        if (wasInAir && !inAir) {
            player.getEngine().getSoundManager().play("player.land");
            float fallDistance = fallStartY - posY;
            if (fallDistance > MIN_FALL_FOR_LAND_EVENT && movementListener != null) {
                movementListener.onLanding(fallDistance);
            }
            fallStartY = posY;
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
