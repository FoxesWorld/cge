package org.foxesworld.cge.modules.player.control;

import com.jme3.collision.CollisionResults;
import com.jme3.collision.UnsupportedCollisionException;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import org.foxesworld.cge.modules.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides advanced camera control for a player, featuring smooth transitions,
 * first and third-person perspectives, and robust collision avoidance.
 * <p>
 * This implementation uses positional smoothing to eliminate camera jitter
 * in third-person mode, ensuring a stable view even near complex geometry.
 * Mouse wheel input controls the zoom level in third-person view.
 */
public final class PlayerCameraControl extends AbstractControl implements AnalogListener, ActionListener {

    // --- Input Mappings ---
    private static final String TOGGLE_VIEW = "Toggle_View";
    private static final String MOUSE_LEFT = "Mouse_Left";
    private static final String MOUSE_RIGHT = "Mouse_Right";
    private static final String MOUSE_UP = "Mouse_Up";
    private static final String MOUSE_DOWN = "Mouse_Down";
    private static final String ZOOM_IN = "Zoom_In";
    private static final String ZOOM_OUT = "Zoom_Out";

    // --- Dependencies ---
    private final Camera cam;
    private final InputManager input;
    private final Spatial sceneRoot;

    // --- Configuration ---
    private final float eyeHeight;
    private final float sensitivity;
    private final float rotationSmoothingFactor;
    private final float minDistance;
    private final float maxDistance;
    private final float defaultDistance;
    private final float zoomStep;
    private final float wallOffset;
    private final float zoomEasingSpeed;
    private final int sphereRays;
    private final float sphereRadius;
    private final float positionalSmoothingFactor; // Key factor for jitter removal

    // --- State ---
    private float yaw, pitch;
    private float targetYaw, targetPitch;
    private boolean thirdPerson;
    private float currentDistance;
    private float desiredZoomDistance;
    private final Vector3f smoothedCameraPosition; // Used to store the final smoothed camera position

    public PlayerCameraControl(Player player) {
        this.cam = player.getCam();
        this.input = player.getInput();
        this.sceneRoot = player.getEngine().getRootNode();

        this.eyeHeight = player.getPlayerConfig().getPhysics().getEyeHeight();
        this.sensitivity = player.getPlayerConfig().getSensitivity();
        this.rotationSmoothingFactor = FastMath.clamp(player.getPlayerConfig().getMovement().getSmoothing(), 0f, 1f);

        // Camera distance and zoom settings
        this.minDistance = 1.0f;
        this.maxDistance = 16.0f;
        this.defaultDistance = 5.0f;
        this.zoomStep = 0.8f;
        this.zoomEasingSpeed = 8.0f; // Controls how quickly zoom snaps

        // Collision detection settings
        this.wallOffset = 0.36f;
        this.sphereRays = 240;
        this.sphereRadius = 0.5f;

        // Anti-jitter smoothing setting. Lower values mean more smoothing.
        // A value of 0.2 to 0.3 is usually a good starting point.
        this.positionalSmoothingFactor = 0.25f;

        // Initialize state
        this.smoothedCameraPosition = new Vector3f();

        setupInputMappings();
        input.setCursorVisible(false);
    }

    private void setupInputMappings() {
        input.addMapping(TOGGLE_VIEW, new KeyTrigger(KeyInput.KEY_C));
        input.addMapping(MOUSE_LEFT, new MouseAxisTrigger(MouseInput.AXIS_X, true));
        input.addMapping(MOUSE_RIGHT, new MouseAxisTrigger(MouseInput.AXIS_X, false));
        input.addMapping(MOUSE_UP, new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        input.addMapping(MOUSE_DOWN, new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        input.addMapping(ZOOM_IN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        input.addMapping(ZOOM_OUT, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        input.addListener(this, TOGGLE_VIEW, MOUSE_LEFT, MOUSE_RIGHT, MOUSE_UP, MOUSE_DOWN, ZOOM_IN, ZOOM_OUT);
    }

    @Override
    protected void controlUpdate(float tpf) {
        if (spatial == null || tpf < 1e-5f) {
            return;
        }

        // --- 1. Calculate Camera Rotation ---
        // Smoothly interpolate the camera's rotation angles (yaw and pitch).
        float rotAlpha = 1f - FastMath.pow(1f - rotationSmoothingFactor, tpf * 60f);
        yaw = FastMath.interpolateLinear(rotAlpha, yaw, targetYaw);
        pitch = FastMath.interpolateLinear(rotAlpha, pitch, targetPitch);
        pitch = FastMath.clamp(pitch, -FastMath.HALF_PI * 0.9f, FastMath.HALF_PI * 0.9f);

        Quaternion rotation = new Quaternion().fromAngles(pitch, yaw, 0);
        Vector3f playerPos = spatial.getWorldTranslation().add(0, eyeHeight, 0);
        Vector3f cameraDirection = rotation.mult(Vector3f.UNIT_Z).negateLocal();

        // --- 2. Determine Target Camera Position ---
        Vector3f targetCamPos;

        if (thirdPerson) {
            // Smoothly adjust the current camera distance towards the desired zoom level.
            float spring = 1f - FastMath.exp(-zoomEasingSpeed * tpf);
            currentDistance = FastMath.interpolateLinear(spring, currentDistance, desiredZoomDistance);

            // Calculate the safest possible distance for the camera to avoid clipping.
            float safeDistance = calculateSafeCameraDistance(playerPos, cameraDirection, currentDistance);

            // The raw, potentially jittery, target position.
            targetCamPos = playerPos.add(cameraDirection.mult(safeDistance));

        } else { // First-person view
            targetCamPos = playerPos;
        }

        // --- 3. Apply Positional Smoothing (The Anti-Jitter Magic) ---
        // Instead of setting the camera position directly, we smoothly interpolate
        // the current camera position towards the target position. This filters out
        // the rapid changes from the collision system that cause jitter.
        float posAlpha = 1f - FastMath.pow(1f - positionalSmoothingFactor, tpf * 60f);
        smoothedCameraPosition.interpolateLocal(targetCamPos, posAlpha);


        // --- 4. Set Final Camera Transform ---
        if (thirdPerson) {
            cam.setLocation(smoothedCameraPosition);
            cam.lookAt(playerPos, Vector3f.UNIT_Y);
        } else {
            cam.setRotation(rotation);
            cam.setLocation(smoothedCameraPosition); // Already equals playerPos
        }
    }

    /**
     * Calculates a safe camera distance by casting rays from the player to the
     * desired camera position to detect obstacles.
     *
     * @return A safe distance between {@code minDistance} and {@code maxDist}.
     */
    private float calculateSafeCameraDistance(Vector3f origin, Vector3f direction, float maxDist) {
        float closestDist = maxDist;
        Vector3f target = origin.add(direction.mult(maxDist));
        List<Vector3f> offsets = getSphereOffsets(direction);

        for (Vector3f offset : offsets) {
            Vector3f rayStart = origin.add(offset);
            Vector3f rayEnd = target.add(offset);
            Vector3f rayDir = rayEnd.subtract(rayStart).normalizeLocal();
            float rayLimit = rayEnd.distance(rayStart);

            if (rayLimit < 0.001f) continue;

            Ray ray = new Ray(rayStart, rayDir);
            ray.setLimit(rayLimit + wallOffset);

            CollisionResults results = new CollisionResults();
            try {
                sceneRoot.collideWith(ray, results);
            } catch (UnsupportedCollisionException ignored) {
            }

            if (results.size() > 0) {
                float dist = results.getClosestCollision().getDistance() - wallOffset;
                if (dist < closestDist) {
                    closestDist = dist;
                }
            }
        }
        return FastMath.clamp(closestDist, minDistance, maxDist);
    }

    /**
     * Generates a set of offset vectors for spherical raycasting to detect collisions.
     */
    private List<Vector3f> getSphereOffsets(Vector3f backDir) {
        List<Vector3f> offsets = new ArrayList<>(sphereRays);
        offsets.add(Vector3f.ZERO.clone()); // Center ray

        Vector3f up = Vector3f.UNIT_Y;
        Vector3f side = backDir.cross(up).normalizeLocal();
        if (side.lengthSquared() < 1e-6f) {
            side.set(Vector3f.UNIT_X); // Handle looking straight up or down
        }

        // Circular rays
        int circularRayCount = (sphereRays - 1) / 2;
        for (int i = 0; i < circularRayCount; i++) {
            float angle = i * FastMath.TWO_PI / circularRayCount;
            Vector3f offset = side.mult(FastMath.cos(angle) * sphereRadius)
                    .add(up.mult(FastMath.sin(angle) * sphereRadius));
            offsets.add(offset);
        }

        // Additional vertical rays for floor/ceiling detection
        for (int i = 1; i <= 3; i++) {
            float height = sphereRadius * i / 3f;
            offsets.add(up.mult(height));
            offsets.add(up.mult(-height));
        }
        return offsets;
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        if (input.isCursorVisible()) {
            return;
        }
        switch (name) {
            case MOUSE_LEFT -> targetYaw += value * sensitivity;
            case MOUSE_RIGHT -> targetYaw -= value * sensitivity;
            case MOUSE_UP -> targetPitch += value * sensitivity;
            case MOUSE_DOWN -> targetPitch -= value * sensitivity;
            case ZOOM_IN -> handleZoom(-zoomStep * value * 2f);
            case ZOOM_OUT -> handleZoom(zoomStep * value * 2f);
        }
    }

    private void handleZoom(float delta) {
        if (thirdPerson) {
            desiredZoomDistance = FastMath.clamp(desiredZoomDistance + delta, minDistance, maxDistance);
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (TOGGLE_VIEW.equals(name) && isPressed) {
            thirdPerson = !thirdPerson;
            if (thirdPerson) {
                // When switching to 3rd person, instantly set distance
                currentDistance = desiredZoomDistance;
            } else {
                // When switching to 1st person, reset distance
                currentDistance = 0f;
            }
        }
    }

    @Override
    public void setSpatial(Spatial spatial) {
        super.setSpatial(spatial);
        if (spatial != null) {
            // Reset state when attaching to a new spatial
            yaw = targetYaw = 0f;
            pitch = targetPitch = 0f;
            desiredZoomDistance = defaultDistance;
            currentDistance = thirdPerson ? desiredZoomDistance : 0f;
            smoothedCameraPosition.set(cam.getLocation()); // Initialize with current camera position
        }
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {
        // This control does not render anything.
    }

    public boolean isThirdPerson() {
        return thirdPerson;
    }

    public void setThirdPerson(boolean thirdPerson) {
        this.thirdPerson = thirdPerson;
    }

    public float getZoomDistance() {
        return desiredZoomDistance;
    }

    /**
     * Sets the desired zoom distance for the third-person camera.
     * The value will be clamped between min and max distance.
     */
    public void setZoomDistance(float distance) {
        this.desiredZoomDistance = FastMath.clamp(distance, minDistance, maxDistance);
    }
}