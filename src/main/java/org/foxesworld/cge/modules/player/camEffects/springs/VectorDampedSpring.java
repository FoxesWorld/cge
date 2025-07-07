package org.foxesworld.cge.modules.player.camEffects.springs;

import com.jme3.math.Vector3f;

/**
 * A specialized, highly optimized damped spring for Vector3f values.
 * Used for creating fluid, inertial motion for camera positions and offsets.
 */
public class VectorDampedSpring {

    private final Vector3f position;
    private final Vector3f velocity;
    private final float stiffness;
    private final float damping;

    public VectorDampedSpring(Vector3f initialPosition, float stiffness, float damping) {
        this.position = initialPosition.clone();
        this.velocity = new Vector3f();
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public void update(float tpf, Vector3f targetPosition) {
        if (tpf <= 0) return;
        float dt = Math.min(tpf, 0.032f); // Clamp timestep for stability

        // F = -k * x - c * v
        Vector3f displacement = position.subtract(targetPosition);
        Vector3f springForce = displacement.mult(-stiffness);
        Vector3f dampingForce = velocity.mult(-damping);
        Vector3f totalForce = springForce.add(dampingForce);

        // v += F * t
        velocity.addLocal(totalForce.mult(dt));
        // p += v * t
        position.addLocal(velocity.mult(dt));
    }

    public void addImpulse(Vector3f impulse) {
        this.velocity.addLocal(impulse);
    }

    public void reset(Vector3f position) {
        this.position.set(position);
        this.velocity.set(0, 0, 0);
    }

    public Vector3f getPosition() {
        return position;
    }
}