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
 * AAA-level RigidBodyModule: robust, thread-safe, extensible, and highly debuggable.
 * Manages rigid-body physics: initialization, force application, removal, and sync.
 * Reads default mass, friction, restitution, and damping from PhysicsConfig.
 * Delegates property management and ensures native safety.
 */
public class RigidBodyModule extends EngineModule<PhysicsConfig> {
    private static final Logger logger = LogManager.getLogger(RigidBodyModule.class);
    private final Map<Spatial, RigidBodyControl> bodies = new ConcurrentHashMap<>();
    private final PhysicsModule physicsModule;

    public RigidBodyModule(PhysicsModule physicsModule) {
        super(RigidBodyModule.class, PhysicsConfig.class, physicsModule.getApp());
        this.physicsModule = physicsModule;
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        PhysicsConfig config = physicsModule.getConfig();
        logger.info("RigidBodyModule init: defaultMass={}, friction={}, restitution={}, linearDamp={}, angularDamp={}",
                config.rigidDefaultMass, config.rigidFriction, config.rigidRestitution,
                config.rigidLinearDamping, config.rigidAngularDamping);
    }

    @Override
    protected void updateModule(float tpf) {
        // Defensive, precise transform sync (thread-safe)
        bodies.forEach((spatial, control) -> {
            if (spatial != null && control != null) {
                try {
                    spatial.setLocalTranslation(control.getPhysicsLocation());
                    spatial.setLocalRotation(control.getPhysicsRotation());
                } catch (Exception e) {
                    logger.error("Failed to sync physics for '{}': {}", spatial.getName(), e.getMessage());
                }
            }
        });
    }

    @Override
    public void onConfigReloaded() {
        logger.info("RigidBodyModule config reloaded: defaultMass={}...", physicsModule.getConfig().rigidDefaultMass);
        // Optionally update all existing bodies with new config
        PhysicsConfig config = physicsModule.getConfig();
        bodies.forEach((spatial, ctrl) -> {
            if (ctrl != null) {
                safeSetFriction(ctrl, config.rigidFriction, spatial);
                safeSetRestitution(ctrl, config.rigidRestitution, spatial);
                safeSetDamping(ctrl, config.rigidLinearDamping, config.rigidAngularDamping, spatial);
            }
        });
    }

    @Override
    protected void cleanupModule(Application app) {
        bodies.forEach((spatial, ctrl) -> {
            try {
                if (ctrl.getPhysicsSpace() != null) {
                    ctrl.getPhysicsSpace().remove(ctrl);
                }
                if (ctrl.getSpatial() != null) {
                    ctrl.getSpatial().removeControl(ctrl);
                }
            } catch (Exception e) {
                logger.error("Error cleaning up rigid body '{}': {}", spatial != null ? spatial.getName() : "null", e.getMessage());
            }
        });
        bodies.clear();
        logger.info("RigidBodyModule cleaned up all bodies");
    }

    @Override
    public void initialize(Application app) {
        // No operation: initModule handles setup
    }

    @Override
    protected void onEnable() {
        // No operation
    }

    @Override
    public void onDisable() {
        cleanupModule(physicsModule.getApp());
    }

    /**
     * Adds a rigid-body to the physics space with given mass.
     * Returns the RigidBodyControl for further customization.
     * Thread-safe, prevents duplicates.
     */
    public RigidBodyControl addRigidBody(Spatial spatial, float mass) {
        if (spatial == null) {
            logger.warn("Cannot add rigid body: spatial is null.");
            return null;
        }
        if (bodies.containsKey(spatial)) {
            logger.warn("Spatial '{}' already has a rigid body.", spatial.getName());
            return bodies.get(spatial);
        }
        PhysicsConfig config = physicsModule.getConfig();
        if (mass <= 0f) {
            logger.warn("Attempting to add a rigid body with non-positive mass: {}. Using default: {}", mass, config.rigidDefaultMass);
            mass = config.rigidDefaultMass;
        }

        RigidBodyControl control = new RigidBodyControl(mass);
        spatial.addControl(control);
        try {
            physicsModule.getBulletAppState().getPhysicsSpace().add(control);

            // теперь physicsSpace не null, можно безопасно задавать параметры
            safeSetFriction(control, config.rigidFriction, spatial);
            safeSetRestitution(control, config.rigidRestitution, spatial);
            safeSetDamping(control, config.rigidLinearDamping, config.rigidAngularDamping, spatial);
            bodies.put(spatial, control);
            logger.debug("Added rigid body '{}' with mass={} and friction={}", spatial.getName(), mass, config.rigidFriction);
        } catch (Exception e) {
            logger.error("Failed to add rigid body '{}' to physics space: {}", spatial.getName(), e.getMessage());
        }
        return control;
    }

    public void setFriction(Spatial spatial, float friction) {
        RigidBodyControl ctrl = bodies.get(spatial);
        safeSetFriction(ctrl, friction, spatial);
    }

    public void setRestitution(Spatial spatial, float restitution) {
        RigidBodyControl ctrl = bodies.get(spatial);
        safeSetRestitution(ctrl, restitution, spatial);
    }

    public void setDamping(Spatial spatial, float linear, float angular) {
        RigidBodyControl ctrl = bodies.get(spatial);
        safeSetDamping(ctrl, linear, angular, spatial);
    }

    private void safeSetFriction(RigidBodyControl ctrl, float friction, Spatial spatial) {
        if (ctrl != null && ctrl.getPhysicsSpace() != null) {
            try {
                ctrl.setFriction(friction);
                logger.debug("Set friction={} for '{}'", friction, spatial != null ? spatial.getName() : "unknown");
            } catch (Exception e) {
                logger.error("Failed to set friction for '{}': {}", spatial != null ? spatial.getName() : "unknown", e.getMessage());
            }
        } else {
            logger.warn("Cannot set friction: control or physicsSpace is null for '{}'", spatial != null ? spatial.getName() : "unknown");
        }
    }

    private void safeSetRestitution(RigidBodyControl ctrl, float restitution, Spatial spatial) {
        if (ctrl != null && ctrl.getPhysicsSpace() != null) {
            try {
                ctrl.setRestitution(restitution);
                logger.debug("Set restitution={} for '{}'", restitution, spatial != null ? spatial.getName() : "unknown");
            } catch (Exception e) {
                logger.error("Failed to set restitution for '{}': {}", spatial != null ? spatial.getName() : "unknown", e.getMessage());
            }
        } else {
            logger.warn("Cannot set restitution: control or physicsSpace is null for '{}'", spatial != null ? spatial.getName() : "unknown");
        }
    }

    private void safeSetDamping(RigidBodyControl ctrl, float linear, float angular, Spatial spatial) {
        if (ctrl != null && ctrl.getPhysicsSpace() != null) {
            try {
                ctrl.setDamping(linear, angular);
                logger.debug("Set damping l={} a={} for '{}'", linear, angular, spatial != null ? spatial.getName() : "unknown");
            } catch (Exception e) {
                logger.error("Failed to set damping for '{}': {}", spatial != null ? spatial.getName() : "unknown", e.getMessage());
            }
        } else {
            logger.warn("Cannot set damping: control or physicsSpace is null for '{}'", spatial != null ? spatial.getName() : "unknown");
        }
    }

    /**
     * Adds an object to the scene with physics and collision setup.
     * Automatically applies default physics settings and attaches to the scene.
     */
    public void addPhysicsObjectToScene(Spatial spatial, float mass) {
        if (spatial == null) {
            logger.warn("Failed to add physics object: spatial is null.");
            return;
        }
        addRigidBody(spatial, mass);

        // Set initial position if not already set
        if (spatial.getLocalTranslation().equals(Vector3f.ZERO)) {
            spatial.setLocalTranslation(new Vector3f(0, 5, 0));
        }

        // Attach to scene
        this.physicsModule.getApp().getRootNode().attachChild(spatial);
        logger.debug("Physics object '{}' added to the scene with mass {}", spatial.getName(), mass);
    }

    /**
     * Applies an impulse to the body at its center.
     */
    public void applyCentralImpulse(Spatial spatial, Vector3f impulse) {
        RigidBodyControl control = bodies.get(spatial);
        if (control != null && impulse != null) {
            control.applyCentralImpulse(impulse);
            logger.debug("Applied central impulse to '{}': {}", spatial.getName(), impulse);
        } else {
            logger.warn("RigidBodyControl not found or impulse is null for '{}'", spatial != null ? spatial.getName() : "null");
        }
    }

    /**
     * Removes a rigid-body from the physics space.
     */
    public void removeRigidBody(Spatial spatial) {
        RigidBodyControl control = bodies.remove(spatial);
        if (control != null) {
            try {
                physicsModule.getBulletAppState().getPhysicsSpace().remove(control);
                spatial.removeControl(control);
                logger.debug("Removed rigid body '{}'", spatial.getName());
            } catch (Exception e) {
                logger.error("Failed to remove rigid body '{}': {}", spatial.getName(), e.getMessage());
            }
        } else {
            logger.warn("Failed to remove rigid body: '{}' not found.", spatial != null ? spatial.getName() : "null");
        }
    }

    /**
     * Sets default mass for bodies added without explicit mass at runtime.
     */
    public void setDefaultMass(float mass) {
        if (mass > 0f) {
            physicsModule.getConfig().rigidDefaultMass = mass;
            logger.info("RigidBodyModule default mass set to {}", mass);
        } else {
            logger.warn("Attempted to set default mass to non-positive value: {}", mass);
        }
    }

    /**
     * Gets the RigidBodyControl for a spatial, if managed.
     */
    public RigidBodyControl getRigidBodyControl(Spatial spatial) {
        return bodies.get(spatial);
    }

    /**
     * Checks if a spatial has a managed rigid body.
     */
    public boolean hasRigidBody(Spatial spatial) {
        return bodies.containsKey(spatial);
    }

    /**
     * Applies a force to a rigid body at the given relative location.
     * AAA: Useful for explosions, impacts, etc.
     */
    public void applyForce(Spatial spatial, Vector3f force, Vector3f location) {
        RigidBodyControl control = bodies.get(spatial);
        if (control != null && force != null && location != null) {
            control.applyForce(force, location);
            logger.debug("Applied force to '{}': force={}, location={}", spatial.getName(), force, location);
        } else {
            logger.warn("RigidBodyControl not found or parameters null for '{}'", spatial != null ? spatial.getName() : "null");
        }
    }

    /**
     * Sets the kinematic state of a rigid body (useful for cutscenes or scripted objects).
     */
    public void setKinematic(Spatial spatial, boolean kinematic) {
        RigidBodyControl control = bodies.get(spatial);
        if (control != null) {
            control.setKinematic(kinematic);
            logger.info("Set kinematic={} for '{}'", kinematic, spatial.getName());
        } else {
            logger.warn("RigidBodyControl not found for '{}'", spatial != null ? spatial.getName() : "null");
        }
    }
}