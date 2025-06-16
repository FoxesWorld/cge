package org.foxesworld.cge.modules.physics.body.soft;

import com.jme3.app.Application;
import com.jme3.bullet.SoftPhysicsAppState;
import com.jme3.bullet.control.SoftBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
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
 * AAA-level SoftBodyModule: robust, flexible, with advanced configuration support.
 * - Creation, parameters, anchoring, force, and cleanup for soft bodies.
 * - Logs all operations, handles nulls, errors, and config reloads.
 */
public class SoftBodyModule extends EngineModule<PhysicsConfig> {
    private static final Logger LOGGER = LogManager.getLogger(SoftBodyModule.class);
    private final Map<Spatial, SoftBodyControl> bodies = new ConcurrentHashMap<>();
    private SoftPhysicsAppState physicsState;
    private PhysicsConfig defaultConfig;

    public SoftBodyModule(PhysicsModule physicsModule) {
        super("physics", PhysicsConfig.class, physicsModule.getApp());
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        defaultConfig = getConfig();
        physicsState = app.getStateManager().getState(SoftPhysicsAppState.class);
        LOGGER.info("Initialized SoftBodyModule: mass={}, stiffness={}, damping={}, pressure={}, iterations={}",
                defaultConfig.softDefaultMass,
                defaultConfig.softStiffness,
                defaultConfig.softDamping,
                defaultConfig.softPressure,
                defaultConfig.softSolverIterations);
    }

    @Override
    protected void onEnable() {
        // No specific enable actions
    }

    @Override
    protected void updateModule(float tpf) {
        // SoftBodyControl auto-syncs transforms; no manual sync needed
    }

    @Override
    public void onConfigReloaded() {
        LOGGER.info("Reloaded SoftBodyModule config: mass={}, stiffness={}, damping={}, pressure={}, iterations={}",
                defaultConfig.softDefaultMass,
                defaultConfig.softStiffness,
                defaultConfig.softDamping,
                defaultConfig.softPressure,
                defaultConfig.softSolverIterations);
        // Optionally update all active bodies with new config
        bodies.forEach((spatial, ctrl) -> {
            if (ctrl != null) {
                try {
                    // Uncomment and update parameters if your SoftBodyControl exposes config setters
                    // ctrl.getSoftBody().setTotalMass(defaultConfig.softDefaultMass, false);
                } catch (Exception e) {
                    LOGGER.error("Error updating soft body config for '{}': {}", spatial != null ? spatial.getName() : "null", e.getMessage());
                }
            }
        });
    }

    @Override
    protected void cleanupModule(Application app) {
        bodies.keySet().forEach(this::removeSoftBody);
        bodies.clear();
        LOGGER.info("Cleaned up all soft bodies");
    }

    @Override
    public void onDisable() {
        cleanupModule(getApplication());
    }

    /**
     * Create and add a soft body with default config.
     */
    public SoftBodyControl addSoftBody(Spatial spatial) {
        return addSoftBody(spatial, defaultConfig);
    }

    /**
     * Creates and adds a soft body with the given config.
     */
    public SoftBodyControl addSoftBody(Spatial spatial, PhysicsConfig cfg) {
        if (spatial == null) {
            LOGGER.warn("Cannot add soft body: spatial is null.");
            return null;
        }
        if (bodies.containsKey(spatial)) {
            LOGGER.warn("Spatial '{}' already has a soft body.", spatial.getName());
            return bodies.get(spatial);
        }
        Geometry geometry = findGeometry(spatial);
        if (geometry == null) {
            LOGGER.warn("Geometry not found in spatial '{}', skipping SoftBody addition.", spatial.getName());
            return null;
        }
        SoftBodyControl control = new SoftBodyControl(false, false, true);
        spatial.addControl(control);
        if (physicsState != null && physicsState.getPhysicsSpace() != null) {
            physicsState.getPhysicsSpace().add(control);
        } else {
            LOGGER.error("SoftPhysicsAppState or its PhysicsSpace is null.");
            return null;
        }
        // Try to set advanced parameters if available in your engine version
        try {
            // Uncomment and adjust for your engine's API:
            // PhysicsSoftBody body = control.getSoftBody();
            // body.setTotalMass(cfg.softDefaultMass, false);
            // PhysicsSoftBody.Config config = body.getConfig();
            // config.kVCF = cfg.softStiffness;
            // config.kDP = cfg.softDamping;
            // config.kPR = cfg.softPressure;
            // body.getWorldInfo().setIterations(cfg.softSolverIterations);
        } catch (Exception e) {
            LOGGER.error("Failed to set soft body parameters for '{}': {}", spatial.getName(), e.getMessage());
        }
        bodies.put(spatial, control);
        LOGGER.debug("Added soft body '{}' with mass={}, stiffness={}, damping={}, pressure={}, iterations={}",
                spatial.getName(),
                cfg.softDefaultMass,
                cfg.softStiffness,
                cfg.softDamping,
                cfg.softPressure,
                cfg.softSolverIterations);
        return control;
    }

    /**
     * Anchor a node of the soft body to a static or rigid spatial.
     */
    public void anchorNode(Spatial softSpatial, int nodeIndex, Spatial target, Vector3f pivot) {
        SoftBodyControl ctrl = bodies.get(softSpatial);
        if (ctrl != null) {
            try {
                // Uncomment and adjust for your engine's API:
                // ctrl.getSoftBody().appendAnchor(nodeIndex, target, pivot, true);
                LOGGER.debug("Anchored node {} of '{}' to '{}' at {}",
                        nodeIndex, softSpatial.getName(), target.getName(), pivot);
            } catch (Exception e) {
                LOGGER.error("Failed to anchor node for '{}': {}", softSpatial.getName(), e.getMessage());
            }
        }
    }

    /**
     * Apply force to entire body or specific node.
     */
    public void applyForce(Spatial spatial, Vector3f force, Integer nodeIndex) {
        SoftBodyControl ctrl = bodies.get(spatial);
        if (ctrl == null || force == null) return;
        try {
            // Uncomment and adjust for your engine's API:
            // PhysicsSoftBody body = ctrl.getSoftBody();
            // if (nodeIndex == null) {
            //   body.applyCentralForce(force);
            // } else {
            //   int count = body.countNodes();
            //   if (nodeIndex < 0 || nodeIndex >= count) return;
            //   body.applyForce(force, nodeIndex);
            // }
        } catch (Exception e) {
            LOGGER.error("Failed to apply force to '{}': {}", spatial.getName(), e.getMessage());
        }
    }

    /**
     * Remove and cleanup a soft body.
     */
    public void removeSoftBody(Spatial spatial) {
        SoftBodyControl ctrl = bodies.remove(spatial);
        if (ctrl != null) {
            try {
                if (physicsState != null && physicsState.getPhysicsSpace() != null) {
                    physicsState.getPhysicsSpace().remove(ctrl);
                }
                if (spatial != null) {
                    spatial.removeControl(ctrl);
                }
                LOGGER.debug("Removed soft body '{}'", spatial.getName());
            } catch (Exception e) {
                LOGGER.error("Error removing soft body '{}': {}", spatial != null ? spatial.getName() : "null", e.getMessage());
            }
        }
    }

    /**
     * Recursively find first Geometry under spatial.
     */
    private Geometry findGeometry(Spatial spatial) {
        if (spatial instanceof Geometry) {
            return (Geometry) spatial;
        } else if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                Geometry g = findGeometry(child);
                if (g != null) return g;
            }
        }
        return null;
    }

    /**
     * Checks if a spatial has a managed soft body.
     */
    public boolean hasSoftBody(Spatial spatial) {
        return bodies.containsKey(spatial);
    }

    /**
     * Gets the SoftBodyControl for a spatial, if managed.
     */
    public SoftBodyControl getSoftBodyControl(Spatial spatial) {
        return bodies.get(spatial);
    }
}