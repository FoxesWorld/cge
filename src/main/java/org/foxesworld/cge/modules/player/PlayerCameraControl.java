package org.foxesworld.cge.modules.player;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.collision.UnsupportedCollisionException;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced player camera control with first/third person switching,
 * collision handling via spherical raycasting, and smoothing.
 * Добавлена поддержка приближения/отдаления колесом мыши в третьем лице.
 */
public class PlayerCameraControl extends AbstractControl implements AnalogListener, ActionListener {

    private static final String TOGGLE_VIEW = "Toggle_View";
    private static final String MOUSE_LEFT  = "Mouse_Left";
    private static final String MOUSE_RIGHT = "Mouse_Right";
    private static final String MOUSE_UP    = "Mouse_Up";
    private static final String MOUSE_DOWN  = "Mouse_Down";
    private static final String ZOOM_IN     = "Zoom_In";
    private static final String ZOOM_OUT    = "Zoom_Out";

    private final Camera cam;
    private final InputManager input;
    private final Spatial sceneRoot;

    private final float eyeHeight;
    private final float sensitivity;
    private final float smoothingFactor;
    private final float minDistance;
    private final float maxDistance;
    private final float wallOffset;
    private final float easingSpeed;
    private final int sphereRays;
    private final float sphereRadius;

    private float yaw, pitch;
    private float targetYaw, targetPitch;
    private boolean thirdPerson;
    private float currentDistance, desiredDistance;

    // Настройки зума
    private float zoomDistance;
    private final float zoomStep = 0.8f;
    private final float defaultDistance = 5.0f;

    private final Vector3f tempVec = new Vector3f();

    public PlayerCameraControl(Player player, float eyeHeight, float sensitivity, float smoothing, Spatial sceneRoot) {
        this.cam = player.getCam();
        this.input = player.getInput();
        this.sceneRoot = sceneRoot;

        this.eyeHeight = eyeHeight;
        this.sensitivity = sensitivity;
        this.smoothingFactor = FastMath.clamp(smoothing, 0f, 1f);

        this.minDistance = 1.2f;
        this.maxDistance = 16.0f;
        this.wallOffset = 0.36f;
        this.easingSpeed = 5.5f;

        this.sphereRays = 7;
        this.sphereRadius = 0.28f;

        this.zoomDistance = defaultDistance;
        this.currentDistance = this.desiredDistance = this.zoomDistance;

        setupInputMappings();
        input.setCursorVisible(false);
    }

    private void setupInputMappings() {
        input.addMapping(TOGGLE_VIEW, new KeyTrigger(KeyInput.KEY_V));
        input.addMapping(MOUSE_LEFT,  new MouseAxisTrigger(MouseInput.AXIS_X, true));
        input.addMapping(MOUSE_RIGHT, new MouseAxisTrigger(MouseInput.AXIS_X, false));
        input.addMapping(MOUSE_UP,    new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        input.addMapping(MOUSE_DOWN,  new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        // Поддержка колесика мыши для зума
        input.addMapping(ZOOM_IN,  new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false)); // колесо вперед (увеличить)
        input.addMapping(ZOOM_OUT, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));  // колесо назад (отдалить)
        input.addListener(this, TOGGLE_VIEW, MOUSE_LEFT, MOUSE_RIGHT, MOUSE_UP, MOUSE_DOWN, ZOOM_IN, ZOOM_OUT);
    }

    @Override
    protected void controlUpdate(float tpf) {
        if (spatial == null) return;

        float alpha = 1f - FastMath.pow(1f - smoothingFactor, tpf * 60f);
        yaw = FastMath.interpolateLinear(alpha, yaw, targetYaw);
        pitch = FastMath.interpolateLinear(alpha, pitch, targetPitch);

        float maxPitch = FastMath.DEG_TO_RAD * 80f;
        pitch = FastMath.clamp(pitch, -maxPitch, maxPitch);

        Quaternion rot = new Quaternion().fromAngles(pitch, yaw, 0);
        Vector3f playerPos = spatial.getWorldTranslation().add(0, eyeHeight, 0);

        desiredDistance = thirdPerson ? zoomDistance : 0.01f;
        float spring = FastMath.clamp(1f - FastMath.exp(-easingSpeed * tpf), 0.2f, 1f);
        currentDistance += (desiredDistance - currentDistance) * spring;

        Vector3f backDir = rot.mult(Vector3f.UNIT_Z).negateLocal();
        Vector3f desiredPos = playerPos.add(backDir.mult(currentDistance));
        Vector3f camPos = calculateCameraPosition(playerPos, desiredPos, backDir);

        if (thirdPerson && currentDistance > minDistance) {
            cam.setLocation(camPos);
            cam.lookAt(playerPos, Vector3f.UNIT_Y);
        } else {
            cam.setRotation(rot);
            cam.setLocation(playerPos);
        }
    }

    private Vector3f calculateCameraPosition(Vector3f origin, Vector3f target, Vector3f backDir) {
        float closestDist = currentDistance;
        for (Vector3f offset : getSphereOffsets(backDir)) {
            Vector3f rayStart = origin.add(offset);
            Vector3f rayEnd = target.add(offset);
            Vector3f dir = rayEnd.subtract(rayStart).normalizeLocal();
            float maxDist = rayEnd.distance(rayStart);

            Ray ray = new Ray(rayStart, dir);
            ray.setLimit(maxDist + wallOffset);

            CollisionResults results = new CollisionResults();
            try {
                sceneRoot.collideWith(ray, results);
            } catch (UnsupportedCollisionException ignored) {}

            if (results.size() > 0) {
                float dist = results.getClosestCollision().getDistance() - wallOffset;
                closestDist = FastMath.clamp(dist, minDistance, closestDist);
            }
        }
        return origin.add(backDir.mult(closestDist));
    }

    private List<Vector3f> getSphereOffsets(Vector3f backDir) {
        List<Vector3f> offsets = new ArrayList<>(sphereRays);
        offsets.add(Vector3f.ZERO.clone());

        Vector3f up = Vector3f.UNIT_Y;
        Vector3f side = backDir.cross(up).normalizeLocal();
        if (side.length() < 1e-3f) side = Vector3f.UNIT_X;

        for (int i = 0; i < sphereRays - 1; i++) {
            float angle = i * FastMath.TWO_PI / (sphereRays - 1);
            Vector3f offset = side.mult(FastMath.cos(angle) * sphereRadius)
                    .add(up.mult(FastMath.sin(angle) * sphereRadius));
            offsets.add(offset);
        }
        return offsets;
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {
        // no rendering logic
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {

        if (input.isCursorVisible() && (
                name.equals(MOUSE_LEFT) ||
                        name.equals(MOUSE_RIGHT) ||
                        name.equals(MOUSE_UP) ||
                        name.equals(MOUSE_DOWN))) {
            return;
        }

        switch (name) {
            case MOUSE_LEFT  -> targetYaw   += value * sensitivity;
            case MOUSE_RIGHT -> targetYaw   -= value * sensitivity;
            case MOUSE_UP    -> targetPitch += value * sensitivity;
            case MOUSE_DOWN  -> targetPitch -= value * sensitivity;
            case ZOOM_IN -> {
                if (thirdPerson) {
                    zoomDistance -= zoomStep * value * 2f;
                    zoomDistance = FastMath.clamp(zoomDistance, minDistance, maxDistance);
                    desiredDistance = currentDistance = zoomDistance;
                }
            }
            case ZOOM_OUT -> {
                if (thirdPerson) {
                    zoomDistance += zoomStep * value * 2f;
                    zoomDistance = FastMath.clamp(zoomDistance, minDistance, maxDistance);
                    desiredDistance = currentDistance = zoomDistance;
                }
            }
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
        yaw = targetYaw = 0f;
        pitch = targetPitch = 0f;
        // Новый "зум" сбрасывается только в третий раз
        currentDistance = desiredDistance = zoomDistance = defaultDistance;
    }

    public boolean isThirdPerson() {
        return thirdPerson;
    }

    public void setThirdPerson(boolean thirdPerson) {
        this.thirdPerson = thirdPerson;
    }

    public Vector3f getDesiredCameraPosition() {
        if (spatial == null) return null;

        float pitchRad = pitch;
        float yawRad = yaw;

        Quaternion rot = new Quaternion().fromAngles(pitchRad, yawRad, 0);
        Vector3f playerPos = spatial.getWorldTranslation().add(0, eyeHeight, 0);

        float dist = thirdPerson ? zoomDistance : 0.01f;
        Vector3f backDir = rot.mult(Vector3f.UNIT_Z).negateLocal();
        Vector3f desiredPos = playerPos.add(backDir.mult(dist));
        return calculateCameraPosition(playerPos, desiredPos, backDir);
    }
    /** Позволяет программно задать текущий зум (например, для сохранения/восстановления состояния) */
    public void setZoomDistance(float distance) {
        this.zoomDistance = FastMath.clamp(distance, minDistance, maxDistance);
    }
    public float getZoomDistance() {
        return zoomDistance;
    }
}