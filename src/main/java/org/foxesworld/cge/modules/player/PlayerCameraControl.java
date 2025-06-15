package org.foxesworld.cge.modules.player;

import com.jme3.collision.CollisionResults;
import com.jme3.collision.Collidable;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.UnsupportedCollisionException;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.math.FastMath;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.math.Quaternion;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.Spatial;

import java.util.ArrayList;
import java.util.List;

/**
 * Camera control with collision: prevents the camera from clipping through scene geometry.
 * Now with Easing (smooth camera switching) and spherical raycast for solid AAA-feel.
 */
public class PlayerCameraControl extends AbstractControl implements AnalogListener, ActionListener {

    private static final String TOGGLE_VIEW = "Toggle_View";
    private static final String MOUSE_LEFT  = "Mouse_Left";
    private static final String MOUSE_RIGHT = "Mouse_Right";
    private static final String MOUSE_UP    = "Mouse_Up";
    private static final String MOUSE_DOWN  = "Mouse_Down";

    private final Camera cam;
    private final InputManager input;
    private final float eyeHeight;
    private final float sensitivity;
    private final float smoothingFactor;
    private final Spatial sceneRoot;

    private float yaw = 0, pitch = 0;
    private float targetYaw = 0, targetPitch = 0;

    private boolean thirdPerson = false;

    // Camera distance settings
    private final float distance = 5.0f;         // third-person target distance
    private final float minDistance = 1.2f;      // minimum allowed distance
    private final float wallOffset = 0.36f;      // offset from collision surface
    private final float easingSpeed = 5.5f;      // how fast camera switches (higher = faster)
    private float currentDistance = 0.01f;       // current camera distance, eases on switch
    private float desiredDistance = 0.01f;

    // Spherical raycast settings
    private final int SPHERE_RAYS = 7; // 1 center, 6 around (hexagon)
    private final float SPHERE_RADIUS = 0.28f; // "near clip" radius for cast

    private final Vector3f tempVec = new Vector3f();

    public PlayerCameraControl(Player player, float eyeHeight, float sensitivity, float smoothing, Spatial sceneRoot) {
        this.cam = player.getCam();
        this.input = player.getInput();
        this.eyeHeight = eyeHeight;
        this.sensitivity = sensitivity;
        this.smoothingFactor = FastMath.clamp(smoothing, 0f, 1f);
        this.sceneRoot = sceneRoot;

        setupMappings();
        input.setCursorVisible(false);
    }

    private void setupMappings() {
        input.addMapping(TOGGLE_VIEW, new KeyTrigger(KeyInput.KEY_C));
        input.addMapping(MOUSE_LEFT,  new MouseAxisTrigger(MouseInput.AXIS_X, true));
        input.addMapping(MOUSE_RIGHT, new MouseAxisTrigger(MouseInput.AXIS_X, false));
        input.addMapping(MOUSE_UP,    new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        input.addMapping(MOUSE_DOWN,  new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        input.addListener(this, TOGGLE_VIEW, MOUSE_LEFT, MOUSE_RIGHT, MOUSE_UP, MOUSE_DOWN);
    }

    @Override
    protected void controlUpdate(float tpf) {
        if (spatial == null) return;

        float alpha = 1f - FastMath.pow(1f - smoothingFactor, tpf * 60f);
        yaw   = FastMath.interpolateLinear(alpha, yaw, targetYaw);
        pitch = FastMath.interpolateLinear(alpha, pitch, targetPitch);

        float maxPitch = FastMath.DEG_TO_RAD * 80f;
        pitch = FastMath.clamp(pitch, -maxPitch, maxPitch);

        Quaternion rot = new Quaternion().fromAngles(pitch, yaw, 0);
        Vector3f playerPos = spatial.getWorldTranslation().add(0, eyeHeight, 0);

        // Camera mode switch with easing
        desiredDistance = thirdPerson ? distance : 0.01f;
        float spring = FastMath.clamp(1f - FastMath.exp(-easingSpeed * tpf), 0f, 1f);
        currentDistance += (desiredDistance - currentDistance) * spring;

        if (currentDistance > minDistance * 1.01f) {
            Vector3f backDir = rot.mult(Vector3f.UNIT_Z).negateLocal();
            Vector3f desiredPos = playerPos.add(backDir.mult(currentDistance));

            // --- Spherical raycast for robust collision ---
            float closestDist = currentDistance;
            List<Vector3f> offsets = getSphereOffsets(backDir, rot);
            for (Vector3f offset : offsets) {
                Vector3f rayStart = playerPos.add(offset);
                Vector3f rayEnd   = desiredPos.add(offset);
                Vector3f dir = rayEnd.subtract(rayStart).normalizeLocal();
                float maxDist = rayEnd.distance(rayStart);

                Ray ray = new Ray(rayStart, dir);
                ray.setLimit(maxDist + wallOffset);

                CollisionResults results = new CollisionResults();
                try {
                    ((Collidable) sceneRoot).collideWith(ray, results);
                } catch (UnsupportedCollisionException ignored) {}

                if (results.size() > 0) {
                    CollisionResult closest = results.getClosestCollision();
                    float dist = closest.getDistance() - wallOffset;
                    dist = FastMath.clamp(dist, minDistance, closestDist);
                    if (dist < closestDist) closestDist = dist;
                }
            }
            Vector3f camPos = playerPos.add(backDir.mult(closestDist));
            cam.setLocation(camPos);
            cam.lookAt(playerPos, Vector3f.UNIT_Y);

        } else {
            // First person
            cam.setRotation(rot);
            cam.setLocation(playerPos);
        }
    }

    /**
     * Computes offsets for spherical raycast: center and points around a circle perpendicular to backDir.
     */
    private List<Vector3f> getSphereOffsets(Vector3f backDir, Quaternion rot) {
        List<Vector3f> offsets = new ArrayList<>(SPHERE_RAYS);
        offsets.add(Vector3f.ZERO.clone()); // center

        // Build a circle perpendicular to backDir
        Vector3f up   = Vector3f.UNIT_Y;
        Vector3f side = backDir.cross(up).normalize();
        if (side.length() < 1e-3f) side = Vector3f.UNIT_X; // fallback if looking straight up/down

        for (int i = 0; i < SPHERE_RAYS - 1; i++) {
            float angle = i * FastMath.TWO_PI / (SPHERE_RAYS - 1);
            Vector3f offset = rot.mult(
                    side.mult(FastMath.cos(angle) * SPHERE_RADIUS)
            ).addLocal(
                    up.mult(FastMath.sin(angle) * SPHERE_RADIUS)
            );
            offsets.add(offset);
        }
        return offsets;
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {
        // not used
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        switch (name) {
            case MOUSE_LEFT  -> targetYaw   += value * sensitivity;
            case MOUSE_RIGHT -> targetYaw   -= value * sensitivity;
            case MOUSE_UP    -> targetPitch += value * sensitivity;
            case MOUSE_DOWN  -> targetPitch -= value * sensitivity;
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (TOGGLE_VIEW.equals(name) && isPressed) {
            thirdPerson = !thirdPerson;
        }
    }

    @Override
    public void setSpatial(Spatial spatial) {
        super.setSpatial(spatial);
        this.yaw = this.targetYaw = 0f;
        this.pitch = this.targetPitch = 0f;
        this.currentDistance = 0.01f;
        this.desiredDistance = 0.01f;
    }

    public boolean isThirdPerson() {
        return thirdPerson;
    }

    public void setThirdPerson(boolean thirdPerson) {
        this.thirdPerson = thirdPerson;
    }
}