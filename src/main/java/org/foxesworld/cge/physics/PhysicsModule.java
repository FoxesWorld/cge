package org.foxesworld.cge.physics;

import com.jme3.app.Application;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.physics.body.rigid.RigidBodyModule;
import org.foxesworld.cge.physics.body.soft.SoftBodyModule;
import org.foxesworld.cge.physics.collision.CollisionModule;

/**
 * Main physics module aggregating collision, rigid and soft body sub-modules.
 */
public class PhysicsModule extends EngineModule<PhysicsConfig> {
    private static final Logger logger = LogManager.getLogger(PhysicsModule.class);

    private CalistaGameEngine calistaGameEngine;
    private final ModuleManager subManager;
    private BulletAppState bulletAppState;

    public PhysicsModule(CalistaGameEngine calistaGameEngine) {
        super("physics", PhysicsConfig.class, calistaGameEngine);
        // subManager will be initialized in initModule when AppStateManager is available
        this.calistaGameEngine = calistaGameEngine;
        this.subManager = new ModuleManager(calistaGameEngine);
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        logger.info("{}: initializing physics...");

        // Attach BulletAppState
        bulletAppState = new BulletAppState();
        if (app.getStateManager().getState(BulletAppState.class) == null) {
            app.getStateManager().attach(bulletAppState);
            logger.debug("BulletAppState attached");
        } else {
            bulletAppState = app.getStateManager().getState(BulletAppState.class);
            logger.debug("BulletAppState already present");
        }

        // Register sub-modules with real AppStateManager
        //subManager.setAppStateManager(app.getStateManager());
        subManager.register(new CollisionModule(this), 10);
        subManager.register(new RigidBodyModule(app), 20);
        subManager.register(new SoftBodyModule(app), 30);
        // e.g. subManager.register(new JointModule(...), 40);

        // Initialize all sub-modules
        subManager.initializeAll(app);
        logger.info("{}: physics sub-modules initialized", getName());

        // Optionally adjust physics settings from config
        PhysicsConfig cfg = getConfig();
        PhysicsSpace space = bulletAppState.getPhysicsSpace();
        space.setGravity(cfg.gravity);
        logger.info("Physics gravity set to {}", cfg.gravity);
    }

    @Override
    protected void updateModule(float tpf) throws Exception {
        // Physics steps are handled by BulletAppState automatically
    }

    @Override
    protected void cleanupModule(Application app) throws Exception {
        logger.info("{}: cleaning up physics...");

        // Detach sub-modules
        subManager.shutdown(app);

        // Detach BulletAppState
        if (bulletAppState != null) {
            app.getStateManager().detach(bulletAppState);
            logger.debug("BulletAppState detached");
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

    }

    @Override
    protected void onDisable() {

    }

    public BulletAppState getBulletAppState() {
        return bulletAppState;
    }

    public CalistaGameEngine getCalistaGameEngine() {
        return calistaGameEngine;
    }
}
