package org.foxesworld.cge.modules.physics.body.rigid;

import com.jme3.app.Application;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.physics.PhysicsConfig;
import org.foxesworld.cge.modules.physics.PhysicsModule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles rigid-body physics: creates bodies, applies forces/impulses, and syncs transforms.
 * Reads default mass, friction, restitution, and damping from PhysicsConfig.
 */
public class RigidBodyModule extends EngineModule<PhysicsConfig> {
    private static final Logger logger = LogManager.getLogger(RigidBodyModule.class);
    private final Map<Spatial, RigidBodyControl> bodies = new ConcurrentHashMap<>();
    private final PhysicsModule physicsModule;

    public RigidBodyModule(PhysicsModule physicsModule) {
        super("physics", PhysicsConfig.class, physicsModule.getApp());
        this.physicsModule = physicsModule;
    }

    protected void initModule(CalistaGameEngine app) {
        PhysicsConfig config = physicsModule.getConfig();
        logger.info("RigidBodyModule init: defaultMass={}, friction={}, restitution={}, linearDamp={}, angularDamp={}",
                config.rigidDefaultMass, config.rigidFriction, config.rigidRestitution,
                config.rigidLinearDamping, config.rigidAngularDamping);
    }



    protected void updateModule(float tpf) {
        // Sync physics transforms to spatials
        bodies.forEach((spatial, control) -> {
            spatial.setLocalTranslation(control.getPhysicsLocation());
            spatial.setLocalRotation(control.getPhysicsRotation());
        });
    }

    protected void onConfigReloaded() {
        logger.info("RigidBodyModule config reloaded: defaultMass={}...", physicsModule.getConfig().rigidDefaultMass);
    }

    protected void cleanupModule(Application app) {
        bodies.values().forEach(ctrl -> {
            ctrl.getPhysicsSpace().remove(ctrl);
            ctrl.getSpatial().removeControl(ctrl);
        });
        bodies.clear();
        logger.info("RigidBodyModule cleaned up all bodies");
    }

    public void initialize(Application app) {
        // no operation: initModule handles setup
    }

    @Override
    protected void onEnable() {
    }

    public void onDisable() {
        cleanupModule(physicsModule.getApp());
    }

    /**
     * Adds a rigid-body to the physics space with given mass.
     */
    public void addRigidBody(Spatial spatial, float mass) {
        PhysicsConfig config = physicsModule.getConfig();
        if (mass <= 0f) {
            logger.warn("Attempting to add a rigid body with non-positive mass: {}", mass);
            mass = config.rigidDefaultMass;  // Use default mass if invalid value
        }

        RigidBodyControl control = new RigidBodyControl(mass);
        // Apply default material
        control.setFriction(config.rigidFriction);
        control.setRestitution(config.rigidRestitution);
        control.setDamping(config.rigidLinearDamping, config.rigidAngularDamping);
        spatial.addControl(control);
        physicsModule.getBulletAppState().getPhysicsSpace().add(control);
        bodies.put(spatial, control);
        logger.debug("Added rigid body '{}' with mass={} and friction={}", spatial.getName(), mass, config.rigidFriction);
    }

    /**
     * Adds an object to the scene with physics and collision setup.
     * This method will automatically apply default physics settings and add the object to the scene.
     */
    public void addPhysicsObjectToScene(Spatial spatial, float mass) {
        if (spatial != null) {
            // Set up the rigid body and collision properties
            addRigidBody(spatial, mass);

            // Optionally, you can set the initial position, rotation, or other properties
            spatial.setLocalTranslation(new Vector3f(0, 5, 0));  // Example position

            // Register the object with the BulletAppState and add it to the rootNode if necessary
            this.physicsModule.getApp().getRootNode().attachChild(spatial);
            logger.debug("Physics object '{}' added to the scene with mass {}", spatial.getName(), mass);
        } else {
            logger.warn("Failed to add physics object: spatial is null.");
        }
    }

    /**
     * Applies an impulse to the body at its center.
     */
    public void applyCentralImpulse(Spatial spatial, Vector3f impulse) {
        RigidBodyControl control = bodies.get(spatial);
        if (control != null) {
            control.applyCentralImpulse(impulse);
            logger.debug("Applied central impulse to '{}': {}", spatial.getName(), impulse);
        } else {
            logger.warn("RigidBodyControl not found for '{}'", spatial.getName());
        }
    }

    /**
     * Removes a rigid-body from the physics space.
     */
    public void removeRigidBody(Spatial spatial) {
        RigidBodyControl control = bodies.remove(spatial);
        if (control != null) {
            physicsModule.getBulletAppState().getPhysicsSpace().remove(control);
            spatial.removeControl(control);
            logger.debug("Removed rigid body '{}'", spatial.getName());
        } else {
            logger.warn("Failed to remove rigid body: '{}' not found.", spatial.getName());
        }
    }

    /**
     * Sets default mass for bodies added without explicit mass at runtime.
     */
    public void setDefaultMass(float mass) {
        physicsModule.getConfig().rigidDefaultMass = mass;
        logger.info("RigidBodyModule default mass set to {}", mass);
    }
}
