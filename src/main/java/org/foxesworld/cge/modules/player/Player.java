package org.foxesworld.cge.modules.player;

import com.jme3.anim.AnimComposer;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.physics.PhysicsModule;
import org.foxesworld.cge.modules.player.animation.AnimLayerControl;
import org.foxesworld.cge.modules.player.config.PlayerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Player with animation integration via AnimComposer and animation logging.
 */
public class Player extends Node implements PlayerContext {

    private static final Logger logger = LoggerFactory.getLogger(Player.class);
    private final PlayerModule playerModule;
    private final CalistaGameEngine engine;
    private final InputManager input;
    private final Camera cam;
    private final BulletAppState bullet;
    private final CharacterControl character;
    private final MovementControl movementControl;
    private final CameraEffectsControl camEffectsControl;
    private final PlayerHud playerHud;

    private PlayerCameraControl camControl;
    private Spatial playerModel;
    private AnimComposer animComposer;
    private AnimLayerControl animLayerControl;
    private PlayerAnimationController animationController;
    private final Vector3f reuseVec1 = new Vector3f();
    private final Vector3f reuseVec2 = new Vector3f();

    private boolean isCrouching = false;
    private float crouchAmount = 0f;
    private float targetEyeHeight;
    private float interpEyeHeight;

    private boolean lastGrounded = true;
    private float airTime = 0f;

    public Player(PlayerModule playerModule, Vector3f spawnPos) {
        super("Player");
        this.playerModule = Objects.requireNonNull(playerModule);
        this.engine = playerModule.getGameEngine();
        this.input = engine.getInputManager();
        this.cam = engine.getCamera();
        this.bullet = engine.getModuleManager().getModule(PhysicsModule.class).getBulletAppState();
        this.playerHud = new PlayerHud(this);

        // Use from config
        float configEyeHeight = playerModule.getConfig().getPhysics().getEyeHeight();
        this.targetEyeHeight = configEyeHeight;
        this.interpEyeHeight = configEyeHeight;

        setLocalTranslation(spawnPos);
        CapsuleCollisionShape shape = new CapsuleCollisionShape(
                playerModule.getConfig().getPhysics().getRadius(),
                playerModule.getConfig().getPhysics().getHeight() - 2 * playerModule.getConfig().getPhysics().getRadius(),
                1);
        this.character = new CharacterControl(shape, playerModule.getConfig().getPhysics().getStepHeight());
        character.setPhysicsLocation(spawnPos);
        character.setJumpSpeed(playerModule.getConfig().getPhysics().getJumpSpeed());
        character.setFallSpeed(playerModule.getConfig().getPhysics().getFallSpeed());
        character.setGravity(playerModule.getConfig().getPhysics().getGravity());

        addControl(character);
        bullet.getPhysicsSpace().add(character);

        engine.getFlyByCamera().setEnabled(false);
        input.setCursorVisible(false);

        camControl = new PlayerCameraControl(this, configEyeHeight, 0.18f,
                playerModule.getConfig().getMovement().getSmoothing(),
                engine.getRootNode());
        addControl(camControl);

        movementControl = new MovementControl(this, playerModule.getConfig().getMovement());
        addControl(movementControl);

        camEffectsControl = new CameraEffectsControl(this);
        addControl(camEffectsControl);

        // анимация прыжка/приземления с blend
        movementControl.setMovementListener(new MovementControl.MovementListener() {
            @Override
            public void onJumpStart() {
                camEffectsControl.notifyJumpStart();
                if (animationController != null) animationController.setAnimation("jump", 0.18f, null, false);
            }

            @Override
            public void onLanding(float peak) {
                camEffectsControl.notifyLanding(peak);
                if (animationController != null) animationController.setAnimation("landing", 0.18f, null, false);
            }

            @Override
            public void move(float speed) {
                String anim;
                if (speed == 0f) {
                    anim = "idle";
                } else if (speed <= 0.03f) {
                    anim = "walk";
                } else {
                    anim = "sprint";
                }
                if (animationController != null) {
                    animationController.setAnimation(anim, 0.18f, null, true);
                }
            }
        });


        loadPlayerModel(playerModule.getConfig().getModel().getModelPath());
        synchronize(true);
    }

    private void loadPlayerModel(String modelPath) {
        try {
            playerModel = engine.getAssetManager().loadModel(modelPath);
            playerModel.setLocalScale(playerModule.getConfig().getModel().getScale());
            playerModel.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            playerModel.setCullHint(Spatial.CullHint.Never);
            playerModel.setLocalTranslation(0, playerModule.getConfig().getModel().getDownOffset(), 0);
            attachChild(playerModel);

            animComposer = fetchControl(playerModel, AnimComposer.class);
            if (animComposer != null) {
                logger.info("AnimComposer found. Animation list:");
                animComposer.getAnimClipsNames().forEach(name -> logger.info("  - {}", name));

                animLayerControl = new AnimLayerControl();
                playerModel.addControl(animLayerControl);

                animationController = new PlayerAnimationController(animComposer);
                animationController.setAnimation("idle", 0.15f, null, true);
            } else {
                logger.warn("AnimComposer not found on player model!");
            }

        } catch (Exception e) {
            logger.warn("Failed to load player model '{}': {}", modelPath, e.getMessage());
        }
    }

    private <T extends Control> T fetchControl(Spatial spatial, Class<T> type) {
        if (spatial == null) return null;
        T control = spatial.getControl(type);
        if (control != null) return control;
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                T childControl = fetchControl(child, type);
                if (childControl != null) return childControl;
            }
        }
        return null;
    }

    public void update(float tpf) {
        float configEyeHeight = playerModule.getConfig().getPhysics().getEyeHeight();
        crouchAmount += ((isCrouching ? 0.7f : 1.0f) - crouchAmount) * 0.15f;
        targetEyeHeight = configEyeHeight * crouchAmount;

        String animName = isGrounded() ? (movementControl.isSprinting() ? "sprint" : movementControl.isWalking() ? "move" : "idle") : "jump";
        if (animationController != null) animationController.setAnimation(animName, 0.13f);

        synchronize(true);
        updateModelPosition();
        updateGroundedState(tpf);
        playerHud.update(tpf);
    }

    private void synchronize(boolean instant) {
        setLocalTranslation(character.getPhysicsLocation());
        if (instant) interpEyeHeight = targetEyeHeight;
        else interpEyeHeight += (targetEyeHeight - interpEyeHeight) * 0.12f;
    }

    private void updateModelPosition() {
        if (playerModel == null) return;
        Vector3f modelPos = reuseVec1.set(character.getPhysicsLocation()).addLocal(0, -playerModule.getConfig().getPhysics().getHeight() / 2f, 0);
        playerModel.setLocalTranslation(modelPos);

        Vector3f lookTarget = modelPos.add(cam.getDirection(reuseVec2).normalizeLocal());
        playerModel.lookAt(lookTarget, Vector3f.UNIT_Y);
    }

    private void updateGroundedState(float tpf) {
        boolean grounded = isGrounded();
        if (!grounded) {
            airTime += tpf;
        } else {
            if (!lastGrounded && airTime > 0.1f) {
                camEffectsControl.notifyLanding(airTime);
            }
            airTime = 0;
        }
        lastGrounded = grounded;
    }

    public boolean isGrounded() {
        return character.onGround() || checkGroundWithRaycast();
    }

    /**
     * Проверка наличия земли под игроком с помощью физического raycast.
     * Возвращает true, если под игроком обнаружен коллайдер (не сам игрок) на разумной дистанции.
     */
    private boolean checkGroundWithRaycast() {
        Vector3f origin = character.getPhysicsLocation().add(0, 0.1f, 0);
        Vector3f direction = Vector3f.UNIT_Y.negate();
        float rayLength = 1.5f;

        PhysicsSpace physicsSpace = bullet.getPhysicsSpace();
        if (physicsSpace == null) return false;

        Vector3f end = origin.add(direction.mult(rayLength));
        List<PhysicsRayTestResult> results = physicsSpace.rayTest(origin, end);

        float minFraction = Float.MAX_VALUE;
        PhysicsRayTestResult closest = null;

        for (PhysicsRayTestResult result : results) {
            Object userObject = result.getCollisionObject().getUserObject();
            if (userObject == character || userObject == this) continue;
            if (result.getHitFraction() < minFraction) {
                minFraction = result.getHitFraction();
                closest = result;
            }
        }
        if (closest != null) {
            float hitDistance = rayLength * minFraction;
            return hitDistance < 0.25f;
        }
        return false;
    }

    public void setCrouching(boolean crouch) {
        this.isCrouching = crouch;
    }

    public void cleanup() {
        removeControl(movementControl);
        removeControl(camEffectsControl);
        bullet.getPhysicsSpace().remove(character);
        engine.getFlyByCamera().setEnabled(true);
        input.setCursorVisible(true);
    }

    public void updateModelVisibility() {
        if (playerModel != null) {
            playerModel.setCullHint(camControl.isThirdPerson() ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        }
    }

    @Override public CharacterControl getCharacter() { return character; }
    @Override public Camera getCam() { return cam; }
    @Override public InputManager getInput() { return input; }
    @Override public CameraEffectsControl getCamEffectsControl() { return camEffectsControl; }
    @Override public PlayerCameraControl getCamControl() { return camControl; }

    public PlayerConfig getPlayerConfig() {
        return playerModule.getConfig();
    }

    public MovementControl getMovementControl() { return movementControl; }
    public CalistaGameEngine getEngine() { return engine; }
    public boolean isCrouching() { return isCrouching; }
    public float getInterpEyeHeight() { return interpEyeHeight; }
    public PlayerHud getPlayerHud() { return playerHud; }
    public AnimComposer getAnimComposer() { return animComposer; }
    public AnimLayerControl getAnimLayerControl() { return animLayerControl; }
    public PlayerAnimationController getAnimationController() { return animationController; }
}