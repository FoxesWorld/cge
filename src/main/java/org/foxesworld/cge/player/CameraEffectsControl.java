package org.foxesworld.cge.player;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.Node;

public class CameraEffectsControl extends AbstractControl {

    private final Camera cam;
    private final Node camNode;
    private final MovementControl moveCtrl;

    // bobbing
    private float walkTime = 0;
    private final float bobFrequency;
    private final Vector3f bobAmplitude; // x: sway, y: bob, z: base height
    private final float bobTilt;         // угол наклона корпуса в радианах

    // landing spring
    private float landOffset = 0;
    private float landVelocity = 0;
    private final float springK;   // жёсткость пружины
    private final float damping;   // демпфирование
    public CameraEffectsControl(Player player) {
        this.cam            = player.getCam();
        this.camNode        = player.getCamNode();
        this.moveCtrl       = player.getMovementControl();
        this.bobFrequency   = 1.8f;
        this.bobTilt      = FastMath.DEG_TO_RAD * 3f;
        this.bobAmplitude = new Vector3f(
                player.getShape().getRadius() * 0.1f,    // боковое колебание
                player.getShape().getHeight() * 0.03f,   // вертикальное колебание
                1.6f         // базовая высота камеры
        );
        this.springK        = 50f;
        this.damping        = 8f;

    }

    public void notifyJumpStart() {
        // обнуляем приземление, камера подпрыгнет заново
        landOffset = 0;
        landVelocity = 0;
    }

    public void notifyLanding(float peakHeight) {
        // начальная скорость пружины пропорциональна высоте
        landVelocity = peakHeight * 2f;
    }

    @Override
    protected void controlUpdate(float tpf) {
        Vector3f base = new Vector3f(0, bobAmplitude.z, 0);
        Vector3f offset = new Vector3f();

        // 1) bobbing при ходьбе
        if (moveCtrl.isMoving()) {
            walkTime += tpf * bobFrequency;
            float bobSin = FastMath.sin(walkTime * FastMath.TWO_PI);
            float bobCos = FastMath.cos(walkTime * FastMath.TWO_PI);
            // вертикальный bob
            offset.y += bobSin * bobAmplitude.y;
            // горизонтальный sway
            offset.x += bobCos * bobAmplitude.x;
            // наклон вперёд-назад
            float tilt = bobSin * bobTilt;
            camNode.setLocalRotation(new Quaternion().fromAngles(tilt, 0, 0));
        } else {
            // сброс наклона
            camNode.setLocalRotation(new Quaternion().fromAngles(0, 0, 0));
        }

        // 2) spring-landing: имитируем пружину
        if (FastMath.abs(landOffset) > 0.001f || FastMath.abs(landVelocity) > 0.001f) {
            // F = -k*x, a = F, verlet:
            float force = -springK * landOffset;
            landVelocity += force * tpf;
            landVelocity *= FastMath.clamp(1 - damping * tpf, 0, 1);
            landOffset += landVelocity * tpf;
            offset.y -= landOffset;
        }

        // 3) применяем итог
        camNode.setLocalTranslation(base.add(offset));
        cam.setLocation(camNode.getWorldTranslation());
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm,
                                 com.jme3.renderer.ViewPort vp) {
        // no-op
    }
}
