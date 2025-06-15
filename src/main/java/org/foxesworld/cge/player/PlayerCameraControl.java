package org.foxesworld.cge.player;

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

/**
 * Camera control with collision: prevents the camera from clipping through scene geometry.
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

    private float distance = 5f;              // third-person distance
    private float minDistance = 1.2f;         // minimum distance to avoid inside
    private float wallOffset = 0.3f;          // offset from collision surface

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

        if (thirdPerson) {
            Vector3f backDir = rot.mult(Vector3f.UNIT_Z).negateLocal();
            Vector3f desiredPos = playerPos.add(backDir.mult(distance));

            // Ray-cast from player head toward desired camera position
            Vector3f dir = desiredPos.subtract(playerPos).normalizeLocal();
            Ray ray = new Ray(playerPos, dir);
            CollisionResults results = new CollisionResults();
            try {
                ((Collidable) sceneRoot).collideWith(ray, results);
            } catch (UnsupportedCollisionException ignored) {
                // no collision support
            }

            Vector3f camPos = desiredPos;
            if (results.size() > 0) {
                CollisionResult closest = results.getClosestCollision();
                float dist = closest.getDistance() - wallOffset;
                dist = FastMath.clamp(dist, minDistance, distance);
                camPos = playerPos.add(dir.mult(dist));
            } else {
                // ensure minimum distance from player
                if (camPos.distance(playerPos) < minDistance) {
                    camPos = playerPos.add(backDir.mult(minDistance));
                }
            }

            cam.setLocation(camPos);
            cam.lookAt(playerPos, Vector3f.UNIT_Y);
        } else {
            cam.setRotation(rot);
            cam.setLocation(playerPos);
        }
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
    }

    public boolean isThirdPerson() {
        return thirdPerson;
    }

    public void setThirdPerson(boolean thirdPerson) {
        this.thirdPerson = thirdPerson;
    }
}
