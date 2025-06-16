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
 * Delegates body property management to submodules for AAA extensibility.
 */
public class PhysicsModule extends EngineModule<PhysicsConfig> {
    private static final Logger logger = LogManager.getLogger(PhysicsModule.class);
    private final CalistaGameEngine app;
    private final ModuleManager subManager;
    private BulletAppState bulletAppState;
    private BulletDebugAppState debugAppState;

    // Exposed submodules for property delegation
    private RigidBodyModule rigidBodyModule;
    private SoftBodyModule softBodyModule;
    private CollisionModule collisionModule;

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
            debugAppState.setEnabled(true);
            app.getStateManager().attach(debugAppState);
            logger.info("BulletDebugAppState attached and enabled");
        }
    }

    private void registerSubModules() {
        collisionModule = new CollisionModule(this);
        rigidBodyModule = new RigidBodyModule(this);
        softBodyModule = new SoftBodyModule(this);

        subManager.register(collisionModule, 10);
        subManager.register(rigidBodyModule, 20);
        subManager.register(softBodyModule, 30);
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
        // Delegate config reloads to submodules if needed
        if (rigidBodyModule != null) rigidBodyModule.onConfigReloaded();
        if (softBodyModule != null) softBodyModule.onConfigReloaded();
        if (collisionModule != null) collisionModule.onConfigReloaded();
    }

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}

    public BulletAppState getBulletAppState() {
        return bulletAppState;
    }

    public CalistaGameEngine getApp() {
        return app;
    }

    // Delegation methods for RigidBodyModule
    public RigidBodyModule getRigidBodyModule() {
        return rigidBodyModule;
    }

    public void addRigidBody(Spatial spat, float mass) {
        if (rigidBodyModule != null) {
            rigidBodyModule.addRigidBody(spat, mass);
        } else {
            logger.warn("RigidBodyModule is not initialized.");
        }
    }

    public void removeRigidBody(Spatial spat) {
        if (rigidBodyModule != null) {
            rigidBodyModule.removeRigidBody(spat);
        } else {
            logger.warn("RigidBodyModule is not initialized.");
        }
    }

    public void setRigidBodyFriction(Spatial spat, float friction) {
        if (rigidBodyModule != null && rigidBodyModule.hasRigidBody(spat)) {
            RigidBodyControl ctrl = rigidBodyModule.getRigidBodyControl(spat);
            // Проверка: control и его native object ещё валидны!
            if (ctrl != null && ctrl.getPhysicsSpace() != null) {
                ctrl.setFriction(friction);
            } else {
                logger.warn("Attempt to set friction on invalid or removed body '{}'", spat.getName());
            }
        }
    }

    public void setRigidBodyRestitution(Spatial spat, float restitution) {
        if (rigidBodyModule != null && rigidBodyModule.hasRigidBody(spat)) {
            rigidBodyModule.getRigidBodyControl(spat).setRestitution(restitution);
            logger.debug("Set restitution={} for rigid body '{}'", restitution, spat.getName());
        }
    }

    public void setRigidBodyDamping(Spatial spat, float linear, float angular) {
        if (rigidBodyModule != null && rigidBodyModule.hasRigidBody(spat)) {
            rigidBodyModule.getRigidBodyControl(spat).setDamping(linear, angular);
            logger.debug("Set damping l={} a={} for rigid body '{}'", linear, angular, spat.getName());
        }
    }

    // Delegation methods for SoftBodyModule
    public SoftBodyModule getSoftBodyModule() {
        return softBodyModule;
    }

    public void addSoftBody(Spatial spat) {
        if (softBodyModule != null) {
            softBodyModule.addSoftBody(spat);
        } else {
            logger.warn("SoftBodyModule is not initialized.");
        }
    }

    public void removeSoftBody(Spatial spat) {
        if (softBodyModule != null) {
            softBodyModule.removeSoftBody(spat);
        } else {
            logger.warn("SoftBodyModule is not initialized.");
        }
    }

    // ... Add more delegation methods as needed for other properties (pressure, stiffness, anchors, etc.)

    public CollisionModule getCollisionModule() {
        return collisionModule;
    }
}