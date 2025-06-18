package org.foxesworld.cge.modules.player;

import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.control.Control;

/**
 * CameraEffectsControl: AAA-style head bob, jump/landing and roll effects.
 * - Вертикальное и боковое покачивание камеры при ходьбе/беге.
 * - Roll (наклон камеры) влево/вправо синхронизирован с шагами и плавно затухает.
 * - Эффекты прыжка и приземления.
 * - Idle breathing в покое.
 *
 * FIX: roll теперь аккуратно ДОБАВЛЯЕТСЯ к текущему повороту камеры (yaw/pitch) — мышиный look не сбрасывается!
 */
public class CameraEffectsControl extends AbstractControl {

    private final Camera cam;
    private final Player player;
    private final MovementControl moveCtrl;
    private final CharacterControl characterCtrl;
    private final float characterHeight;

    private float verticalOffsetSmoothed;
    private float targetYOffset;
    private float targetXOffset;
    private float targetRoll;
    private float smoothedRoll = 0f;
    private float bobPhase = 0f;
    private float stepPhase = 0f;
    private float idleBreathPhase = 0f;

    private boolean isJumping = false;
    private float jumpStartHeight = 0f;

    private float landingOffset = 0f;
    private float landingShakeDuration = 0.38f;
    private float landingShakeTimer = 0f;

    public CameraEffectsControl(Player player) {
        this.player = player;
        this.cam = player.getCam();
        this.moveCtrl = player.getMovementControl();
        this.characterCtrl = player.getCharacter();

        CollisionShape shape = characterCtrl.getCharacter().getCollisionShape();
        if (!(shape instanceof CapsuleCollisionShape capsule)) {
            throw new IllegalStateException("CameraEffectsControl expects CapsuleCollisionShape");
        }
        this.characterHeight = capsule.getHeight() + 2f * capsule.getRadius();

        verticalOffsetSmoothed = characterHeight / 2f;
        targetYOffset = verticalOffsetSmoothed;
    }

    public void notifyJumpStart() {
        isJumping = true;
        jumpStartHeight = characterCtrl.getPhysicsLocation().y;
        landingOffset = 0f;
    }

    public void notifyLanding(float airTime) {
        isJumping = false;
        landingOffset = FastMath.clamp(FastMath.log(airTime + 1f), 0.07f, 0.32f);
        landingShakeTimer = landingShakeDuration;
        bobPhase = 0f;
        stepPhase = 0f;
    }

    @Override
    protected void controlUpdate(float tpf) {
        Vector3f charPos = characterCtrl.getPhysicsLocation();
        float halfHeight = characterHeight / 2f;

        targetYOffset = halfHeight;
        targetXOffset = 0f;
        targetRoll = 0f;

        boolean moving = moveCtrl.isMoving() && characterCtrl.onGround();
        boolean running = moving && moveCtrl.getCurrentSpeed() > (player.getWalkSpeed() + 0.01f);

        // Настройки bob/roll
        float bobAmplitude = running ? 0.07f : 0.034f;
        float bobFrequency = running ? 8.1f : 5.1f;
        float lateralAmplitude = running ? 0.022f : 0.011f;
        float lateralFrequency = running ? 4.3f : 2.1f;
        float rollAmplitude = running ? 0.16f : 0.09f; // радианы! (около 9 и 5 градусов)
        float rollSyncShift = FastMath.HALF_PI; // roll по фазе сдвинут относительно шага

        if (isJumping) {
            float jumpNow = (charPos.y - jumpStartHeight) + halfHeight;
            targetYOffset = FastMath.interpolateLinear(0.18f, verticalOffsetSmoothed, jumpNow + 0.04f * FastMath.sin(bobPhase));
            targetRoll = 0f;
        } else if (landingShakeTimer > 0f) {
            float shake = computeLandingShake();
            landingShakeTimer = Math.max(landingShakeTimer - tpf, 0f);
            targetYOffset = halfHeight - shake;
            targetXOffset = shake * 0.14f * FastMath.sin(landingShakeTimer * 26f);
            targetRoll = 0f;
        } else if (moving) {
            float speed = FastMath.clamp(moveCtrl.getCurrentSpeed() / (player.getWalkSpeed() + player.getSprintSpeed()), 0.5f, 1.0f);

            bobPhase += FastMath.TWO_PI * bobFrequency * tpf * speed;
            if (bobPhase > FastMath.TWO_PI) bobPhase -= FastMath.TWO_PI;
            stepPhase += FastMath.TWO_PI * lateralFrequency * tpf * speed;
            if (stepPhase > FastMath.TWO_PI) stepPhase -= FastMath.TWO_PI;

            float vertBob = FastMath.sin(bobPhase) * bobAmplitude * (running ? 1.0f : 0.8f);
            float latBob = FastMath.sin(stepPhase + FastMath.HALF_PI) * lateralAmplitude * (running ? 1.0f : 0.8f);

            float totalBob = vertBob - Math.abs(latBob) * 0.13f;

            targetYOffset = halfHeight + totalBob;
            targetXOffset = latBob;

            // Roll синхронизирован с шагами
            targetRoll = FastMath.sin(stepPhase + rollSyncShift) * rollAmplitude * (running ? 1.0f : 0.8f);
        } else {
            // Idle breathing
            idleBreathPhase = (idleBreathPhase + FastMath.TWO_PI * 1.33f * tpf) % FastMath.TWO_PI;
            float idleBob = FastMath.sin(idleBreathPhase) * 0.009f;
            targetYOffset = halfHeight + idleBob;
            targetXOffset = 0f;
            targetRoll = 0f;
            bobPhase = 0f;
            stepPhase = 0f;
        }

        // Сглаживание для мягкости (low-pass фильтр)
        verticalOffsetSmoothed = FastMath.interpolateLinear(FastMath.clamp(tpf * 2.6f, 0f, 1f), verticalOffsetSmoothed, targetYOffset);
        smoothedRoll = FastMath.interpolateLinear(FastMath.clamp(tpf * 1.2f, 0f, 1f), smoothedRoll, targetRoll);

        // Устанавливаем положение камеры
        cam.setLocation(charPos.add(targetXOffset, verticalOffsetSmoothed, 0));

        // --- FIX: roll накладывается на текущий yaw/pitch от мыши ---
        Quaternion baseRot = cam.getRotation().clone();
        Quaternion rollQ = new Quaternion().fromAngles(0f, 0f, smoothedRoll);
        baseRot.multLocal(rollQ);
        cam.setRotation(baseRot);
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {}

    @Override
    public Control cloneForSpatial(Spatial spatial) {
        return this;
    }

    private float computeLandingShake() {
        float progress = (landingShakeDuration - landingShakeTimer) / landingShakeDuration;
        float spring = FastMath.exp(-progress * 6f) * FastMath.sin(progress * FastMath.PI * 2.2f);
        return landingOffset * (1f - progress) * (0.62f + 0.38f * spring);
    }
}