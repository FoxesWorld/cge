package org.foxesworld.cge.player;

import com.jme3.input.InputManager;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.control.AbstractControl;

public class FirstPersonCameraControl extends AbstractControl {

    private static final float SENSITIVITY = 0.3f;
    private float yaw = 0, pitch = 0;
    private final Camera cam;
    private final Node camNode;

    public FirstPersonCameraControl(Camera cam, InputManager input, Node player, float headHeight) {
        this.cam = cam;
        camNode = new Node("CamNode");
        camNode.setLocalTranslation(0, headHeight, 0);
        camNode.addControl(new com.jme3.scene.control.CameraControl(
                cam, com.jme3.scene.control.CameraControl.ControlDirection.SpatialToCamera
        ));
        player.attachChild(camNode);

        // слушаем сырые события мыши
        input.addRawInputListener(new RawInputListener() {
            @Override public void onMouseMotionEvent(MouseMotionEvent evt) {
                input.setCursorVisible(false);
                yaw   -= evt.getDX() * SENSITIVITY * FastMath.DEG_TO_RAD;
                pitch += evt.getDY() * SENSITIVITY * FastMath.DEG_TO_RAD; // inverted Y if needed
                pitch = FastMath.clamp(pitch, -FastMath.HALF_PI, FastMath.HALF_PI);
                evt.setConsumed();
            }
            @Override public void beginInput() {}
            @Override public void endInput() {}
            @Override public void onKeyEvent(com.jme3.input.event.KeyInputEvent e) {}
            @Override public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent e) {}
            @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent e) {}
            @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent e) {}
            @Override public void onTouchEvent(com.jme3.input.event.TouchEvent e) {}
        });
    }

    @Override
    protected void controlUpdate(float tpf) {
        // тело игрока поворачивает только по yaw
        spatial.setLocalRotation(new Quaternion().fromAngles(0, yaw, 0));
        // голова (камера) — по pitch
        camNode.setLocalRotation(new Quaternion().fromAngles(pitch, 0, 0));
        // камера сама стоит на camNode
        cam.setLocation(camNode.getWorldTranslation());
    }

    @Override public void controlRender(com.jme3.renderer.RenderManager rm,
                                        com.jme3.renderer.ViewPort vp) {}
}
