package org.foxesworld.cge.modules.player.control;

import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.control.AbstractControl;
import org.foxesworld.cge.modules.player.Player;
import org.foxesworld.cge.modules.player.control.camEffects.CameraEffectsConfig;
import org.foxesworld.cge.modules.player.control.camEffects.springs.ScalarDampedSpring;
import org.foxesworld.cge.modules.player.control.camEffects.springs.VectorDampedSpring;

import java.util.List;

import static com.jme3.math.Vector3f.UNIT_Y;

/**
 * Manages AAA-quality procedural camera effects for both first and third-person views.
 * This implementation uses specialized damped spring models for all camera motions,
 * ensuring fluid inertia and a highly configurable, realistic feel.
 */
public final class CameraEffectsControl extends AbstractControl {

    private final Player player;
    private final Camera cam;
    private final MovementControl moveCtrl;
    private final CharacterControl characterCtrl;
    private final CameraEffectsConfig config;

    private final float characterHeight;
    private final float baseFov;

    // --- Specialized Springs for All Camera Motions ---
    private final VectorDampedSpring positionalSpring;
    private final VectorDampedSpring rotationalLagSpring;
    private final ScalarDampedSpring rollSpring;
    private final ScalarDampedSpring fovSpring;

    // --- State & Phase Trackers ---
    private float stepPhase = 0f, idleBreathPhase = 0f, jitterPhase = 0f;
    private boolean isThirdPerson = false;
    private final Vector3f thirdPersonTargetPos = new Vector3f();
    private final Vector3f lastCamDir = new Vector3f();
    private final Vector3f reuseVec = new Vector3f();

    public CameraEffectsControl(Player player, CameraEffectsConfig config) {
        this.player = player;
        this.config = config;
        this.cam = player.getCam();
        this.baseFov = cam.getFov();
        this.moveCtrl = player.getMovementControl();
        this.characterCtrl = player.getCharacter();

        if (!(characterCtrl.getCharacter().getCollisionShape() instanceof CapsuleCollisionShape capsule)) {
            throw new IllegalStateException("Requires a CapsuleCollisionShape.");
        }
        this.characterHeight = capsule.getHeight() + 2f * capsule.getRadius();

        // Initialize all springs using the new concrete classes and config values
        CameraEffectsConfig.SpringSettings posSpringCfg = config.positionalSpring;
        this.positionalSpring = new VectorDampedSpring(new Vector3f(0, getRestingEyeHeight(), 0), posSpringCfg.stiffness, posSpringCfg.damping);

        CameraEffectsConfig.SpringSettings rotLagSpringCfg = config.rotationalLagSettings.springSettings;
        this.rotationalLagSpring = new VectorDampedSpring(Vector3f.ZERO, rotLagSpringCfg.stiffness, rotLagSpringCfg.damping);

        CameraEffectsConfig.SpringSettings rollSpringCfg = config.rotationalSpring;
        this.rollSpring = new ScalarDampedSpring(0.0f, rollSpringCfg.stiffness, rollSpringCfg.damping);

        CameraEffectsConfig.SpringSettings fovSpringCfg = config.fovSpring;
        this.fovSpring = new ScalarDampedSpring(0.0f, fovSpringCfg.stiffness, fovSpringCfg.damping);

        this.lastCamDir.set(cam.getDirection());
    }

    public void setThirdPerson(boolean thirdPerson) {
        this.isThirdPerson = thirdPerson;
        if (!thirdPerson) {
            fovSpring.reset(0.0f);
            cam.setFov(baseFov);
        }
    }

    public void notifyJumpStart() {
        positionalSpring.addImpulse(new Vector3f(0, 0.08f * characterHeight, 0));
    }

    public void notifyLanding(float airTime) {
        float impact = FastMath.clamp(airTime * config.landingSettings.impactFactor, config.landingSettings.minImpact, config.landingSettings.maxImpact);
        positionalSpring.addImpulse(new Vector3f(0, -impact * characterHeight * 0.5f, 0));
        fovSpring.addImpulse(impact * config.landingSettings.fovPunch);
        this.stepPhase = FastMath.PI;
    }

    @Override
    protected void controlUpdate(float tpf) {
        if (isThirdPerson) {
            applyThirdPersonFollow(tpf);
        } else {
            applyFirstPersonEffects(tpf);
        }
    }

    private void applyThirdPersonFollow(float tpf) {
        Vector3f characterPos = characterCtrl.getPhysicsLocation();
        Vector3f camDirInv = cam.getDirection(reuseVec).negateLocal();

        float scaledDistance = config.thirdPersonSettings.distance * (characterHeight / 1.8f);
        float scaledHeight = config.thirdPersonSettings.heightOffset * (characterHeight / 1.8f);

        thirdPersonTargetPos.set(characterPos)
                .addLocal(camDirInv.mult(scaledDistance))
                .addLocal(0, scaledHeight, 0);

        Vector3f castFrom = characterPos.add(0, getRestingEyeHeight(), 0);
        float hitDist = raycast(castFrom, thirdPersonTargetPos);
        if (hitDist > 0) {
            thirdPersonTargetPos.interpolateLocal(castFrom, 1f - hitDist / castFrom.distance(thirdPersonTargetPos) + 0.2f);
        }

        Vector3f currentCamPos = cam.getLocation();
        currentCamPos.interpolateLocal(thirdPersonTargetPos, FastMath.clamp(tpf * config.thirdPersonSettings.smoothSpeed, 0, 1));
        cam.setLocation(currentCamPos);
        cam.lookAt(castFrom, UNIT_Y);
    }

    private void applyFirstPersonEffects(float tpf) {
        // Step 1: Calculate all target values for our springs based on player state
        Vector3f targetPosition = new Vector3f(0, getRestingEyeHeight(), 0);
        float targetRoll = 0f;
        float targetFovOffset = 0f;

        boolean onGround = characterCtrl.onGround();
        boolean isMoving = moveCtrl.isMoving() && onGround;

        if (isMoving) {
            CameraEffectsConfig.MotionProfile profile = moveCtrl.isSprinting() ? config.sprintProfile : config.walkProfile;
            float targetSpeed = moveCtrl.isSprinting() ? player.getPlayerConfig().getMovement().getSprintSpeed() : player.getPlayerConfig().getMovement().getWalkSpeed();

            float stride = characterHeight * profile.strideFactor;
            float stepsPerSec = (stride > 0) ? targetSpeed / stride : 0;
            stepPhase = (stepPhase + FastMath.TWO_PI * stepsPerSec * tpf) % FastMath.TWO_PI;

            float vBob = curve(FastMath.sin(stepPhase * 2f), profile.curvePower) * profile.bobAmp;
            float lSway = FastMath.cos(stepPhase) * profile.swayAmp;
            targetPosition.y += vBob * characterHeight;
            targetPosition.x += lSway * characterHeight;
            targetRoll += -lSway * profile.rollAmp;

            jitterPhase += profile.jitterSpeed * tpf;
            float jitter = (FastMath.sin(jitterPhase) + FastMath.sin(jitterPhase * 0.7f)) * 0.5f;
            targetPosition.addLocal(jitter * profile.jitterIntensity, jitter * profile.jitterIntensity * 1.2f, 0);

            targetFovOffset = profile.fovAdd;
        } else if (onGround) {
            idleBreathPhase = (idleBreathPhase + FastMath.TWO_PI * config.idleSettings.breathFreq * tpf) % FastMath.TWO_PI;
            targetPosition.y += FastMath.sin(idleBreathPhase) * config.idleSettings.breathAmp * characterHeight;
        }

        // Step 2: Calculate rotational lag impulse
        Vector3f camDirDelta = lastCamDir.subtract(cam.getDirection(reuseVec));
        float lagH = camDirDelta.dot(cam.getLeft(reuseVec));
        float lagV = camDirDelta.dot(cam.getUp(reuseVec));
        rotationalLagSpring.addImpulse(new Vector3f(lagH, lagV, 0).multLocal(config.rotationalLagSettings.lagIntensity));
        lastCamDir.set(cam.getDirection());

        // Step 3: Update all spring simulations
        positionalSpring.update(tpf, targetPosition);
        rotationalLagSpring.update(tpf, Vector3f.ZERO);
        rollSpring.update(tpf, targetRoll);
        fovSpring.update(tpf, targetFovOffset);

        // Step 4: Apply all transforms in the correct coordinate spaces
        Vector3f finalCamPos = characterCtrl.getPhysicsLocation().add(positionalSpring.getPosition());

        Quaternion camRotation = cam.getRotation();
        Vector3f lagOffset = rotationalLagSpring.getPosition();
        finalCamPos.addLocal(camRotation.mult(Vector3f.UNIT_X, reuseVec).multLocal(lagOffset.x));
        finalCamPos.addLocal(camRotation.mult(UNIT_Y, new Vector3f()).multLocal(lagOffset.y));
        cam.setLocation(finalCamPos);

        // Step 5: Apply final rotation and Field of View
        Quaternion finalRot = cam.getRotation().clone();
        finalRot.multLocal(new Quaternion().fromAngles(0, 0, rollSpring.getPosition()));
        cam.setRotation(finalRot);
        cam.setFov(baseFov + fovSpring.getPosition());
    }

    private float raycast(Vector3f from, Vector3f to) {
        List<PhysicsRayTestResult> results = player.getBullet().getPhysicsSpace().rayTest(from, to);
        for (PhysicsRayTestResult result : results) {
            if (result.getCollisionObject() != characterCtrl.getCharacter()) {
                return result.getHitFraction();
            }
        }
        return -1f;
    }

    private float curve(float v, float p) { return (v >= 0) ? v : -(FastMath.pow(-v, p)); }
    private float getRestingEyeHeight() { return characterHeight * 0.92f; }
    @Override protected void controlRender(RenderManager rm, ViewPort vp) {}
}