package org.foxesworld.cge.modules.player.control;

import com.jme3.collision.CollisionResults;
import com.jme3.input.InputManager;
import com.jme3.input.controls.AnalogListener;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import org.foxesworld.cge.modules.inputManager.InputManagerModule;
import org.foxesworld.cge.modules.player.Player;
import org.foxesworld.cge.modules.player.config.PlayerConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides advanced camera control for a player, featuring smooth transitions,
 * first and third-person perspectives, and robust collision avoidance.
 * <p>
 * This control polls digital input from the central {@link InputManagerModule}
 * and listens for analog input (mouse/zoom) directly from JME3's InputManager.
 * All input actions are defined in an external configuration file.
 */
public final class PlayerCameraControl extends AbstractControl implements AnalogListener {

    private final Camera cam;
    private final InputManager input;
    private final InputManagerModule inputModule;
    private final Spatial sceneRoot;

    private final float eyeHeight;
    private final float sensitivity;
    private final float rotationSmoothingFactor;
    private final float minDistance, maxDistance, defaultDistance;
    private final float zoomStep, zoomEasingSpeed;
    private final float wallOffset, sphereRadius, positionalSmoothingFactor;
    private final int sphereRays;

    private float yaw, pitch, targetYaw, targetPitch;
    private boolean thirdPerson, viewTogglePressed;
    private float currentDistance, desiredZoomDistance;
    private final Vector3f smoothedCameraPosition;

    public PlayerCameraControl(Player player) {
        this.cam = player.getCam();
        this.input = player.getInput();
        this.inputModule = player.getEngine().getModuleManager().getModule(InputManagerModule.class);
        this.sceneRoot = player.getEngine().getRootNode();

        if (this.inputModule == null) {
            throw new IllegalStateException("InputManagerModule must be registered before Player module.");
        }

        PlayerConfig config = player.getPlayerConfig();
        this.eyeHeight = config.getPhysics().getEyeHeight();
        this.sensitivity = config.getSensitivity();
        this.rotationSmoothingFactor = FastMath.clamp(config.getMovement().getSmoothing(), 0f, 1f);
        this.minDistance = 1.0f;
        this.maxDistance = 16.0f;
        this.defaultDistance = 5.0f;
        this.zoomStep = 0.8f;
        this.zoomEasingSpeed = 8.0f;
        this.wallOffset = 0.36f;
        this.sphereRays = 240;
        this.sphereRadius = 0.5f;
        this.positionalSmoothingFactor = 0.25f;

        this.smoothedCameraPosition = new Vector3f();
        setupInputListeners();
    }

    private void setupInputListeners() {
        // Добавляем себя как слушателя для аналоговых действий,
        // которые уже были зарегистрированы модулем.
        input.addListener(this,
                "cam_left", "cam_right", "cam_up", "cam_down",
                "zoom_in", "zoom_out"
        );
        input.setCursorVisible(false);
    }

    @Override
    protected void controlUpdate(float tpf) {
        if (spatial == null || tpf < 1e-5f) return;

        handleViewToggle();

        float rotAlpha = 1f - FastMath.pow(1f - rotationSmoothingFactor, tpf * 60f);
        yaw = FastMath.interpolateLinear(rotAlpha, yaw, targetYaw);
        pitch = FastMath.interpolateLinear(rotAlpha, pitch, targetPitch);
        pitch = FastMath.clamp(pitch, -FastMath.HALF_PI * 0.9f, FastMath.HALF_PI * 0.9f);

        Quaternion rotation = new Quaternion().fromAngles(pitch, yaw, 0);
        Vector3f playerPos = spatial.getWorldTranslation().add(0, eyeHeight, 0);
        Vector3f cameraDirection = rotation.mult(Vector3f.UNIT_Z).negateLocal();

        Vector3f targetCamPos;
        if (thirdPerson) {
            float spring = 1f - FastMath.exp(-zoomEasingSpeed * tpf);
            currentDistance = FastMath.interpolateLinear(spring, currentDistance, desiredZoomDistance);
            float safeDistance = calculateSafeCameraDistance(playerPos, cameraDirection, currentDistance);
            targetCamPos = playerPos.add(cameraDirection.mult(safeDistance));
        } else {
            targetCamPos = playerPos;
        }

        float posAlpha = 1f - FastMath.pow(1f - positionalSmoothingFactor, tpf * 60f);
        smoothedCameraPosition.interpolateLocal(targetCamPos, posAlpha);

        if (thirdPerson) {
            cam.setLocation(smoothedCameraPosition);
            cam.lookAt(playerPos, Vector3f.UNIT_Y);
        } else {
            cam.setRotation(rotation);
            cam.setLocation(smoothedCameraPosition);
        }
    }

    private void handleViewToggle() {
        boolean nowPressed = inputModule.isActionActive("toggle_view");
        if (nowPressed && !viewTogglePressed) {
            thirdPerson = !thirdPerson;
            currentDistance = thirdPerson ? desiredZoomDistance : 0f;
        }
        viewTogglePressed = nowPressed;
    }

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
            sceneRoot.collideWith(ray, results);

            if (results.size() > 0) {
                float dist = results.getClosestCollision().getDistance() - wallOffset;
                if (dist < closestDist) {
                    closestDist = dist;
                }
            }
        }
        return FastMath.clamp(closestDist, minDistance, maxDist);
    }

    private List<Vector3f> getSphereOffsets(Vector3f backDir) {
        List<Vector3f> offsets = new ArrayList<>(sphereRays);
        offsets.add(Vector3f.ZERO.clone());

        Vector3f up = Vector3f.UNIT_Y;
        Vector3f side = backDir.cross(up).normalizeLocal();
        if (side.lengthSquared() < 1e-6f) side.set(Vector3f.UNIT_X);

        int circularRayCount = (sphereRays - 1) / 2;
        for (int i = 0; i < circularRayCount; i++) {
            float angle = i * FastMath.TWO_PI / circularRayCount;
            Vector3f offset = side.mult(FastMath.cos(angle) * sphereRadius)
                    .add(up.mult(FastMath.sin(angle) * sphereRadius));
            offsets.add(offset);
        }
        for (int i = 1; i <= 3; i++) {
            float height = sphereRadius * i / 3f;
            offsets.add(up.mult(height));
            offsets.add(up.mult(-height));
        }
        return offsets;
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        if (input.isCursorVisible()) return;

        float adjustedValue = value * sensitivity;
        switch (name) {
            case "cam_right" -> targetYaw -= adjustedValue;
            case "cam_left" -> targetYaw += adjustedValue;
            case "cam_up" -> targetPitch += adjustedValue;
            case "cam_down" -> targetPitch -= adjustedValue;
            case "zoom_in" -> handleZoom(-zoomStep * value * 2f);
            case "zoom_out" -> handleZoom(zoomStep * value * 2f);
        }
    }

    private void handleZoom(float delta) {
        if (thirdPerson) {
            desiredZoomDistance = FastMath.clamp(desiredZoomDistance + delta, minDistance, maxDistance);
        }
    }

    @Override
    public void setSpatial(Spatial spatial) {
        super.setSpatial(spatial);
        if (spatial != null) {
            yaw = targetYaw = 0f;
            pitch = targetPitch = 0f;
            desiredZoomDistance = defaultDistance;
            currentDistance = thirdPerson ? desiredZoomDistance : 0f;
            smoothedCameraPosition.set(cam.getLocation());
        }
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {}
    public boolean isThirdPerson() { return thirdPerson; }
    public void setThirdPerson(boolean thirdPerson) { this.thirdPerson = thirdPerson; }
    public float getZoomDistance() { return desiredZoomDistance; }
    public void setZoomDistance(float distance) {
        this.desiredZoomDistance = FastMath.clamp(distance, minDistance, maxDistance);
    }
}