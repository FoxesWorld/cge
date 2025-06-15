package org.foxesworld.cge.modules.player;

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
 * CameraEffectsControl handles first-person camera motion effects such as:
 * - bobbing while walking or running;
 * - smooth rising during jump start;
 * - smooth landing bump effect.
 */
public class CameraEffectsControl extends AbstractControl {

    private final Camera cam;
    private final Player player;
    private final MovementControl moveCtrl;
    private final CharacterControl characterCtrl;
    private final float characterHeight;

    private float verticalOffsetSmoothed;
    private float bobbingPhase = 0f;
    private float bobbingAmplitude;
    private float bobbingFrequency;

    private boolean isJumping = false;
    private float jumpStartHeight = 0f;

    private float landingOffset = 0f;
    private float landingShakeDuration;
    private float landingShakeTimer = 0f;

    private final Vector3f tempCamPos = new Vector3f();

    public CameraEffectsControl(Player player) {
        this.player = player;
        this.cam = player.getCam();
        this.moveCtrl = player.getMovementControl();
        this.characterCtrl = player.getCharacter();

        CollisionShape shape = characterCtrl.getCharacter().getCollisionShape();
        if (!(shape instanceof CapsuleCollisionShape)) {
            throw new IllegalStateException("CameraEffectsControl expects CapsuleCollisionShape");
        }
        CapsuleCollisionShape capsule = (CapsuleCollisionShape) shape;
        this.characterHeight = capsule.getHeight() + 2f * capsule.getRadius();

        initParameters();
    }

    private void initParameters() {
        verticalOffsetSmoothed = characterHeight / 2f;
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

        float targetOffset;

        if (isJumping) {
            float jumpOffset = (charPos.y - jumpStartHeight) + halfHeight;
            targetOffset = jumpOffset;

        } else if (landingShakeTimer > 0f) {
            float shake = computeLandingShake();
            landingShakeTimer = Math.max(landingShakeTimer - tpf, 0f);
            targetOffset = halfHeight - shake;

        } else if (moveCtrl.isMoving() && characterCtrl.onGround()) {
            float speedFactor = FastMath.clamp(
                    moveCtrl.getCurrentSpeed() / (player.getWalkSpeed() + player.getSprintSpeed()),
                    0f, 1f
            );

            bobbingPhase = (bobbingPhase + FastMath.TWO_PI * bobbingFrequency * tpf * speedFactor) % FastMath.TWO_PI;
            float bobOffset = FastMath.sin(bobbingPhase) * bobbingAmplitude * speedFactor;
            targetOffset = halfHeight + bobOffset;

        } else {
            targetOffset = halfHeight;
            bobbingPhase = 0f;
        }

        // Smooth vertical movement (low-pass filter)
        verticalOffsetSmoothed = FastMath.interpolateLinear(tpf * 6f, verticalOffsetSmoothed, targetOffset);

        // Apply camera movement
        cam.getLocation().set(charPos).addLocal(0, verticalOffsetSmoothed, 0);
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {
        // Not used
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
