package org.foxesworld.cge.player;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.Node;

/**
 * Реалистичные эффекты камеры: bobbing и spring-landing,
 * параметризованные по размеру персонажа и скорости движения.
 */
public class CameraEffectsControl extends AbstractControl {

    private final Camera cam;
    private final Node camNode;
    private final MovementControl moveCtrl;
    private final float characterHeight;
    private final float characterRadius;

    // bobbing
    private float walkTime = 0;
    private final float bobBaseFreq;      // базовая частота
    private final Vector3f bobAmplitude;  // амплитуды: sway, bob, base height
    private final float bobTilt;          // максимальный наклон

    // spring-landing
    private float landOffset = 0;
    private float landVelocity = 0;
    private final float springK;          // жёсткость
    private final float damping;          // демпф

    public CameraEffectsControl(Player player) {
        this.cam              = player.getCam();
        this.camNode          = player.getCamNode();
        this.moveCtrl         = player.getMovementControl();
        this.characterHeight  = player.getShape().getHeight() + 2 * player.getShape().getRadius();
        this.characterRadius  = player.getShape().getRadius();

        // базовые параметры, масштабируемые по росту
        this.bobBaseFreq   = 1.5f; // шагов в секунду для среднего роста
        this.bobAmplitude  = new Vector3f(
                characterRadius * 0.1f,          // sway ~ 10% радиуса
                characterHeight * 0.025f,        // bob ~ 2.5% роста
                characterHeight * 0.9f           // base height ~90% роста
        );
        this.bobTilt       = FastMath.DEG_TO_RAD * (characterHeight * 0.5f);
        // tilt ~ 0.5° на каждый метр роста

        // spring-landing: жёсткость и демпф зависят от массы (рост ~ масса)
        float massScale   = characterHeight * 0.8f;
        this.springK      = 40f * massScale;
        this.damping      = 6f * massScale;
    }

    public void notifyJumpStart() {
        landOffset   = 0;
        landVelocity = 0;
    }

    public void notifyLanding(float peakHeight) {
        // начальная скорость пропорциональна высоте прыжка и росту
        landVelocity = peakHeight * 3f * (characterHeight * 0.5f);
    }

    @Override
    protected void controlUpdate(float tpf) {
        Vector3f base   = new Vector3f(0, bobAmplitude.z, 0);
        Vector3f offset = new Vector3f();

        // 1) bobbing: частота пропорциональна скорости движения
        float speed = moveCtrl.getCurrentSpeed(); // добавить getter в MovementControl
        if (moveCtrl.isMoving() && speed > 0.1f) {
            float freq = bobBaseFreq * (speed / moveCtrl.getMaxSpeed());
            walkTime += tpf * freq;
            float phase = walkTime * FastMath.TWO_PI;
            float sin = FastMath.sin(phase);
            float cos = FastMath.cos(phase);
            offset.y += sin * bobAmplitude.y;
            offset.x += cos * bobAmplitude.x * 0.6f;  // чуть меньше sway
            camNode.setLocalRotation(new Quaternion().fromAngles(sin * bobTilt, 0, cos * bobTilt * 0.3f));
        } else {
            camNode.setLocalRotation(Quaternion.IDENTITY);
            walkTime = 0;
        }

        // 2) spring-landing physics
        if (FastMath.abs(landOffset) > 0.001f || FastMath.abs(landVelocity) > 0.001f) {
            float force = -springK * landOffset;
            landVelocity += force * tpf;
            landVelocity *= FastMath.clamp(1 - damping * tpf, 0, 1);
            landOffset += landVelocity * tpf;
            offset.y -= landOffset;
        }

        // 3) apply
        camNode.setLocalTranslation(base.add(offset));
        cam.setLocation(camNode.getWorldTranslation());
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {}
}