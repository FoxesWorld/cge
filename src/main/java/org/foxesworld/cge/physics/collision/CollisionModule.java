package org.foxesworld.cge.physics.collision;

import com.jme3.app.Application;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleState;
import org.foxesworld.cge.physics.PhysicsConfig;
import org.foxesworld.cge.physics.PhysicsModule;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Universal CollisionModule: listens to collision events, tracks enter/exit,
 * supports collision groups and user callbacks.
 */
public class CollisionModule extends EngineModule<PhysicsConfig> implements PhysicsCollisionListener {
    private static final Logger LOGGER = LogManager.getLogger(CollisionModule.class);

    private final  PhysicsModule physicsModule;
    private BulletAppState bulletState;
    private final Set<CollisionCallback> callbacks = new CopyOnWriteArraySet<>();
    private final Set<CollisionPair> activePairs = Collections.synchronizedSet(new HashSet<>());

    public CollisionModule(PhysicsModule physicsModule) {
        super("physics", PhysicsConfig.class, physicsModule.getCalistaGameEngine());
        this.physicsModule = physicsModule;
        bulletState = physicsModule.getBulletAppState();
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
       // bulletState = app.getStateManager().getState(BulletAppState.class);
        bulletState.getPhysicsSpace().addCollisionListener(this);
        LOGGER.info("CollisionModule initialized and registered listener");
    }

    @Override
    protected void onEnable() {
        // No-op
    }

    @Override
    protected void onDisable() {

    }

    @Override
    protected void updateModule(float tpf) {
        // Nothing to update each frame; events drive logic
    }

    @Override
    protected void onConfigReloaded() {
        // Config can adjust filtering in future
        LOGGER.info("CollisionModule config reloaded");
    }

    @Override
    protected void cleanupModule(Application app) {
        if(this.physicsModule.getState() == ModuleState.RUNNING) {
            bulletState.getPhysicsSpace().removeCollisionListener(this);
            activePairs.clear();
            callbacks.clear();
            LOGGER.info("CollisionModule cleaned up");
        }
    }

    @Override
    public void collision(PhysicsCollisionEvent event) {
        CollisionPair pair = new CollisionPair(
                event.getNodeA(), event.getNodeB());

        // New collision
        if (!activePairs.contains(pair)) {
            activePairs.add(pair);
            callbacks.forEach(cb -> cb.onCollisionBegin(event));
        }

        // Always notify contact
        callbacks.forEach(cb -> cb.onCollision(event));

        // Collision end detection deferred (requires tracking separate step)
    }

    /**
     * Registers a callback for collision events.
     */
    public void addCollisionCallback(CollisionCallback callback) {
        callbacks.add(callback);
    }

    /**
     * Unregisters a collision callback.
     */
    public void removeCollisionCallback(CollisionCallback callback) {
        callbacks.remove(callback);
    }

    /**
     * Interface for collision event callbacks.
     */
    public interface CollisionCallback {
        void onCollisionBegin(PhysicsCollisionEvent event);
        void onCollision(PhysicsCollisionEvent event);
        void onCollisionEnd(PhysicsCollisionEvent event);
    }

    /**
     * Simple immutable pair of collided spatials for tracking.
     */
    private static class CollisionPair {
        private final Object a, b;
        CollisionPair(Object a, Object b) {
            // order-insensitive
            if (a.hashCode() <= b.hashCode()) { this.a = a; this.b = b; }
            else { this.a = b; this.b = a; }
        }
        @Override public int hashCode() { return a.hashCode()*31 + b.hashCode(); }
        @Override public boolean equals(Object o) {
            if (!(o instanceof CollisionPair)) return false;
            CollisionPair p = (CollisionPair) o;
            return p.a == a && p.b == b;
        }
    }
}
