package org.foxesworld.cge.modules.player;

import com.jme3.math.Vector3f;

/**
 * PlayerState encapsulates the current state of the player for querying and UI.
 * It provides a snapshot of motion, speed, and relevant flags.
 * Now also provides a "type" string: "Idle", "Moving", "Jumping", "Sprinting".
 */
public class PlayerState {
    private final boolean moving;
    private final boolean sprinting;
    private final boolean inAir;
    private final float currentSpeed;
    private final Vector3f velocity;
    private final Vector3f position;

    public PlayerState(boolean moving, boolean sprinting, boolean inAir,
                       float currentSpeed, Vector3f velocity, Vector3f position) {
        this.moving = moving;
        this.sprinting = sprinting;
        this.inAir = inAir;
        this.currentSpeed = currentSpeed;
        this.velocity = velocity.clone();
        this.position = position.clone();
    }

    public boolean isMoving() { return moving; }
    public boolean isSprinting() { return sprinting; }
    public boolean isInAir() { return inAir; }
    public float getCurrentSpeed() { return currentSpeed; }
    public Vector3f getVelocity() { return velocity; }
    public Vector3f getPosition() { return position; }

    /**
     * Returns the current player state type as a string:
     * "Idle", "Moving", "Jumping", "Sprinting"
     */
    public String getType() {
        if (inAir) return "Jumping";
        if (sprinting && moving) return "Sprinting";
        if (moving) return "Moving";
        return "Idle";
    }
}