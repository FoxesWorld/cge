package org.foxesworld.cge.modules.player;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.math.Vector3f;

import java.util.List;

 public class PhysicsHelper {
     private final Player player;
    private final CharacterControl character;
    private final BulletAppState bullet;
    private final PlayerModule module;

    PhysicsHelper(Player player) {
        this.player = player;
        this.character = player.getCharacter();
        this.bullet = player.getBullet();
        this.module = player.getPlayerModule();
    }

    Vector3f getPhysicsLocation() {
        return character.getPhysicsLocation();
    }

    boolean checkGroundWithRaycast(Player owner) {
        Vector3f origin = character.getPhysicsLocation().add(0, 0.1f, 0);
        Vector3f direction = Vector3f.UNIT_Y.negate();
        float rayLength = 1.5f;

        PhysicsSpace physicsSpace = bullet.getPhysicsSpace();
        if (physicsSpace == null) return false;

        Vector3f end = origin.add(direction.mult(rayLength));
        List<PhysicsRayTestResult> results = physicsSpace.rayTest(origin, end);

        float minFraction = Float.MAX_VALUE;
        PhysicsRayTestResult closest = null;

        for (PhysicsRayTestResult result : results) {
            Object userObject = result.getCollisionObject().getUserObject();
            if (userObject == character || userObject == owner) continue;
            if (result.getHitFraction() < minFraction) {
                minFraction = result.getHitFraction();
                closest = result;
            }
        }
        if (closest != null) {
            float hitDistance = rayLength * minFraction;
            return hitDistance < 0.25f;
        }
        return false;
    }

     public void updateModelPosition() {
         if (player.playerModel == null) return;
         Vector3f modelPos = player.reuseVec1.set(character.getPhysicsLocation()).addLocal(0, -player.getPlayerModule().getConfig().getPhysics().getHeight() / 2f, 0);
         player.playerModel.setLocalTranslation(modelPos);

         Vector3f lookTarget = modelPos.add(player.getCam().getDirection(player.reuseVec2).normalizeLocal());
         player.playerModel.lookAt(lookTarget, Vector3f.UNIT_Y);
     }

     public boolean isGrounded() {
         return character.onGround() || checkGroundWithRaycast(player);
     }

     public void synchronize(boolean instant) {
         player.setLocalTranslation(character.getPhysicsLocation());
         if (instant) player.interpEyeHeight = player.targetEyeHeight;
         else player.interpEyeHeight += (player.targetEyeHeight - player.interpEyeHeight) * 0.12f;
     }
}