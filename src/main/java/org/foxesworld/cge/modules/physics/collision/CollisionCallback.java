package org.foxesworld.cge.modules.physics.collision;

import com.jme3.bullet.collision.PhysicsCollisionEvent;
import org.foxesworld.cge.modules.physics.collision.CollisionModule;

public interface CollisionCallback {
    /**
     * Invoked once, at the moment of the first contact between two objects in a series of collisions.
     * @param event The collision event, containing information about the objects and impact force.
     */
    void onCollisionBegin(PhysicsCollisionEvent event);

    /**
     * Invoked on every physics tick as long as two objects remain in contact.
     * @param event The collision event.
     */
    void onCollision(PhysicsCollisionEvent event);

    /**
     * Invoked once when two objects cease to be in contact.
     * @param endedPair The pair of objects whose collision has ended.
     */
    void onCollisionEnd(CollisionModule.CollisionPair endedPair);
}