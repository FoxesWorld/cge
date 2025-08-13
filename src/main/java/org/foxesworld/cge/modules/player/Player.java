package org.foxesworld.cge.modules.player;

import com.jme3.anim.AnimComposer;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.physics.PhysicsModule;
import org.foxesworld.cge.modules.player.control.camEffects.CameraEffectsConfig;
import org.foxesworld.cge.modules.player.control.CameraEffectsControl;
import org.foxesworld.cge.modules.player.config.PlayerConfig;
import org.foxesworld.cge.modules.player.control.MovementControl;
import org.foxesworld.cge.modules.player.control.PlayerCameraControl;
import org.foxesworld.cge.modules.player.hud.PlayerHud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class Player extends Node {

    private static final Logger logger = LoggerFactory.getLogger(Player.class);

    private final PlayerModule playerModule;
    private final CalistaGameEngine engine;
    private final InputManager input;
    private final Camera cam;
    private final BulletAppState bullet;
    final CharacterControl character;
    private final MovementControl movementControl;
    private final CameraEffectsControl camEffectsControl;
    private final PlayerHud playerHud;

    private PlayerCameraControl camControl;
    Spatial playerModel;
    private AnimComposer animComposer;
    private PlayerAnimationController animationController;
    final Vector3f reuseVec1 = new Vector3f();
    final Vector3f reuseVec2 = new Vector3f();

    private boolean isCrouching = false;
    private float crouchAmount = 0f;
    float targetEyeHeight;
    float interpEyeHeight;

    private boolean lastGrounded = true;
    private float airTime = 0f;

    private final PhysicsHelper physicsHelper;
    private final ModelHelper modelHelper;

    public Player(PlayerModule playerModule, Vector3f spawnPos) {
        super("Player");
        this.playerModule = Objects.requireNonNull(playerModule);
        this.engine = playerModule.getGameEngine();
        this.input = engine.getInputManager();
        this.cam = engine.getCamera();
        this.bullet = engine.getModuleManager().getModule(PhysicsModule.class).getBulletAppState();
        this.playerHud = new PlayerHud(this);

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

        camControl = new PlayerCameraControl(this);
        addControl(camControl);

        movementControl = new MovementControl(this, playerModule.getConfig().getMovement());
        addControl(movementControl);

        CameraEffectsConfig effectsConfig = CameraEffectsConfig.load(
                engine.getAssetManager(),
                "assets/config/camera_effects.json"
        );
        camEffectsControl = new CameraEffectsControl(this, effectsConfig);
        addControl(camEffectsControl);

        movementControl.setMovementListener(new MovementControl.MovementListener() {
            @Override
            public void onJumpStart() {
                camEffectsControl.notifyJumpStart();
                if (animationController != null) animationController.forcePlay("jumping", 0.18f, null, false);
            }

            @Override
            public void onLanding(float peak) {
                camEffectsControl.notifyLanding(peak);
                String anim;
                if(peak <= 10) {
                    anim = "landing";
                } else {
                    anim = "hardLanding";
                }
                if (animationController != null) animationController.play(anim, 0.18f, null, false);
            }

            @Override
            public void onStay() {
                animationController.play("idle", 0.18f, null, true);
            }

            @Override
            public void onStep(boolean left, float speed) {
                String anim;
                if (speed <= 0.035f) {
                    anim = "walk";
                } else {
                    anim = "sprint";
                }

                if (animationController != null) {
                    animationController.play(anim, 0.18f, null, true);
                }
            }

            @Override
            public  void onAirborne(float airtime) {
                animationController.play("falling", 0.18f, null, true);
            }

            @Override
            public void onStopMoving() {
                animationController.play("stopMoving", 0.18f, null, true);
            }
        });
        this.physicsHelper = new PhysicsHelper(this);
        this.modelHelper = new ModelHelper(this);

        loadPlayerModel(playerModule.getConfig().getModel().getModelPath());
        physicsHelper.synchronize(true);
    }


    private void loadPlayerModel(String modelPath) {
        modelHelper.loadPlayerModel(modelPath);
        this.playerModel = modelHelper.getPlayerModel();
        this.animComposer = modelHelper.getAnimComposer();
        this.animationController = modelHelper.getAnimationController();
    }

    public void update(float tpf) {
        float configEyeHeight = playerModule.getConfig().getPhysics().getEyeHeight();
        crouchAmount += ((isCrouching ? 0.7f : 1.0f) - crouchAmount) * 0.15f;
        targetEyeHeight = configEyeHeight * crouchAmount;

        physicsHelper.synchronize(true);
        physicsHelper.updateModelPosition();
        updateGroundedState(tpf);
        playerHud.update(tpf);
        if (animationController != null) animationController.update(tpf);
    }

    private void updateGroundedState(float tpf) {
        boolean grounded = physicsHelper.isGrounded();
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

    public void cleanup() {
        removeControl(movementControl);
        removeControl(camEffectsControl);
        bullet.getPhysicsSpace().remove(character);
        engine.getFlyByCamera().setEnabled(true);
        input.setCursorVisible(true);
    }

    public CharacterControl getCharacter() {
        return character;
    }

    public Camera getCam() {
        return cam;
    }

    public InputManager getInput() {
        return input;
    }

    public CameraEffectsControl getCamEffectsControl() {
        return camEffectsControl;
    }

    public PlayerCameraControl getCamControl() {
        return camControl;
    }

    public PlayerConfig getPlayerConfig() {
        return playerModule.getConfig();
    }

    public BulletAppState getBullet() {
        return bullet;
    }

    public MovementControl getMovementControl() {
        return movementControl;
    }

    public CalistaGameEngine getEngine() {
        return engine;
    }

    public PlayerHud getPlayerHud() {
        return playerHud;
    }

    public PlayerModule getPlayerModule() {
        return playerModule;
    }

    public PhysicsHelper getPhysicsHelper() {
        return physicsHelper;
    }
}
