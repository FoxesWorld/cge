package org.foxesworld.cge.player;

import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.*;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.control.AbstractControl;

/**
 * MovementControl handles player movement based on camera orientation, with smooth acceleration/deceleration,
 * jump detection/callbacks, and optional sprinting. Inspired by Frostbite-style movement:
 * <ul>
 *     <li>Movement aligns to camera forward/left vectors (projected onto horizontal plane).</li>
 *     <li>Smooth acceleration and deceleration blend towards a target velocity.</li>
 *     <li>Character rotation is updated to face movement direction when moving.</li>
 *     <li>Sprinting via holding SHIFT increases max speed.</li>
 *     <li>Jump and landing events notify a JumpListener.</li>
 * </ul>
 */
public class MovementControl extends AbstractControl implements ActionListener {

    public interface JumpListener {
        void onJumpStart();
        void onLanding(float peakHeight);
    }

    // Input action names
    private static final String MAPPING_FORWARD   = "MoveForward";
    private static final String MAPPING_BACKWARD  = "MoveBackward";
    private static final String MAPPING_LEFT      = "MoveLeft";
    private static final String MAPPING_RIGHT     = "MoveRight";
    private static final String MAPPING_JUMP      = "Jump";
    private static final String MAPPING_SPRINT    = "Sprint";

    private final Player            player;
    private final CharacterControl  character;
    private final InputManager      input;
    private final Camera            cam;
    private JumpListener            jumpListener;

    // Movement configuration (units: m/s and m/s²)
    private final float walkSpeed;        // normal walking speed
    private final float sprintSpeed;      // sprinting speed
    private final float acceleration;     // acceleration rate
    private final float deceleration;     // deceleration rate

    // Input state
    private boolean forward, backward, left, right, sprint;

    // Velocity vectors (reused to avoid per-frame allocations)
    private final Vector3f currentVel = new Vector3f();
    private final Vector3f desiredVel = new Vector3f();
    private final Vector3f camForward = new Vector3f();
    private final Vector3f camLeft    = new Vector3f();
    private final Vector3f moveDir    = new Vector3f();

    // Jump/landing detection
    private boolean wasInAir = false;
    private float lastY      = 0f;
    private float jumpPeak   = 0f;

    /**
     * Constructs a MovementControl.
     *
     * @param player       the Player instance containing CharacterControl, InputManager, Camera, and HUD
     * @param walkSpeed    walking speed in m/s
     * @param sprintSpeed  sprint speed in m/s
     * @param acceleration acceleration rate in m/s²
     * @param deceleration deceleration rate in m/s²
     */
    public MovementControl(Player player,
                           float walkSpeed,
                           float sprintSpeed,
                           float acceleration,
                           float deceleration) {
        this.player       = player;
        this.walkSpeed    = walkSpeed;
        this.sprintSpeed  = sprintSpeed;
        this.acceleration = acceleration;
        this.deceleration = deceleration;

        this.character = player.getCharacter();
        this.input     = player.getInput();
        this.cam       = player.getCam();

        initInputMappings();
    }

    /**
     * Binds input mappings and registers this as ActionListener.
     */
    private void initInputMappings() {
        input.addMapping(MAPPING_FORWARD,   new KeyTrigger(KeyInput.KEY_W));
        input.addMapping(MAPPING_BACKWARD,  new KeyTrigger(KeyInput.KEY_S));
        input.addMapping(MAPPING_LEFT,      new KeyTrigger(KeyInput.KEY_A));
        input.addMapping(MAPPING_RIGHT,     new KeyTrigger(KeyInput.KEY_D));
        input.addMapping(MAPPING_JUMP,      new KeyTrigger(KeyInput.KEY_SPACE));
        input.addMapping(MAPPING_SPRINT,    new KeyTrigger(KeyInput.KEY_LSHIFT));
        input.addListener(this,
                MAPPING_FORWARD, MAPPING_BACKWARD, MAPPING_LEFT,
                MAPPING_RIGHT, MAPPING_JUMP, MAPPING_SPRINT
        );
    }

    @Override
    protected void controlUpdate(float tpf) {
        // 1) Determine raw movement direction from input (X = left/right, Z = forward/back)
        moveDir.set(0, 0, 0);
        if (forward)  moveDir.z += 1f;
        if (backward) moveDir.z -= 1f;
        if (left)     moveDir.x += 1f;
        if (right)    moveDir.x -= 1f;

        if (!moveDir.equals(Vector3f.ZERO)) {
            moveDir.normalizeLocal();

            // 2) Project camera basis onto horizontal plane and normalize
            cam.getDirection(camForward).setY(0).normalizeLocal();
            cam.getLeft(camLeft).setY(0).normalizeLocal();

            // 3) Compute desired velocity: combine camera-forward and camera-left
            desiredVel.set(camForward).multLocal(moveDir.z).addLocal(camLeft.mult(moveDir.x));

            // 4) Scale by sprint or walk speed
            float maxSpeed = sprint ? sprintSpeed : walkSpeed;
            desiredVel.multLocal(maxSpeed);

            // 5) Smoothly accelerate or decelerate toward desiredVel
            Vector3f delta = desiredVel.subtract(currentVel);
            float accelRate = (desiredVel.lengthSquared() > currentVel.lengthSquared()
                    ? acceleration : deceleration) * tpf;

            if (delta.lengthSquared() > accelRate * accelRate) {
                delta.normalizeLocal().multLocal(accelRate);
            }
            currentVel.addLocal(delta);

            // 6) Update character walk direction
            character.setWalkDirection(currentVel);

            // 7) Rotate character to face movement direction (yaw only)
            Vector3f faceDir = new Vector3f(currentVel.x, 0, currentVel.z);
            if (faceDir.lengthSquared() > 1e-4f) {
                character.setViewDirection(faceDir.normalizeLocal());
            }
        } else {
            // No input: decelerate to zero
            if (currentVel.lengthSquared() > 1e-4f) {
                Vector3f delta = currentVel.normalize().negate().multLocal(deceleration * tpf);
                if (delta.lengthSquared() > currentVel.lengthSquared()) {
                    currentVel.set(0, 0, 0);
                } else {
                    currentVel.addLocal(delta);
                }
                character.setWalkDirection(currentVel);
            } else {
                // Fully stopped
                currentVel.set(0, 0, 0);
                character.setWalkDirection(Vector3f.ZERO);
            }
        }

        // 8) Jump/landing detection
        float currentY = character.getPhysicsLocation().y;
        boolean inAir = !character.onGround();
        if (inAir) {
            float deltaY = currentY - lastY;
            if (deltaY > 0) {
                // ascending
                jumpPeak = Math.max(jumpPeak, currentY);
            }
        }
        if (wasInAir && !inAir && jumpListener != null) {
            float peakHeight = jumpPeak - lastY;
            jumpListener.onLanding(peakHeight);
            jumpPeak = 0f;
        }
        wasInAir = inAir;
        lastY    = currentY;

        // 9) Update HUD with horizontal speed
        player.getPlayerHud().setPlayerSpeed(getCurrentSpeed());
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case MAPPING_FORWARD  -> forward  = isPressed;
            case MAPPING_BACKWARD -> backward = isPressed;
            case MAPPING_LEFT     -> left     = isPressed;
            case MAPPING_RIGHT    -> right    = isPressed;
            case MAPPING_SPRINT   -> sprint   = isPressed;
            case MAPPING_JUMP -> {
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

    /**
     * Sets a JumpListener to receive jump start and landing events.
     *
     * @param listener listener implementation
     */
    public void setJumpListener(JumpListener listener) {
        this.jumpListener = listener;
    }

    /**
     * Returns the player's current horizontal speed (m/s).
     *
     * @return current ground-plane speed
     */
    public float getCurrentSpeed() {
        // horizontal speed magnitude
        return (float) Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);
    }

    /**
     * Returns the player's current maximum speed (m/s), depending on sprint state.
     *
     * @return walkSpeed or sprintSpeed if sprinting
     */
    public float getMaxSpeed() {
        return sprint ? sprintSpeed : walkSpeed;
    }

    /**
     * Returns true if the player is currently moving (horizontal velocity > small threshold).
     *
     * @return true if walking or sprinting
     */
    public boolean isMoving() {
        return currentVel.x * currentVel.x + currentVel.z * currentVel.z > 1e-4f;
    }
}