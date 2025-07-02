package org.foxesworld.cge.modules.physics.collision;

import com.jme3.app.Application;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.physics.PhysicsConfig;
import org.foxesworld.cge.modules.physics.PhysicsModule;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * An advanced CollisionModule that tracks the beginning, continuation, and end of collisions.
 * This module is thread-safe and uses an efficient mechanism to determine collision states
 * between frames.
 *
 * <p><b>How it works:</b></p>
 * <ol>
 *   <li>The {@link #collision(PhysicsCollisionEvent)} method, called on the physics thread,
 *       collects all active collisions for the current frame into the thread-safe
 *       {@code currentFrameEvents} map. It performs minimal work to avoid slowing down the physics engine.</li>
 *   <li>The {@link #updateModule(float)} method, called on the main game thread, compares the
 *       current frame's events (from {@code currentFrameEvents}) with the previous frame's pairs
 *       (from {@code previousFramePairs}).</li>
 *   <li>Based on this comparison, it determines:
 *       <ul>
 *         <li><b>New collisions</b> (exist in current frame but not in previous) -> invokes {@code onCollisionBegin}.</li>
 *         <li><b>Ended collisions</b> (existed in previous frame but not in current) -> invokes {@code onCollisionEnd}.</li>
 *       </ul>
 *   </li>
 *   <li>After processing, the current frame's collision set becomes the 'previous' set for the next frame,
 *       and the cycle repeats.</li>
 * </ol>
 */
public class CollisionModule extends EngineModule<PhysicsConfig> implements PhysicsCollisionListener {

    private static final Logger LOGGER = LogManager.getLogger(CollisionModule.class);

    private final PhysicsModule physicsModule;
    private BulletAppState bulletState;
    private final Set<CollisionCallback> callbacks = new CopyOnWriteArraySet<>();

    /**
     * Stores pairs that were in collision during the previous frame.
     * Accessed and modified only on the main thread (in updateModule).
     */
    private final Set<CollisionPair> previousFramePairs = new HashSet<>();

    /**
     * A thread-safe map for collecting collisions that occurred during the current physics tick.
     * The key is the pair of collided objects, and the value is the event itself.
     * Populated on the physics thread, read on the main thread.
     */
    private final ConcurrentMap<CollisionPair, PhysicsCollisionEvent> currentFrameEvents = new ConcurrentHashMap<>();

    public CollisionModule(PhysicsModule physicsModule) {
        super(CollisionModule.class, PhysicsConfig.class, physicsModule.getApp());
        this.bulletState = physicsModule.getBulletAppState();
        this.physicsModule = physicsModule;
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        if (this.bulletState != null) {
            this.bulletState.getPhysicsSpace().addCollisionListener(this);
            LOGGER.info("CollisionModule initialized and registered as a collision listener.");
        } else {
            LOGGER.error("BulletAppState not found! CollisionModule will not work.");
        }
    }

    /**
     * This method is called by the physics engine on the physics thread for each collision.
     * It performs minimal work by simply registering the collision event to be processed
     * later on the main game thread in {@link #updateModule(float)}.
     *
     * @param event The physics collision event.
     */
    @Override
    public void collision(PhysicsCollisionEvent event) {
        // It's often useful to filter out "sleeping" contacts (when objects are just resting on each other)
        // and only react to active impacts. If sleeping contacts are needed, remove this condition.
        if (event.getAppliedImpulse() == 0f) {
            return;
        }

        CollisionPair pair = CollisionPair.of(event.getNodeA(), event.getNodeB());

        // putIfAbsent ensures we only store the first event for this pair in the current frame,
        // which is ideal for onCollisionBegin.
        currentFrameEvents.putIfAbsent(pair, event);

        // Notify about continuous contact immediately.
        // This is the only callback that fires directly from the physics thread.
        callbacks.forEach(cb -> cb.onCollision(event));
    }

    /**
     * Processes the collected collision data on the main game thread to detect new and ended collisions.
     * This method is called once per frame.
     * @param tpf The time-per-frame.
     */
    @Override
    protected void updateModule(float tpf) {
        // 1. Detect NEW collisions
        // These are pairs that exist in currentFrameEvents but not in previousFramePairs.
        for (Map.Entry<CollisionPair, PhysicsCollisionEvent> entry : currentFrameEvents.entrySet()) {
            if (!previousFramePairs.contains(entry.getKey())) {
                callbacks.forEach(cb -> cb.onCollisionBegin(entry.getValue()));
            }
        }

        // 2. Detect ENDED collisions
        // These are pairs that exist in previousFramePairs but not in currentFrameEvents.
        Iterator<CollisionPair> iterator = previousFramePairs.iterator();
        while (iterator.hasNext()) {
            CollisionPair previousPair = iterator.next();
            if (!currentFrameEvents.containsKey(previousPair)) {
                callbacks.forEach(cb -> cb.onCollisionEnd(previousPair));
                iterator.remove(); // Safely remove the ended pair.
            }
        }

        // 3. Add all new collisions from the current frame to the set for the next frame.
        previousFramePairs.addAll(currentFrameEvents.keySet());

        // 4. Clear the current frame's events map to prepare for the next physics tick.
        currentFrameEvents.clear();
    }

    @Override
    protected void cleanupModule(Application app) {
        if (bulletState.getPhysicsSpace() != null) {
            bulletState.getPhysicsSpace().removeCollisionListener(this);
        }
        previousFramePairs.clear();
        currentFrameEvents.clear();
        callbacks.clear();
        LOGGER.info("CollisionModule cleaned up and listener removed.");
    }

    /**
     * Registers a callback to receive notifications about collision events.
     * @param callback An implementation of the {@link CollisionCallback} interface.
     */
    public void addCollisionCallback(CollisionCallback callback) {
        callbacks.add(callback);
    }

    /**
     * Unregisters a previously registered collision callback.
     * @param callback The callback to remove.
     */
    public void removeCollisionCallback(CollisionCallback callback) {
        callbacks.remove(callback);
    }

    // --- Helper Classes and Interfaces ---



    /**
     * An immutable, order-independent wrapper for a pair of collided objects.
     * Using a record makes it concise and inherently thread-safe.
     * The factory method {@link #of(Spatial, Spatial)} ensures consistent ordering.
     */
    public record CollisionPair(Spatial a, Spatial b) {
        /**
         * Creates an instance of CollisionPair, ensuring that the objects inside
         * are always in a consistent order regardless of their order in the collision event.
         * This is crucial for correct {@code equals()} and {@code hashCode()} behavior.
         *
         * @param nodeA the first object
         * @param nodeB the second object
         * @return a new, correctly ordered CollisionPair instance
         */
        public static CollisionPair of(Spatial nodeA, Spatial nodeB) {
            // Using System.identityHashCode is more reliable than hashCode() for object identity,
            // as a Spatial's hashCode can change if its properties (like name) are modified.
            if (System.identityHashCode(nodeA) <= System.identityHashCode(nodeB)) {
                return new CollisionPair(nodeA, nodeB);
            } else {
                return new CollisionPair(nodeB, nodeA);
            }
        }
        // equals() and hashCode() are generated automatically and work correctly
        // because the factory method enforces consistent ordering.
    }


    // --- Unchanged Lifecycle Methods ---
    @Override
    protected void onEnable() { /* No-op */ }
    @Override
    protected void onDisable() { /* No-op */ }
    @Override
    public void onConfigReloaded() {
        LOGGER.info("CollisionModule config reloaded.");
    }
}