package org.foxesworld.cge.modules.player;

import com.google.gson.Gson;
import com.jme3.anim.AnimComposer;
import com.jme3.asset.AssetInfo;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.physics.PhysicsModule;

import org.foxesworld.cge.modules.player.control.camEffects.CameraEffectsConfig;
import org.foxesworld.cge.modules.player.control.CameraEffectsControl;
import org.foxesworld.cge.modules.player.config.AnimationMapping;
import org.foxesworld.cge.modules.player.config.PlayerConfig;
import org.foxesworld.cge.modules.player.control.MovementControl;
import org.foxesworld.cge.modules.player.control.PlayerCameraControl;
import org.foxesworld.cge.modules.player.hud.PlayerHud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Player with animation integration via AnimComposer and animation logging.
 */
public class Player extends Node {

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

    private static final int HOTBAR_SIZE = 9;
    private static final float INTERACTION_DISTANCE = 5f;

    private PlayerCameraControl camControl;
    private Spatial playerModel;
    private AnimComposer animComposer;
    //private AnimLayerControl animLayerControl;
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

        camControl = new PlayerCameraControl(this);
        addControl(camControl);

        movementControl = new MovementControl(this, playerModule.getConfig().getMovement());
        addControl(movementControl);

        CameraEffectsConfig effectsConfig = CameraEffectsConfig.load(
                engine.getAssetManager(),
                "assets/config/camera_effects.json" // Укажите ваш путь
        );
        camEffectsControl = new CameraEffectsControl(this, effectsConfig);
        addControl(camEffectsControl);

        // анимация прыжка/приземления с blend
        movementControl.setMovementListener(new MovementControl.MovementListener() {
            @Override
            public void onJumpStart() {
                camEffectsControl.notifyJumpStart();
                if (animationController != null) animationController.play("jump", 0.18f, null, false);
            }

            @Override
            public void onLanding(float peak) {
                camEffectsControl.notifyLanding(peak);
                if (animationController != null) animationController.play("landing", 0.18f, null, false);
            }

            @Override
            public void onMove(float speed) {
                String anim;
                if (speed == 0f) {
                    anim = "idle";
                } else if (speed <= 0.03f) {
                    anim = "walk";
                } else {
                    anim = "sprint";
                }
                if (animationController != null) {
                    animationController.play(anim, 0.18f, null, true);
                }
            }

            @Override
            public void onStep() {
                //getEngine().getModuleManager().getModule(SoundModule.class).playSound("assets/Sounds/FST_Conc_JumpDown_Player_1st_01.ogg", playerModel.getLocalTranslation(), true, 1.0f);
            }
        });


        loadPlayerModel(playerModule.getConfig().getModel().getModelPath());
        synchronize(true);
    }

    private AnimationMapping loadAnimationMapping() {
        String path = playerModule.getConfig().getAnimMappingPath();
        AnimationMapping animationMapping;

        // 1. Проверяем, задан ли путь
        if (path == null || path.isEmpty()) {
            logger.warn("Animation mapping path is not defined in player config. Using an empty mapping.");
            animationMapping = new AnimationMapping(); // Создаем пустую карту
            return null;
        }

        // 2. Используем AssetManager для загрузки
        try {
            // AssetManager.loadAsset() может загружать не только модели, но и текстовые файлы.
            // Для этого нужно правильно указать ключ.
            // Он вернет объект, из которого можно получить поток данных.
            AssetInfo assetInfo = engine.getAssetManager().locateAsset(new com.jme3.asset.AssetKey<>(path));

            if (assetInfo == null) {
                throw new com.jme3.asset.AssetNotFoundException("Asset not found via locateAsset: " + path);
            }

            // 3. Открываем поток и читаем JSON (try-with-resources закроет потоки автоматически)
            try (InputStream stream = assetInfo.openStream();
                 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                animationMapping = new Gson().fromJson(reader, AnimationMapping.class);
                if (animationMapping == null) {
                    animationMapping = new AnimationMapping();
                    logger.warn("Animation mapping file '{}' is empty or invalid. Using an empty mapping.", path);
                } else {
                    logger.info("Successfully loaded animation mapping from '{}'", path);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to load animation mapping from '{}'. Using an empty mapping.", path, e);
            animationMapping = new AnimationMapping();
        }
        return animationMapping;
    }

    private void loadPlayerModel(String modelPath) {
        try {
            playerModel = engine.getAssetManager().loadModel(modelPath);
            playerModel.setLocalScale(playerModule.getConfig().getModel().getScale());
            playerModel.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            playerModel.setCullHint(Spatial.CullHint.Never);
            playerModel.setLocalTranslation(0, playerModule.getConfig().getModel().getDownOffset(), playerModule.getConfig().getModel().getBackOffset());
            attachChild(playerModel);

            animComposer = fetchControl(playerModel, AnimComposer.class);
            if (animComposer != null) {
                logger.info("AnimComposer found. Animation list:");
                animComposer.getAnimClipsNames().forEach(name -> logger.info("  - {}", name));

                //animLayerControl = new AnimLayerControl();
                //playerModel.addControl(animLayerControl);

                animationController = new PlayerAnimationController(animComposer, loadAnimationMapping());
                animationController.play("idle", 0.15f, null, true);
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

        String animName = isGrounded() ? (movementControl.isSprinting() ? "sprint" : movementControl.isMoving() ? "move" : "idle") : "jump";
        if (animationController != null) animationController.play(animName, 0.13f, "", true);

        synchronize(true);
        updateModelPosition();
        updateGroundedState(tpf);
        playerHud.update(tpf);
        //animLayerControl.update(tpf);
        animationController.update(tpf
        );
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



    public CharacterControl getCharacter() {
        return character;
    }

    public Camera getCam() {
        return cam;
    }

    public Spatial getPlayerModel() {
        return playerModel;
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

    public AnimComposer getAnimComposer() {
        return animComposer;
    }

    //public AnimLayerControl getAnimLayerControl() { return animLayerControl; }
    public PlayerAnimationController getAnimationController() {
        return animationController;
    }
}