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

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * The main physics module, acting as a unified facade for managing the physics world in jMonkeyEngine.
 * <p>
 * This module aggregates sub-systems for handling rigid bodies, soft bodies, and collisions,
 * delegating property management to the respective sub-modules.
 * <p>
 * It ensures thread safety when interacting with the physics world and the scene graph
 * by executing all modifications on the main application thread via {@link Application#enqueue(Runnable)}.
 */
public class PhysicsModule extends EngineModule<PhysicsConfig> {
    private static final Logger logger = LogManager.getLogger(PhysicsModule.class);

    private final CalistaGameEngine app;
    private final ModuleManager subManager;

    private BulletAppState bulletAppState;
    private BulletDebugAppState debugAppState;

    private RigidBodyModule rigidBodyModule;
    private SoftBodyModule softBodyModule;
    private CollisionModule collisionModule;

    /**
     * Constructs the PhysicsModule.
     * @param app The main game engine application instance.
     */
    public PhysicsModule(CalistaGameEngine app) {
        super(PhysicsModule.class, PhysicsConfig.class, app, false);
        this.app = Objects.requireNonNull(app, "Application cannot be null");
        this.subManager = new ModuleManager(app);
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        logger.info("Initializing PhysicsModule...");

        bulletAppState = app.getStateManager().getState(BulletAppState.class);
        if (bulletAppState == null) {
            bulletAppState = new BulletAppState();
            app.getStateManager().attach(bulletAppState);
            logger.debug("New BulletAppState attached.");
        } else {
            logger.debug("Reusing existing BulletAppState.");
        }

        registerAndInitSubModules();
        applyConfig();

        debugAppState = app.getStateManager().getState(BulletDebugAppState.class);

        if (getConfig().debug) {
            if (debugAppState == null) {
                DebugConfiguration debugConfig = new DebugConfiguration();
                debugAppState = new BulletDebugAppState(debugConfig);
                app.getStateManager().attach(debugAppState);
                logger.info("BulletDebugAppState attached with new configuration.");
            }
            debugAppState.setEnabled(true);
            logger.info("Bullet physics debug view enabled.");
        } else {
            if (debugAppState != null) {
                debugAppState.setEnabled(false);
                logger.info("Bullet physics debug view disabled as per configuration.");
                if (app.getStateManager().hasState(debugAppState)) {
                    app.getStateManager().detach(debugAppState);
                    this.debugAppState = null;
                }
            }
        }
        logger.info("PhysicsModule initialized successfully.");
    }

    private void registerAndInitSubModules() {
        collisionModule = new CollisionModule(this);
        rigidBodyModule = new RigidBodyModule(this);
        softBodyModule = new SoftBodyModule(this);

        subManager.register(collisionModule, 10);
        subManager.register(rigidBodyModule, 20);
        subManager.register(softBodyModule, 30);
    }

    private void applyConfig() {
        PhysicsConfig cfg = getConfig();
        getPhysicsSpace().setGravity(cfg.gravity);
        logger.info("Physics world gravity set to {}.", cfg.gravity);
    }

    @Override
    public void onConfigReloaded() {
        logger.info("Reloading physics configuration...");
        applyConfig();
        subManager.getModules().stream()
                .filter(m -> m instanceof EngineModule)
                .forEach(m -> {
                    try {
                        ((EngineModule<?>) m).onConfigReloaded();
                    } catch (Exception e) {
                        logger.error("Failed to reload config for submodule: " + m.getClass().getSimpleName(), e);
                    }
                });
    }

    @Override
    protected void cleanupModule(Application app) {
        logger.info("Cleaning up PhysicsModule...");
        subManager.shutdown(app);

        if (debugAppState != null && app.getStateManager().hasState(debugAppState)) {
            app.getStateManager().detach(debugAppState);
            logger.debug("BulletDebugAppState detached.");
        }
        if (bulletAppState != null && app.getStateManager().hasState(bulletAppState)) {
            app.getStateManager().detach(bulletAppState);
            logger.debug("BulletAppState detached.");
        }
    }

    /**
     * Enqueues an action to be executed on the jME3 render thread to ensure thread safety.
     * @param action The action to be executed.
     */
    private void execute(Runnable action) {
        app.enqueue(action);
    }

    /**
     * Checks if the module has been initialized and throws an exception if not.
     * @throws IllegalStateException if the module is not initialized.
     */
    private void checkInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException("PhysicsModule is not initialized. Cannot perform this action.");
        }
    }

    public PhysicsSpace getPhysicsSpace() {
        return bulletAppState.getPhysicsSpace();
    }

    public BulletAppState getBulletAppState() {
        return bulletAppState;
    }

    public CalistaGameEngine getApp() {
        return app;
    }

    public RigidBodyModule getRigidBodyModule() {
        return rigidBodyModule;
    }

    /**
     * Adds a rigid body to the specified Spatial with a given mass.
     * The collision shape will be generated automatically. This operation is thread-safe.
     *
     * @param spatial The scene object to which the rigid body will be attached.
     * @param mass    The mass of the object (0 for a static body).
     */
    public void addRigidBody(Spatial spatial, float mass) {
        checkInitialized();
        execute(() -> rigidBodyModule.addRigidBody(spatial, mass));
    }

    /**
     * Removes the rigid body control from the specified Spatial. This operation is thread-safe.
     * @param spatial The scene object from which to remove the rigid body.
     */
    public void removeRigidBody(Spatial spatial) {
        checkInitialized();
        execute(() -> rigidBodyModule.removeRigidBody(spatial));
    }

    /**
     * Sets the friction for a rigid body on the specified Spatial. This operation is thread-safe.
     * @param spatial  The target spatial.
     * @param friction The new friction value.
     */
    public void setRigidBodyFriction(Spatial spatial, float friction) {
        checkInitialized();
        execute(() -> {
            RigidBodyControl control = rigidBodyModule.getRigidBodyControl(spatial);
            if (control != null && control.getPhysicsSpace() != null) {
                control.setFriction(friction);
            } else {
                logger.warn("Attempted to set friction on a non-existent or detached rigid body for spatial: {}", spatial.getName());
            }
        });
    }

    /**
     * Sets the restitution (bounciness) for a rigid body on the specified Spatial. This operation is thread-safe.
     * @param spatial     The target spatial.
     * @param restitution The new restitution value.
     */
    public void setRigidBodyRestitution(Spatial spatial, float restitution) {
        checkInitialized();
        execute(() -> {
            RigidBodyControl control = rigidBodyModule.getRigidBodyControl(spatial);
            if (control != null && control.getPhysicsSpace() != null) {
                control.setRestitution(restitution);
            }
        });
    }

    /**
     * Sets the linear and angular damping for a rigid body on the specified Spatial. This operation is thread-safe.
     * @param spatial The target spatial.
     * @param linear  The new linear damping value.
     * @param angular The new angular damping value.
     */
    public void setRigidBodyDamping(Spatial spatial, float linear, float angular) {
        checkInitialized();
        execute(() -> {
            RigidBodyControl control = rigidBodyModule.getRigidBodyControl(spatial);
            if (control != null && control.getPhysicsSpace() != null) {
                control.setDamping(linear, angular);
            }
        });
    }

    public SoftBodyModule getSoftBodyModule() {
        return softBodyModule;
    }

    /**
     * Adds a soft body to the specified Spatial. This operation is thread-safe.
     * @param spatial The scene object to convert into a soft body.
     */
    public void addSoftBody(Spatial spatial) {
        checkInitialized();
        execute(() -> softBodyModule.addSoftBody(spatial));
    }

    /**
     * Removes the soft body control from the specified Spatial. This operation is thread-safe.
     * @param spatial The scene object from which to remove the soft body.
     */
    public void removeSoftBody(Spatial spatial) {
        checkInitialized();
        execute(() -> softBodyModule.removeSoftBody(spatial));
    }

    public CollisionModule getCollisionModule() {
        return collisionModule;
    }

    @Override protected void updateModule(float tpf) {}
    @Override protected void onEnable() {}
    @Override protected void onDisable() {}
}