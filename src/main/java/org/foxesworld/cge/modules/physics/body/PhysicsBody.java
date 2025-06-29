// Создайте этот новый файл: PhysicsBody.java
package org.foxesworld.cge.modules.physics.body;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

import java.util.function.Consumer;

/**
 * A wrapper for a Spatial and its RigidBodyControl, providing a clean,
 * object-oriented API for physics interactions.
 */
public class PhysicsBody {
    private final Spatial spatial;
    private final RigidBodyControl control;
    private final Consumer<PhysicsBody> removalCallback;

    public PhysicsBody(Spatial spatial, RigidBodyControl control, Consumer<PhysicsBody> removalCallback) {
        this.spatial = spatial;
        this.control = control;
        this.removalCallback = removalCallback;
    }

    public Spatial getSpatial() {
        return spatial;
    }

    public RigidBodyControl getControl() {
        return control;
    }

    // --- Fluent API for Physics Properties ---

    public PhysicsBody setFriction(float friction) {
        control.setFriction(friction);
        return this;
    }

    public PhysicsBody setRestitution(float restitution) {
        control.setRestitution(restitution);
        return this;
    }

    public PhysicsBody setDamping(float linear, float angular) {
        control.setDamping(linear, angular);
        return this;
    }

    public PhysicsBody setKinematic(boolean kinematic) {
        control.setKinematic(kinematic);
        return this;
    }

    public boolean isKinematic() {
        return control.isKinematic();
    }

    // --- Fluent API for Physics Actions ---

    public PhysicsBody applyCentralImpulse(Vector3f impulse) {
        control.applyCentralImpulse(impulse);
        return this;
    }

    public PhysicsBody applyForce(Vector3f force, Vector3f location) {
        control.applyForce(force, location);
        return this;
    }

    public PhysicsBody setLinearVelocity(Vector3f velocity) {
        control.setLinearVelocity(velocity);
        return this;
    }

    /**
     * Safely removes this body from the physics space and the scene graph.
     */
    public void remove() {
        removalCallback.accept(this);
    }
}