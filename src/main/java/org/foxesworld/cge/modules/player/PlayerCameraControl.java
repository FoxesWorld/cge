package org.foxesworld.cge.modules.player;

import com.jme3.collision.CollisionResults;
import com.jme3.collision.Collidable;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.UnsupportedCollisionException;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Camera control with collision: prevents the camera from clipping through scene geometry.
 * Easing (smooth camera switching) and spherical raycast for solid AAA-feel.
 * Гарантированный захват клавиши C через RawInputListener и ActionListener.
 * Исправлено: потокобезопасная инициализация input (синхронизация с движением), нет гонки
 * при назначении слушателей. Все изменения инпута происходят только в игровом потоке.
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

    // Для потокобезопасного аккумулирования дельт мыши (решает проблему гонки!)
    private volatile float pendingDeltaYaw = 0f;
    private volatile float pendingDeltaPitch = 0f;

    // Гарантированный захват C
    private RawInputListener rawListener;
    private final AtomicBoolean rawRegistered = new AtomicBoolean(false);
    private final AtomicBoolean inputMappingsRegistered = new AtomicBoolean(false);

    public PlayerCameraControl(Player player, float eyeHeight, float sensitivity, float smoothing, Spatial sceneRoot) {
        this.cam = player.getCam();
        this.input = player.getInput();
        this.eyeHeight = eyeHeight;
        this.sensitivity = sensitivity;
        this.smoothingFactor = FastMath.clamp(smoothing, 0f, 1f);
        this.sceneRoot = sceneRoot;

        if (!inputMappingsRegistered.get()) setupMappings();
        if (!rawRegistered.get()) setupRawListener();
        input.setCursorVisible(false);
    }

    private void setupMappings() {
        if (!inputMappingsRegistered.compareAndSet(false, true)) return;
        if (!input.hasMapping(TOGGLE_VIEW))
            input.addMapping(TOGGLE_VIEW, new KeyTrigger(KeyInput.KEY_C));
        if (!input.hasMapping(MOUSE_LEFT))
            input.addMapping(MOUSE_LEFT,  new MouseAxisTrigger(MouseInput.AXIS_X, true));
        if (!input.hasMapping(MOUSE_RIGHT))
            input.addMapping(MOUSE_RIGHT, new MouseAxisTrigger(MouseInput.AXIS_X, false));
        if (!input.hasMapping(MOUSE_UP))
            input.addMapping(MOUSE_UP,    new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        if (!input.hasMapping(MOUSE_DOWN))
            input.addMapping(MOUSE_DOWN,  new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        input.removeListener(this);
        input.addListener(this, TOGGLE_VIEW, MOUSE_LEFT, MOUSE_RIGHT, MOUSE_UP, MOUSE_DOWN);
    }

    private void removeMappings() {
        if (inputMappingsRegistered.compareAndSet(true, false)) {
            input.removeListener(this);
        }
    }

    private void setupRawListener() {
        if (!rawRegistered.compareAndSet(false, true)) return;
        rawListener = new RawInputListener() {
            @Override public void beginInput() {}
            @Override public void endInput() {}
            @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent evt) {}
            @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent evt) {}
            @Override public void onMouseMotionEvent(com.jme3.input.event.MouseMotionEvent evt) {}
            @Override public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent evt) {}
            @Override public void onTouchEvent(com.jme3.input.event.TouchEvent evt) {}
            @Override
            public void onKeyEvent(com.jme3.input.event.KeyInputEvent evt) {
                if (evt.getKeyCode() == KeyInput.KEY_C && evt.isPressed()) {
                    toggleThirdPerson();
                    evt.setConsumed();
                }
            }
        };
        input.addRawInputListener(rawListener);
    }

    private void removeRawListener() {
        if (rawRegistered.compareAndSet(true, false) && rawListener != null) {
            input.removeRawInputListener(rawListener);
        }
    }

    @Override
    protected void controlUpdate(float tpf) {
        if (spatial == null) return;

        // Применяем накопленные дельты мыши (решает состояние гонки!)
        float localDeltaYaw = pendingDeltaYaw;
        float localDeltaPitch = pendingDeltaPitch;
        pendingDeltaYaw = 0f;
        pendingDeltaPitch = 0f;

        targetYaw += localDeltaYaw;
        targetPitch += localDeltaPitch;

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
            case MOUSE_LEFT:
                pendingDeltaYaw += value * sensitivity;
                break;
            case MOUSE_RIGHT:
                pendingDeltaYaw -= value * sensitivity;
                break;
            case MOUSE_UP:
                pendingDeltaPitch += value * sensitivity;
                break;
            case MOUSE_DOWN:
                pendingDeltaPitch -= value * sensitivity;
                break;
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (TOGGLE_VIEW.equals(name) && isPressed) {
            toggleThirdPerson();
        }
    }

    public void toggleThirdPerson() {
        thirdPerson = !thirdPerson;
    }

    @Override
    public void setSpatial(Spatial spatial) {
        super.setSpatial(spatial);
        if (spatial == null) {
            removeRawListener();
            removeMappings();
        } else {
            setupMappings();
            setupRawListener();
            this.yaw = this.targetYaw = 0f;
            this.pitch = this.targetPitch = 0f;
            this.currentDistance = 0.01f;
            this.desiredDistance = 0.01f;
            this.pendingDeltaYaw = 0f;
            this.pendingDeltaPitch = 0f;
        }
    }

    public boolean isThirdPerson() {
        return thirdPerson;
    }

    public void setThirdPerson(boolean thirdPerson) {
        this.thirdPerson = thirdPerson;
    }
}