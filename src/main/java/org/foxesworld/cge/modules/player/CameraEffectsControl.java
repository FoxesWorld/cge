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
 * Поддерживает переключение между первым и третьим лицом.
 * Все эффекты масштабируются пропорционально размеру модели игрока (высоте капсулы).
 */
public class CameraEffectsControl extends AbstractControl {
    private final Camera cam;
    private final Player player;
    private final MovementControl moveCtrl;
    private final CharacterControl characterCtrl;
    private final float characterHeight;
    private final float characterRadius;

    // Эталонная высота "человека" для масштабирования эффектов (метры)
    private static final float BASE_HEIGHT = 1.6f;

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

    private boolean thirdPerson = false;
    private float thirdPersonDistance = 5.5f;
    private float thirdPersonHeightOffset = 1.8f;
    private Vector3f thirdPersonCamOffset = new Vector3f(0, 0, 0);

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
        this.characterRadius = capsule.getRadius();

        verticalOffsetSmoothed = characterHeight / 2f;
        targetYOffset = verticalOffsetSmoothed;
    }

    public void setThirdPerson(boolean thirdPerson) {
        this.thirdPerson = thirdPerson;
    }

    public void notifyJumpStart() {
        isJumping = true;
        jumpStartHeight = characterCtrl.getPhysicsLocation().y;
        landingOffset = 0f;
    }

    public void notifyLanding(float airTime) {
        isJumping = false;
        // Масштабируем силу приземления под размер персонажа
        float scale = characterHeight / BASE_HEIGHT;
        landingOffset = FastMath.clamp(FastMath.log(airTime + 1f), 0.07f * scale, 0.32f * scale);
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
        boolean running = moving && moveCtrl.getCurrentSpeed() > (player.getPlayerConfig().getMovement().getWalkSpeed() + 0.01f);

        // Масштабируем эффекты по высоте модели
        float scale = characterHeight / BASE_HEIGHT;

        if (!thirdPerson) {
            // FIRST PERSON CAMERA EFFECTS
            float bobAmplitude = (running ? 0.07f : 0.034f) * scale;
            float bobFrequency = (running ? 8.1f : 5.1f) / FastMath.sqrt(scale); // выше персонаж = ниже частота
            float lateralAmplitude = (running ? 0.022f : 0.011f) * scale;
            float lateralFrequency = (running ? 4.3f : 2.1f) / FastMath.sqrt(scale);
            float rollAmplitude = (running ? 0.16f : 0.09f) * scale;
            float rollSyncShift = FastMath.HALF_PI;

            if (isJumping) {
                float jumpNow = (charPos.y - jumpStartHeight) + halfHeight;
                targetYOffset = FastMath.interpolateLinear(0.18f, verticalOffsetSmoothed, jumpNow + 0.04f * scale * FastMath.sin(bobPhase));
            } else if (landingShakeTimer > 0f) {
                float shake = computeLandingShake(scale);
                landingShakeTimer = Math.max(landingShakeTimer - tpf, 0f);
                targetYOffset = halfHeight - shake;
                targetXOffset = shake * 0.14f * scale * FastMath.sin(landingShakeTimer * 26f / FastMath.sqrt(scale));
            } else if (moving) {
                float speed = FastMath.clamp(moveCtrl.getCurrentSpeed() / (player.getPlayerConfig().getMovement().getWalkSpeed() + player.getPlayerConfig().getMovement().getSprintSpeed()), 0.5f, 1.0f);

                bobPhase += FastMath.TWO_PI * bobFrequency * tpf * speed;
                bobPhase %= FastMath.TWO_PI;

                stepPhase += FastMath.TWO_PI * lateralFrequency * tpf * speed;
                stepPhase %= FastMath.TWO_PI;

                float vertBob = FastMath.sin(bobPhase) * bobAmplitude;
                float latBob = FastMath.sin(stepPhase + FastMath.HALF_PI) * lateralAmplitude;

                float totalBob = vertBob - Math.abs(latBob) * 0.13f * scale;
                targetYOffset = halfHeight + totalBob;
                targetXOffset = latBob;
                targetRoll = FastMath.sin(stepPhase + rollSyncShift) * rollAmplitude;
            } else {
                idleBreathPhase = (idleBreathPhase + FastMath.TWO_PI * 1.33f * tpf / FastMath.sqrt(scale)) % FastMath.TWO_PI;
                float idleBob = FastMath.sin(idleBreathPhase) * 0.009f * scale;
                targetYOffset = halfHeight + idleBob;
            }

            verticalOffsetSmoothed = FastMath.interpolateLinear(FastMath.clamp(tpf * 2.6f, 0f, 1f), verticalOffsetSmoothed, targetYOffset);
            smoothedRoll = FastMath.interpolateLinear(FastMath.clamp(tpf * 1.2f, 0f, 1f), smoothedRoll, targetRoll);

            cam.setLocation(charPos.add(targetXOffset, verticalOffsetSmoothed, 0));

            Quaternion baseRot = cam.getRotation().clone();
            Quaternion rollQ = new Quaternion().fromAngles(0f, 0f, smoothedRoll);
            baseRot.multLocal(rollQ);
            cam.setRotation(baseRot);
        } else {
            // THIRD PERSON CAMERA FOLLOW
            Vector3f lookDir = cam.getDirection().normalize();
            float tpsDistance = thirdPersonDistance * scale;
            float tpsHeightOffset = thirdPersonHeightOffset * scale;
            Vector3f targetCamPos = charPos.subtract(lookDir.mult(tpsDistance));
            targetCamPos.y += tpsHeightOffset;

            // Сглаживание (чтобы камера не дёргалась резко)
            thirdPersonCamOffset.interpolateLocal(targetCamPos, FastMath.clamp(tpf * 4f, 0f, 1f));
            Vector3f camPos = player.getCamControl().getDesiredCameraPosition();
            cam.setLocation(camPos);
            //cam.setLocation(thirdPersonCamOffset);

            // Камера всегда "смотрит" на голову персонажа
            Vector3f lookTarget = charPos.add(0, halfHeight, 0);
            cam.lookAt(lookTarget, Vector3f.UNIT_Y);
        }
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {}

    @Override
    public Control cloneForSpatial(Spatial spatial) {
        return this;
    }

    private float computeLandingShake(float scale) {
        float progress = (landingShakeDuration - landingShakeTimer) / landingShakeDuration;
        float spring = FastMath.exp(-progress * 6f) * FastMath.sin(progress * FastMath.PI * 2.2f);
        return landingOffset * (1f - progress) * (0.62f + 0.38f * spring) * scale;
    }
}