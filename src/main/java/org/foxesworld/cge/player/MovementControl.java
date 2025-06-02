package org.foxesworld.cge.player;

import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.*;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.control.AbstractControl;

/**
 * Handles player movement with smooth acceleration/deceleration and jump callbacks.
 * <ul>
 *   <li>Movement aligns to camera forward/left vectors on the horizontal plane.</li>
 *   <li>Smooth acceleration/deceleration toward a target velocity.</li>
 *   <li>Character rotation updates to face movement direction when moving.</li>
 *   <li>Sprinting by holding SHIFT increases max speed.</li>
 *   <li>Jumping is allowed whenever on ground.</li>
 *   <li>Jump and landing events notify a JumpListener.</li>
 * </ul>
 */
public class MovementControl extends AbstractControl implements ActionListener {

    public interface JumpListener {
        void onJumpStart();
        void onLanding(float peakHeight);
    }

    private static final String MAPPING_FORWARD  = "MoveForward";
    private static final String MAPPING_BACKWARD = "MoveBackward";
    private static final String MAPPING_LEFT     = "MoveLeft";
    private static final String MAPPING_RIGHT    = "MoveRight";
    private static final String MAPPING_JUMP     = "Jump";
    private static final String MAPPING_SPRINT   = "Sprint";

    private final Player player;
    private final CharacterControl character;
    private final InputManager input;
    private final Camera cam;
    private JumpListener jumpListener;

    private final float walkSpeed;
    private final float sprintSpeed;
    private final float acceleration;
    private final float deceleration;

    private boolean forward, backward, left, right, sprint;

    private final Vector3f currentVel = new Vector3f();
    private final Vector3f desiredVel = new Vector3f();
    private final Vector3f camForward  = new Vector3f();
    private final Vector3f camLeft     = new Vector3f();
    private final Vector3f moveDir     = new Vector3f();
    private final Vector3f delta       = new Vector3f();
    private final Vector3f faceDir     = new Vector3f();

    private boolean wasInAir = false;
    private float lastY      = 0f;
    private float jumpPeak   = 0f;

    /**
     * @param player       Player instance containing CharacterControl, InputManager, Camera, and HUD
     * @param walkSpeed    walking speed (m/s)
     * @param sprintSpeed  sprint speed (m/s)
     * @param acceleration acceleration rate (m/s²)
     * @param deceleration deceleration rate (m/s²)
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

    private void initInputMappings() {
        input.addMapping(MAPPING_FORWARD,  new KeyTrigger(KeyInput.KEY_W));
        input.addMapping(MAPPING_BACKWARD, new KeyTrigger(KeyInput.KEY_S));
        input.addMapping(MAPPING_LEFT,     new KeyTrigger(KeyInput.KEY_A));
        input.addMapping(MAPPING_RIGHT,    new KeyTrigger(KeyInput.KEY_D));
        input.addMapping(MAPPING_JUMP,     new KeyTrigger(KeyInput.KEY_SPACE));
        input.addMapping(MAPPING_SPRINT,   new KeyTrigger(KeyInput.KEY_LSHIFT));
        input.addListener(this,
                MAPPING_FORWARD, MAPPING_BACKWARD, MAPPING_LEFT,
                MAPPING_RIGHT, MAPPING_JUMP, MAPPING_SPRINT
        );
    }

    @Override
    protected void controlUpdate(float tpf) {
        // Determine movement direction
        moveDir.set(0, 0, 0);
        if (forward)  moveDir.z += 1f;
        if (backward) moveDir.z -= 1f;
        if (left)     moveDir.x += 1f;
        if (right)    moveDir.x -= 1f;

        if (!moveDir.equals(Vector3f.ZERO)) {
            moveDir.normalizeLocal();
            cam.getDirection(camForward).setY(0).normalizeLocal();
            cam.getLeft(camLeft).setY(0).normalizeLocal();
            desiredVel.set(camForward).multLocal(moveDir.z).addLocal(camLeft.mult(moveDir.x));

            float maxSpeed = sprint ? sprintSpeed : walkSpeed;
            desiredVel.multLocal(maxSpeed);

            delta.set(desiredVel).subtractLocal(currentVel);
            float accelRate = ((desiredVel.lengthSquared() > currentVel.lengthSquared())
                    ? acceleration : deceleration) * tpf;
            if (delta.lengthSquared() > accelRate * accelRate) {
                delta.normalizeLocal().multLocal(accelRate);
            }
            currentVel.addLocal(delta);

            character.setWalkDirection(currentVel);

            faceDir.set(currentVel.x, 0, currentVel.z);
            if (faceDir.lengthSquared() > 1e-4f) {
                character.setViewDirection(faceDir.normalizeLocal());
            }
        } else {
            if (currentVel.lengthSquared() > 1e-4f) {
                delta.set(currentVel).normalizeLocal().negateLocal().multLocal(deceleration * tpf);
                if (delta.lengthSquared() > currentVel.lengthSquared()) {
                    currentVel.set(0, 0, 0);
                } else {
                    currentVel.addLocal(delta);
                }
                character.setWalkDirection(currentVel);
            } else {
                currentVel.set(0, 0, 0);
                character.setWalkDirection(Vector3f.ZERO);
            }
        }

        // Jump/landing detection
        float currentY = character.getPhysicsLocation().y;
        boolean inAir = !character.onGround();
        if (inAir) {
            float deltaY = currentY - lastY;
            if (deltaY > 0) {
                jumpPeak = Math.max(jumpPeak, currentY);
            }
        }
        if (wasInAir && !inAir && jumpListener != null) {
            float peakHeight = jumpPeak - lastY;
            jumpListener.onLanding(peakHeight);
            jumpPeak = 0f;
        }
        wasInAir = inAir;
        lastY   = currentY;

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
     * Sets a listener to receive jump start and landing events.
     */
    public void setJumpListener(JumpListener listener) {
        this.jumpListener = listener;
    }

    /**
     * Returns the player's current horizontal speed (m/s).
     */
    public float getCurrentSpeed() {
        return (float) Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);
    }

    /**
     * Returns true if the player is moving horizontally.
     */
    public boolean isMoving() {
        return currentVel.x * currentVel.x + currentVel.z * currentVel.z > 1e-4f;
    }
}
