package org.foxesworld.cge.physics;

import com.jme3.app.Application;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.debug.BulletDebugAppState;
import com.jme3.bullet.debug.DebugConfiguration;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.physics.body.rigid.RigidBodyModule;
import org.foxesworld.cge.physics.body.soft.SoftBodyModule;
import org.foxesworld.cge.physics.collision.CollisionModule;

/**
 * Main physics module aggregating collision, rigid, and soft body sub-modules.
 */
public class PhysicsModule extends EngineModule<PhysicsConfig> {
    private static final Logger logger = LogManager.getLogger(PhysicsModule.class);
    protected final CalistaGameEngine calistaGameEngine;
    private final ModuleManager subManager;
    protected BulletAppState bulletAppState;
    private BulletDebugAppState bulletDebugAppState;  // Для отображения отладки

    public PhysicsModule(CalistaGameEngine calistaGameEngine) {
        super("physics", PhysicsConfig.class, calistaGameEngine);
        this.calistaGameEngine = calistaGameEngine;
        this.subManager = new ModuleManager(calistaGameEngine);
        initialize(calistaGameEngine);
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        logger.info("{}: initializing physics...");

        // Initialize BulletAppState and attach to the AppStateManager if not already present
        if (app.getStateManager().getState(BulletAppState.class) == null) {
            bulletAppState = new BulletAppState();
            app.getStateManager().attach(bulletAppState);
            logger.debug("BulletAppState attached");
        } else {
            bulletAppState = app.getStateManager().getState(BulletAppState.class);
            logger.debug("BulletAppState already present");
        }

        // Register and initialize sub-modules
        registerSubModules(app);
        subManager.initializeAll(app);
        logger.info("{}: physics sub-modules initialized", getName());

        // Optionally adjust physics settings from config
        applyConfigSettings();

        // Enable debug if enabled in config
        if (getConfig().debug) {
            DebugConfiguration debugConfig = new DebugConfiguration();
            debugConfig.setEnabled(true);
            bulletDebugAppState = new BulletDebugAppState(debugConfig);
            app.getStateManager().attach(bulletDebugAppState);
            logger.info("BulletDebugAppState attached: Collision shapes and debug visuals will be shown.");
        }
    }

    private void registerSubModules(CalistaGameEngine app) {
        subManager.register(new RigidBodyModule(this), 20);
        subManager.register(new CollisionModule(this), 10);
        subManager.register(new SoftBodyModule(this), 30);
        // Add any additional sub-modules here as needed
    }

    private void applyConfigSettings() {
        PhysicsConfig cfg = getConfig();
        PhysicsSpace space = bulletAppState.getPhysicsSpace();
        space.setGravity(cfg.gravity);
        logger.info("Physics gravity set to {}", cfg.gravity);
    }

    @Override
    protected void updateModule(float tpf) throws Exception {
        // BulletAppState automatically handles the physics steps in the background
    }

    @Override
    protected void cleanupModule(Application app) throws Exception {
        logger.info("{}: cleaning up physics...");

        // Shutdown all sub-modules
        subManager.shutdown(app);

        // Detach BulletAppState and BulletDebugAppState if they were attached
        if (bulletAppState != null) {
            app.getStateManager().detach(bulletAppState);
            logger.debug("BulletAppState detached");
        }

        if (bulletDebugAppState != null) {
            app.getStateManager().detach(bulletDebugAppState);
            logger.debug("BulletDebugAppState detached");
        }
    }

    @Override
    protected void onConfigReloaded() throws Exception {
        // Update gravity on config reload
        PhysicsConfig cfg = getConfig();
        if (bulletAppState != null) {
            bulletAppState.getPhysicsSpace().setGravity(cfg.gravity);
            logger.info("{}: gravity reloaded to {}", getName(), cfg.gravity);
        }
    }

    @Override
    protected void onEnable() {
        // Optionally handle enable logic here
    }

    @Override
    protected void onDisable() {
        // Optionally handle disable logic here
    }

    public BulletAppState getBulletAppState() {
        return bulletAppState;
    }

    public CalistaGameEngine getCalistaGameEngine() {
        return calistaGameEngine;
    }

    public ModuleManager getSubManager() {
        return subManager;
    }

    /**
     * Оборачивает Spatial в RigidBodyControl и добавляет в физический мир.
     * @param spat  любой узел или модель
     * @param mass  масса тела (0 — статический, >0 — динамический)
     */
    public void addRigidBody(Spatial spat, float mass) {
        if (bulletAppState == null) {
            logger.warn("BulletAppState not initialized – cannot add physics body for {}", spat.getName());
            return;
        }
        RigidBodyControl control = new RigidBodyControl(mass);
        spat.addControl(control);
        bulletAppState.getPhysicsSpace().add(control);
        logger.debug("Added RigidBodyControl (mass={}) to {}", mass, spat.getName());
    }
}
