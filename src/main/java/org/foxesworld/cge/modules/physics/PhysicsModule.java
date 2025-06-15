package org.foxesworld.cge.modules.physics;

import com.jme3.app.Application;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.debug.BulletDebugAppState;
import com.jme3.bullet.debug.DebugConfiguration;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.modules.physics.body.rigid.RigidBodyModule;
import org.foxesworld.cge.modules.physics.body.soft.SoftBodyModule;
import org.foxesworld.cge.modules.physics.collision.CollisionModule;

/**
 * Aggregates physics subsystems: collision, rigid and soft bodies, and debug.
 */
public class PhysicsModule extends EngineModule<PhysicsConfig> {
    private static final Logger logger = LogManager.getLogger(PhysicsModule.class);
    private final CalistaGameEngine app;
    private final ModuleManager subManager;
    private BulletAppState bulletAppState;
    private BulletDebugAppState debugAppState;

    public PhysicsModule(CalistaGameEngine app) {
        super("physics", PhysicsConfig.class, app);
        this.app = app;
        this.subManager = new ModuleManager(app);
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        logger.info("Initializing PhysicsModule...");
        // Attach or reuse BulletAppState
        bulletAppState = app.getStateManager().getState(BulletAppState.class);
        if (bulletAppState == null) {
            bulletAppState = new BulletAppState();
            app.getStateManager().attach(bulletAppState);
            logger.debug("BulletAppState attached");
        } else {
            logger.debug("BulletAppState already present");
        }

        // Register and init sub-modules
        registerSubModules();
        subManager.initializeAll(app);
        applyConfig();

        // Setup debug if enabled
        if (getConfig().debug) {
            DebugConfiguration cfg = new DebugConfiguration();
            cfg.setEnabled(true);
            debugAppState = new BulletDebugAppState(cfg);
            // Ensure debug node renders in scene
            debugAppState.setEnabled(true);//setDebugRootNode(app.getRootNode());
            app.getStateManager().attach(debugAppState);
            logger.info("BulletDebugAppState attached and enabled");
        }
    }

    private void registerSubModules() {
        subManager.register(new CollisionModule(this), 10);
        subManager.register(new RigidBodyModule(this), 20);
        subManager.register(new SoftBodyModule(this), 30);
    }

    private void applyConfig() {
        PhysicsConfig cfg = getConfig();
        PhysicsSpace space = bulletAppState.getPhysicsSpace();
        space.setGravity(cfg.gravity);
        logger.info("Gravity set to {}", cfg.gravity);
    }

    @Override
    protected void updateModule(float tpf) throws Exception {
        // Physics stepping is handled by BulletAppState internally
    }

    @Override
    protected void cleanupModule(Application app) throws Exception {
        logger.info("Cleaning up PhysicsModule...");
        subManager.shutdown(app);
        if (debugAppState != null) {
            app.getStateManager().detach(debugAppState);
            logger.debug("BulletDebugAppState detached");
        }
        if (bulletAppState != null) {
            app.getStateManager().detach(bulletAppState);
            logger.debug("BulletAppState detached");
        }
    }

    @Override
    protected void onConfigReloaded() throws Exception {
        PhysicsConfig cfg = getConfig();
        bulletAppState.getPhysicsSpace().setGravity(cfg.gravity);
        logger.info("Gravity reloaded: {}", cfg.gravity);
    }

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}

    public BulletAppState getBulletAppState() {
        return bulletAppState;
    }

    /**
     * Adds a rigid body control to the spatial and registers it.
     */
    public void addRigidBody(Spatial spat, float mass) {
        if (bulletAppState == null) {
            logger.warn("Cannot add rigid body, BulletAppState is null");
            return;
        }
        RigidBodyControl ctrl = new RigidBodyControl(mass);
        spat.addControl(ctrl);
        bulletAppState.getPhysicsSpace().add(ctrl);
        logger.debug("RigidBodyControl (mass={}) added to {}", mass, spat.getName());
    }

    public CalistaGameEngine getApp() {
        return app;
    }
}