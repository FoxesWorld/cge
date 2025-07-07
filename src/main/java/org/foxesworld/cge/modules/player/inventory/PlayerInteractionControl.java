package org.foxesworld.cge.modules.player.inventory;

import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import org.foxesworld.cge.modules.player.Player;

import java.util.List;

/**
 * Handles player interaction with the world, such as picking up items.
 */
public class PlayerInteractionControl extends AbstractControl {

    private final Player player;
    private final Camera cam;
    private final float interactionDistance;
    private PickableItemControl targetedItem = null;

    public PlayerInteractionControl(Player player, float interactionDistance) {
        this.player = player;
        this.cam = player.getCam();
        this.interactionDistance = interactionDistance;
    }

    @Override
    protected void controlUpdate(float tpf) {
        // Raycast from the center of the screen to find interactable objects
        Vector3f origin = cam.getLocation();
        Vector3f direction = cam.getDirection();

        List<PhysicsRayTestResult> results = player.getBullet().getPhysicsSpace().rayTest(origin, origin.add(direction.mult(interactionDistance)));

        targetedItem = null; // Reset target
        for (PhysicsRayTestResult result : results) {
            Object userObject = result.getCollisionObject().getUserObject();

            // --- НАДЕЖНАЯ ПРОВЕРКА ---
            // Мы сравниваем, является ли "владелец" коллайдера самим игроком.
            if (userObject == player) {continue;} // Ignore self

            if (userObject instanceof Spatial) {
                Spatial hitSpatial = (Spatial) userObject;
                PickableItemControl pic = hitSpatial.getControl(PickableItemControl.class);
                if (pic != null) {
                    targetedItem = pic;
                    // Optional: highlight the targeted item here
                    break; // Found the closest pickable item
                }
            }
        }
    }

    /**
     * Called when the player presses the interact key.
     */
    public void interact() {
        if (targetedItem != null) {
            player.pickupItem(targetedItem);
        }
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {}
}