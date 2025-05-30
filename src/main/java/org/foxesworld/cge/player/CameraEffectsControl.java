package org.foxesworld.cge.player;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.Node;

/**
 * Эффекты камеры: усиленный bobbing при ходьбе, яркий прыжок и мощный bounce при приземлении.
 */
public class CameraEffectsControl extends AbstractControl {

    private final Camera cam;
    private final Node camNode;
    private final MovementControl moveCtrl;

    // bobbing
    private float walkTime = 0;
    private final float bobFrequency;
    private final Vector3f bobAmplitude; // x: sway, y: bob, z: base height
    private final float bobTilt;         // угол наклона корпуса

    // прыжок
    private float jumpOffset = 0;
    private float jumpVelocity = 0;
    private final float jumpSpringK;     // жесткая пружина прыжка
    private final float jumpDamping;

    // приземление
    private float landOffset = 0;
    private float landVelocity = 0;
    private final float landSpringK;     // жесткая пружина приземления
    private final float landDamping;

    public CameraEffectsControl(Camera cam, Node camNode,
                                MovementControl moveCtrl,
                                float bobFrequency,
                                Vector3f bobAmplitude,
                                float bobTilt,
                                float jumpSpringK,
                                float jumpDamping,
                                float landSpringK,
                                float landDamping) {
        this.cam            = cam;
        this.camNode        = camNode;
        this.moveCtrl       = moveCtrl;
        this.bobFrequency   = bobFrequency;
        this.bobAmplitude   = bobAmplitude;
        this.bobTilt        = bobTilt;
        this.jumpSpringK    = jumpSpringK;
        this.jumpDamping    = jumpDamping;
        this.landSpringK    = landSpringK;
        this.landDamping    = landDamping;
    }

    /** Вызывать при старте прыжка */
    public void notifyJumpStart() {
        jumpOffset = 0;
        jumpVelocity = 5f; // мощный стартовый импульс вверх
    }

    /** Вызывать при приземлении */
    public void notifyLanding(float peakHeight) {
        landVelocity = peakHeight * 8f; // усиливаем приземление
    }

    @Override
    protected void controlUpdate(float tpf) {
        Vector3f base = new Vector3f(0, bobAmplitude.z, 0);
        Vector3f offset = new Vector3f();

        // 1) bobbing при ходьбе
        if (moveCtrl.isMoving()) {
            walkTime += tpf * bobFrequency;
            float sin = FastMath.sin(walkTime * FastMath.TWO_PI);
            float cos = FastMath.cos(walkTime * FastMath.TWO_PI);
            offset.y += sin * bobAmplitude.y;
            offset.x += cos * bobAmplitude.x;
            camNode.setLocalRotation(
                    new Quaternion().fromAngles(sin * bobTilt, 0f, cos * bobTilt * 0.5f)
            );
        } else {
            camNode.setLocalRotation(new Quaternion());
        }

        // 2) импульс прыжка
        if (jumpVelocity != 0 || jumpOffset != 0) {
            // spring physics
            float f = -jumpSpringK * jumpOffset;
            jumpVelocity += f * tpf;
            jumpVelocity *= FastMath.clamp(1 - jumpDamping * tpf, 0, 1);
            jumpOffset += jumpVelocity * tpf;
            offset.y += jumpOffset; // подъем камеры
        }

        // 3) bounce приземления
        if (landVelocity != 0 || landOffset != 0) {
            float f = -landSpringK * landOffset;
            landVelocity += f * tpf;
            landVelocity *= FastMath.clamp(1 - landDamping * tpf, 0, 1);
            landOffset += landVelocity * tpf;
            offset.y -= landOffset; // опускание камеры
        }

        // финальный сдвиг
        camNode.setLocalTranslation(base.add(offset));
        cam.setLocation(camNode.getWorldTranslation());
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm,
                                 com.jme3.renderer.ViewPort vp) {
        // no-op
    }
}
