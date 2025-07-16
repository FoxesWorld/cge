package org.foxesworld.cge.modules.player.control.camEffects.springs;

/**
 * A specialized, highly optimized damped spring for single float values.
 * Used for creating fluid motion for values like camera roll or Field of View.
 */
public class ScalarDampedSpring {

    private float position;
    private float velocity;
    private final float stiffness;
    private final float damping;

    public ScalarDampedSpring(float initialPosition, float stiffness, float damping) {
        this.position = initialPosition;
        this.velocity = 0f;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public void update(float tpf, float targetPosition) {
        if (tpf <= 0) return;
        float dt = Math.min(tpf, 0.032f);

        float displacement = position - targetPosition;
        float springForce = displacement * -stiffness;
        float dampingForce = velocity * -damping;
        float totalForce = springForce + dampingForce;

        velocity += totalForce * dt;
        position += velocity * dt;
    }

    public void addImpulse(float impulse) {
        this.velocity += impulse;
    }

    public void reset(float position) {
        this.position = position;
        this.velocity = 0f;
    }

    public float getPosition() {
        return position;
    }
}