package org.foxesworld.cge.physics.body.soft;

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
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.physics.PhysicsConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal SoftBodyModule: supports creation and management of soft bodies with configurable parameters,
 * anchoring, pressure, and custom solver settings. Comparable to RAGE soft-body package.
 */
public class SoftBodyModule extends EngineModule<PhysicsConfig> {
    private static final Logger LOGGER = LogManager.getLogger(SoftBodyModule.class);

    private final Map<Spatial, SoftBodyControl> bodies = new ConcurrentHashMap<>();
    private SoftPhysicsAppState physicsState;
    private PhysicsConfig defaultConfig;

    public SoftBodyModule(CalistaGameEngine application) {
        super("physics", PhysicsConfig.class, application);
        initialize(application);
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        defaultConfig = this.config;
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
    protected void onConfigReloaded() {
       //initialize(app);
        LOGGER.info("Reloaded SoftBodyModule config: mass={}, stiffness={}, damping={}, pressure={}, iterations={}",
                defaultConfig.softDefaultMass,
                defaultConfig.softStiffness,
                defaultConfig.softDamping,
                defaultConfig.softPressure,
                defaultConfig.softSolverIterations);
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

    public SoftBodyControl addSoftBody(Spatial spatial, PhysicsConfig cfg) {
        /*
        Geometry geometry = findGeometry(spatial);
        if (geometry == null) {
            LOGGER.warn("Geometry not found in spatial '{}', skipping SoftBody addition.", spatial.getName());
            return null;
        }

        // Создаем SoftBodyControl с параметрами по умолчанию
        SoftBodyControl control = new SoftBodyControl(false, false, true);
        spatial.addControl(control);
        physicsState.getPhysicsSpace().add(control);

        // Получаем PhysicsSoftBody для настройки параметров
        PhysicsSoftBody body = control.getSoftBody();

        // Устанавливаем массу
        body.setTotalMass(cfg.softDefaultMass, false);

        // Настраиваем параметры конфигурации
        PhysicsSoftBody.Config config = body.getConfig();
        config.kVCF = cfg.softStiffness; // Volume conservation factor (жесткость)
        config.kDP = cfg.softDamping;    // Damping coefficient (демпфирование)
        config.kPR = cfg.softPressure;   // Pressure coefficient (давление)

        // Устанавливаем количество итераций решателя
        body.getWorldInfo().setIterations(cfg.softSolverIterations);

        bodies.put(spatial, control);
        LOGGER.debug("Added soft body '{}' with mass={}, stiffness={}, damping={}, pressure={}, iterations={}",
                spatial.getName(),
                cfg.softDefaultMass,
                cfg.softStiffness,
                cfg.softDamping,
                cfg.softPressure,
                cfg.softSolverIterations);
        return control;
        */

        return null;
    }


    /**
     * Anchor a node of the soft body to a static or rigid spatial.
     */
    public void anchorNode(Spatial softSpatial, int nodeIndex, Spatial target, Vector3f pivot) {
        SoftBodyControl ctrl = bodies.get(softSpatial);
        if (ctrl != null) {
            //ctrl.getSoftBody().appendAnchor(nodeIndex, target, pivot, true);
            LOGGER.debug("Anchored node {} of '{}' to '{}' at {}",
                    nodeIndex, softSpatial.getName(), target.getName(), pivot);
        }
    }

    /**
     * Apply force to entire body or specific node.
     */
    public void applyForce(Spatial spatial, Vector3f force, Integer nodeIndex) {
        SoftBodyControl ctrl = bodies.get(spatial);
        if (ctrl == null) return;
        //PhysicsSoftBody body = ctrl.getSoftBody();
        if (nodeIndex == null) {
            //body.applyCentralForce(force);
        } else {
            //int count = body.countNodes();
            //if (nodeIndex < 0 || nodeIndex >= count) return;
            //body.applyForce(force, nodeIndex);
        }
    }

    /**
     * Remove and cleanup a soft body.
     */
    public void removeSoftBody(Spatial spatial) {
        SoftBodyControl ctrl = bodies.remove(spatial);
        if (ctrl != null) {
            physicsState.getPhysicsSpace().remove(ctrl);
            spatial.removeControl(ctrl);
            LOGGER.debug("Removed soft body '{}'", spatial.getName());
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
}
