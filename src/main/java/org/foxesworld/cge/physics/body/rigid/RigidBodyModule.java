package org.foxesworld.cge.physics.body.rigid;

import com.jme3.app.Application;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.physics.PhysicsConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles rigid-body physics: creates bodies, applies forces/impulses, and syncs transforms.
 * Reads default mass, friction, restitution, and damping from PhysicsConfig.
 */
public class RigidBodyModule extends EngineModule<PhysicsConfig> {
    private static final Logger LOGGER = LogManager.getLogger(RigidBodyModule.class);

    private final Map<Spatial, RigidBodyControl> bodies = new ConcurrentHashMap<>();
    private BulletAppState bulletState;
    private PhysicsConfig config;

    public RigidBodyModule(CalistaGameEngine application) {
        super("physics", PhysicsConfig.class, application);
        initialize(application);
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        // Load config
       // config = loadConfig();
        bulletState = app.getStateManager().getState(BulletAppState.class);
        LOGGER.info("RigidBodyModule init: defaultMass={}, friction={}, restitution={}, linearDamp={}, angularDamp={}",
                config.rigidDefaultMass, config.rigidFriction, config.rigidRestitution,
                config.rigidLinearDamping, config.rigidAngularDamping);
    }

    @Override
    protected void onEnable() {
        // Module enabled
    }

    @Override
    protected void updateModule(float tpf) {
        // Sync physics transforms to spatials
        bodies.forEach((spatial, control) -> {
            spatial.setLocalTranslation(control.getPhysicsLocation());
            spatial.setLocalRotation(control.getPhysicsRotation());
        });
    }

    @Override
    protected void onConfigReloaded() {
        //config = loadConfig();
        LOGGER.info("RigidBodyModule config reloaded: defaultMass={}...", config.rigidDefaultMass);
    }

    @Override
    protected void cleanupModule(Application app) {
        // Remove and clear all bodies
        bodies.values().forEach(ctrl -> {
            ctrl.getPhysicsSpace().remove(ctrl);
            ctrl.getSpatial().removeControl(ctrl);
        });
        bodies.clear();
        LOGGER.info("RigidBodyModule cleaned up all bodies");
    }

    @Override
    public void initialize(Application app) {
        // no operation: initModule handles setup
    }

    @Override
    public void onDisable() {
        // Remove bodies on disable
        cleanupModule(getApplication());
    }

    /**
     * Adds a rigid-body to the physics space with given mass.
     */
    public void addRigidBody(Spatial spatial, float mass) {
        float m = mass > 0f ? mass : config.rigidDefaultMass;
        RigidBodyControl control = new RigidBodyControl(m);
        // apply default material
        control.setFriction(config.rigidFriction);
        control.setRestitution(config.rigidRestitution);
        control.setDamping(config.rigidLinearDamping, config.rigidAngularDamping);
        spatial.addControl(control);
        bulletState.getPhysicsSpace().add(control);
        bodies.put(spatial, control);
        LOGGER.debug("Added rigid body '{}' with mass={} and friction={}", spatial.getName(), m, config.rigidFriction);
    }

    /**
     * Applies an impulse to the body at its center.
     */
    public void applyCentralImpulse(Spatial spatial, Vector3f impulse) {
        RigidBodyControl control = bodies.get(spatial);
        if (control != null) {
            control.applyCentralImpulse(impulse);
        }
    }

    /**
     * Removes a rigid-body from the physics space.
     */
    public void removeRigidBody(Spatial spatial) {
        RigidBodyControl control = bodies.remove(spatial);
        if (control != null) {
            bulletState.getPhysicsSpace().remove(control);
            spatial.removeControl(control);
            LOGGER.debug("Removed rigid body '{}'", spatial.getName());
        }
    }

    /**
     * Sets default mass for bodies added without explicit mass at runtime.
     */
    public void setDefaultMass(float mass) {
        config.rigidDefaultMass = mass;
    }
}