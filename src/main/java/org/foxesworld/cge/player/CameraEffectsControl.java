package org.foxesworld.cge.player;

import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.control.Control;

/**
 * CameraEffectsControl отвечает за визуальные эффекты камеры при движении персонажа:
 *  - «боб» при ходьбе/беге;
 *  - плавный подъём при старте прыжка;
 *  - сглаженный «удар» и затухание при приземлении.
 */
public class CameraEffectsControl extends AbstractControl {

    private final Camera cam;
    private final Player player;
    private final MovementControl moveCtrl;
    private final CharacterControl characterCtrl;
    private final float characterHeight;

    private float verticalOffset;
    private float bobbingPhase = 0f;
    private float bobbingAmplitude;
    private float bobbingFrequency;

    private boolean isJumping = false;
    private float jumpStartHeight = 0f;

    private float landingOffset = 0f;
    private float landingShakeDuration;
    private float landingShakeTimer = 0f;

    public CameraEffectsControl(Player player) {
        this.player = player;
        this.cam = player.getCam();
        this.moveCtrl = player.getMovementControl();
        this.characterCtrl = player.getCharacter();

        CollisionShape shape = characterCtrl.getCharacter().getCollisionShape();
        if (!(shape instanceof CapsuleCollisionShape)) {
            throw new IllegalStateException("CameraEffectsControl ожидает CapsuleCollisionShape");
        }
        CapsuleCollisionShape capsule = (CapsuleCollisionShape) shape;
        this.characterHeight = capsule.getHeight() + 2f * capsule.getRadius();

        initParameters();
    }

    private void initParameters() {
        verticalOffset = characterHeight / 2f;
        bobbingAmplitude = 0.03f;
        bobbingFrequency = 6.0f;
        landingShakeDuration = 0.3f;
        landingShakeTimer = 0f;
    }

    public void notifyJumpStart() {
        isJumping = true;
        jumpStartHeight = characterCtrl.getPhysicsLocation().y;
        landingOffset = 0f;
    }

    public void notifyLanding(float peakHeight) {
        isJumping = false;
        landingOffset = FastMath.clamp(peakHeight * 0.5f, 0f, 0.3f);
        landingShakeTimer = landingShakeDuration;
        bobbingPhase = 0f;
    }

    @Override
    protected void controlUpdate(float tpf) {
        Vector3f charPos = characterCtrl.getPhysicsLocation();
        float halfHeight = characterHeight / 2f;

        if (isJumping) {
            float currentY = charPos.y;
            float targetOffset = (currentY - jumpStartHeight) + halfHeight;
            verticalOffset = FastMath.interpolateLinear(tpf * 4f, verticalOffset, targetOffset);

        } else if (landingShakeTimer > 0f) {
            float shake = computeLandingShake();
            landingShakeTimer = Math.max(landingShakeTimer - tpf, 0f);
            float targetOffset = halfHeight - shake;
            verticalOffset = FastMath.interpolateLinear(tpf * 8f, verticalOffset, targetOffset);

        } else if (moveCtrl.isMoving() && characterCtrl.onGround()) {
            float walkSpeed = player.getWalkSpeed();
            float sprintSpeed = player.getSprintSpeed();
            float totalSpeed = walkSpeed + sprintSpeed;
            if (totalSpeed <= 0f) totalSpeed = 0.0001f; // предохранитель от деления на 0

            float speedFactor = moveCtrl.getCurrentSpeed() / totalSpeed;
            bobbingPhase = (bobbingPhase + FastMath.TWO_PI * bobbingFrequency * tpf * speedFactor) % FastMath.TWO_PI;

            float bobOffset = FastMath.sin(bobbingPhase) * bobbingAmplitude;
            float targetOffset = halfHeight + bobOffset;
            verticalOffset = FastMath.interpolateLinear(tpf * 5f, verticalOffset, targetOffset);

        } else {
            verticalOffset = FastMath.interpolateLinear(tpf * 5f, verticalOffset, halfHeight);
            bobbingPhase = 0f;
        }

        cam.setLocation(new Vector3f(charPos.x, charPos.y + verticalOffset, charPos.z));
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {
        // не используется
    }

    @Override
    public Control cloneForSpatial(Spatial spatial) {
        return this;
    }

    private float computeLandingShake() {
        float progress = (landingShakeDuration - landingShakeTimer) / landingShakeDuration;
        return landingOffset * (1f - progress);
    }
}
