package org.foxesworld.cge.player;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.control.AbstractControl;

/**
 * Реалистичные эффекты камеры: bobbing и spring-landing.
 * Работает напрямую с камерой, без использования CameraNode.
 */
public class CameraEffectsControl extends AbstractControl {

    private final Camera cam;
    private final MovementControl moveCtrl;
    private final float characterHeight;
    private final float characterRadius;

    // bobbing
    private float walkTime = 0;
    private final float bobBaseFreq;
    private final Vector3f bobAmplitude;
    private final float bobTilt;

    // spring-landing
    private float landOffset = 0;
    private float landVelocity = 0;
    private final float springK;
    private final float damping;

    // внутренние параметры
    private Vector3f camBasePos;
    private Quaternion camBaseRot;

    public CameraEffectsControl(Player player) {
        this.cam = player.getCam();
        this.moveCtrl = player.getMovementControl();
        this.characterHeight = player.getShape().getHeight() + 2 * player.getShape().getRadius();
        this.characterRadius = player.getShape().getRadius();

        this.bobBaseFreq = 1.5f;
        this.bobAmplitude = new Vector3f(
                characterRadius * 0.1f,
                characterHeight * 0.025f,
                characterHeight * 0.9f
        );
        this.bobTilt = FastMath.DEG_TO_RAD * (characterHeight * 0.5f);

        float massScale = characterHeight * 0.8f;
        this.springK = 40f * massScale;
        this.damping = 6f * massScale;

        this.camBasePos = new Vector3f();
        this.camBaseRot = new Quaternion();
    }

    public void notifyJumpStart() {
        landOffset = 0;
        landVelocity = 0;
    }

    public void notifyLanding(float peakHeight) {
        landVelocity = peakHeight * 3f * (characterHeight * 0.5f);
    }

    @Override
    protected void controlUpdate(float tpf) {
        // Сохраняем исходную позицию и поворот
        camBasePos.set(spatial.getWorldTranslation()).addLocal(0, bobAmplitude.z, 0);
        camBaseRot.set(cam.getRotation());

        Vector3f offset = new Vector3f();
        Quaternion rotation = new Quaternion();

        float speed = moveCtrl.getCurrentSpeed();
        if (moveCtrl.isMoving() && speed > 0.1f) {
            float freq = bobBaseFreq * (speed / moveCtrl.getMaxSpeed());
            walkTime += tpf * freq;
            float phase = walkTime * FastMath.TWO_PI;
            float sin = FastMath.sin(phase);
            float cos = FastMath.cos(phase);

            offset.y += sin * bobAmplitude.y;
            offset.x += cos * bobAmplitude.x * 0.6f;
            rotation.fromAngles(sin * bobTilt, 0, cos * bobTilt * 0.3f);
        } else {
            walkTime = 0;
            rotation.loadIdentity();
        }

        // spring-landing
        if (FastMath.abs(landOffset) > 0.001f || FastMath.abs(landVelocity) > 0.001f) {
            float force = -springK * landOffset;
            landVelocity += force * tpf;
            landVelocity *= FastMath.clamp(1 - damping * tpf, 0, 1);
            landOffset += landVelocity * tpf;
            offset.y -= landOffset;
        }

        // итоговая позиция и вращение
        Vector3f finalPos = camBasePos.add(offset);
        Quaternion finalRot = camBaseRot.mult(rotation);

        cam.setLocation(finalPos);
        cam.setRotation(finalRot);
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {}
}
