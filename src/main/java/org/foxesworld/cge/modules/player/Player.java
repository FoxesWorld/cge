package org.foxesworld.cge.modules.player;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.SkinningControl;
import com.jme3.bullet.BulletAppState;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Player с интеграцией анимаций через AnimComposer/SkinningControl/AnimationControl и логированием анимаций.
 */
public class Player extends Node implements PlayerContext {

    private static final Logger logger = LoggerFactory.getLogger(Player.class);

    private enum AnimationSystemType { ANIM_COMPOSER, SKINNING_CONTROL, ANIMATION_CONTROL, NONE }
    private AnimationSystemType animationSystemType = AnimationSystemType.NONE;

    private AnimComposer animComposer;
    private SkinningControl skinningControl;
    private AnimComposer animationControl;
    private AnimLayerControl animLayerControl;

    private final PlayerModule playerModule;
    private final CalistaGameEngine engine;
    private final InputManager input;
    private final Camera cam;
    private final BulletAppState bullet;
    private final CharacterControl character;
    private final MovementControl movementControl;
    private final CameraEffectsControl camEffectsControl;
    private final PlayerHud playerHud;

    private Spatial playerModel;

    // Movement and physics parameters
    private static final float EYE_HEIGHT   = 1.6f;
    private static final float WALK_SPEED   = 0.13f;
    private static final float SPRINT_SPEED = 0.18f;

    private volatile boolean isCrouching = false;
    private float crouchAmount = 0f;
    private float targetEyeHeight = EYE_HEIGHT;
    private float interpEyeHeight = EYE_HEIGHT;

    private final Vector3f reuseVec1 = new Vector3f();
    private final Vector3f reuseVec2 = new Vector3f();

    private boolean lastGrounded = true;
    private PlayerCameraControl camControl;
    private float airTime = 0f;

    /**
     * Конструктор игрока.
     */
    public Player(PlayerModule playerModule, Vector3f spawnPos) {
        super("Player");
        this.playerModule = playerModule;
        this.engine = playerModule.getGameEngine();
        this.input  = engine.getInputManager();
        this.cam    = engine.getCamera();
        this.playerHud = new PlayerHud(this);

        setLocalTranslation(spawnPos);

        // Physics
        this.bullet = engine.getModuleManager()
                .getModule(PhysicsModule.class)
                .getBulletAppState();
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

        // Camera / movement
        engine.getFlyByCamera().setEnabled(false);
        input.setCursorVisible(false);

        camControl = new PlayerCameraControl(this, EYE_HEIGHT, 0.18f, playerModule.getConfig().getMovement().getSmoothing(), engine.getRootNode());
        addControl(camControl);
        this.movementControl = new MovementControl(this, playerModule.getConfig().getMovement());
        addControl(movementControl);
        this.camEffectsControl = new CameraEffectsControl(this);
        addControl(camEffectsControl);

        movementControl.setJumpListener(new MovementControl.JumpListener() {
            @Override
            public void onJumpStart() {
                camEffectsControl.notifyJumpStart();
                setAnimation("jump");
            }
            @Override
            public void onLanding(float peak) {
                camEffectsControl.notifyLanding(peak);
                setAnimation("idle");
            }
        });

        loadPlayerModel(playerModule.getConfig().getModel().getModelPath());
        synchronize(true);
    }

    /**
     * Загрузка и интеграция модели игрока и анимаций.
     * Динамически определяет тип системы анимации: AnimComposer, SkinningControl, AnimationControl.
     */
    private void loadPlayerModel(String model) {
        try {
            playerModel = engine.getAssetManager().loadModel(model);
            playerModel.setLocalScale(playerModule.getConfig().getModel().getScale());
            playerModel.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            playerModel.setCullHint(Spatial.CullHint.Never);
            playerModel.setLocalTranslation(0, playerModule.getConfig().getModel().getDownOffset(), 0);
            attachChild(playerModel);

            animComposer = fetchControl(playerModel, AnimComposer.class);
            if (animComposer != null) {
                animationSystemType = AnimationSystemType.ANIM_COMPOSER;
                logger.info("AnimComposer найден. Список анимаций:");
                for (String n : animComposer.getAnimClipsNames()) {
                    logger.info("  - {}", n);
                }
                animLayerControl = new AnimLayerControl();
                playerModel.addControl(animLayerControl);
            } else {
                animationSystemType = AnimationSystemType.NONE;
                logger.warn("Анимационный контроллер AnimComposer не найден на модели игрока!");
            }
            setAnimation("idle");
        } catch (Exception e) {
            logger.warn("Failed to load model: {}", e.getMessage());
            playerModel = null;
        }
    }
    /** Рекурсивный поиск контрола в иерархии */
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

    /**
     * Хелпер для переключения анимации с логированием.
     */
    private String lastAnim = "";

    private void setAnimation(String animName) {
        if (animName == null) return;

        switch (animationSystemType) {
            case ANIM_COMPOSER:
            case SKINNING_CONTROL:
                if (animComposer != null && animComposer.getAnimClip(animName) != null) {
                    if (!lastAnim.equals(animName)) {
                        logger.info("Switching animation: {} -> {}", lastAnim, animName);
                        animComposer.setCurrentAction(animName);
                        lastAnim = animName;
                    }
                } else {
                    if (animComposer != null && !lastAnim.equals(animName)) {
                        logger.warn("Анимация '{}' не найдена!", animName);
                    }
                }
                break;

            case ANIMATION_CONTROL:
                logger.info("GG");
                break;

            default:
                if (!lastAnim.equals(animName)) {
                    logger.warn("Нет подходящей системы анимации для проигрывания '{}'", animName);
                }
                break;
        }
    }

    private void synchronize(boolean instant) {
        Vector3f pos = character.getPhysicsLocation();
        setLocalTranslation(pos);
        if (instant) {
            interpEyeHeight = targetEyeHeight;
        } else {
            interpEyeHeight += (targetEyeHeight - interpEyeHeight) * 0.12f;
        }
    }

    public void update(float tpf) {
        float desiredCrouch = isCrouching ? 0.7f : 1.0f;
        crouchAmount += (desiredCrouch - crouchAmount) * 0.15f;
        targetEyeHeight = EYE_HEIGHT * crouchAmount;

        // --- Селектор анимации ---
        String animName = "idle";
        if (!isGrounded()) {
            animName = "jump";
        } else if (getMovementControl().isSprinting()) {
            animName = "Run";
        } else if (getMovementControl().isWalking()) {
            animName = "move";
        }
        setAnimation(animName);

        synchronize(true);
        updateModelPosition();
        updateGroundedState(tpf);
        playerHud.update(tpf);
    }

    private void updateModelPosition() {
        if (playerModel == null) return;
        Vector3f physicsLoc = character.getPhysicsLocation();
        float heightOffset = playerModule.getConfig().getPhysics().getHeight() / 2.0f;
        Vector3f modelPos = reuseVec1.set(physicsLoc).addLocal(0, -heightOffset, 0);
        playerModel.setLocalTranslation(modelPos);

        Vector3f camDir = cam.getDirection(reuseVec2).normalizeLocal();
        Vector3f lookTarget = modelPos.add(camDir);
        playerModel.lookAt(lookTarget, Vector3f.UNIT_Y);
    }

    private void updateGroundedState(float tpf) {
        boolean grounded = isGrounded();
        if (!grounded) {
            airTime += tpf;
        } else {
            if (!lastGrounded && airTime > 0.1f) {
                camEffectsControl.notifyLanding(airTime);
                setAnimation("idle");
            }
            airTime = 0;
        }
        lastGrounded = grounded;
    }

    public boolean isGrounded() {
        if (character.onGround()) {
            return true;
        }
        return checkGroundWithRaycast();
    }

    private boolean checkGroundWithRaycast() {
        Vector3f origin = character.getPhysicsLocation().add(0, 0.1f, 0);
        Vector3f down = new Vector3f(0, -1, 0);
        Ray ray = new Ray(origin, down);
        ray.setLimit(1.5f);

        // Реализуйте raycast по вашей сцене, если требуется
        return false;
    }

    public void setCrouching(boolean crouch) {
        isCrouching = crouch;
    }

    public void cleanup() {
        removeControl(movementControl);
        removeControl(camEffectsControl);
        bullet.getPhysicsSpace().remove(character);
        engine.getFlyByCamera().setEnabled(true);
        input.setCursorVisible(true);
    }

    public void updateModelVisibility() {
        if (playerModel == null) return;
        boolean isThirdPerson = camControl.isThirdPerson();
        playerModel.setCullHint(isThirdPerson ? Spatial.CullHint.Never : Spatial.CullHint.Always);
    }

    // --- Getters ---

    @Override
    public CharacterControl getCharacter() { return character; }

    @Override
    public float getWalkSpeed() {
        return WALK_SPEED;
    }

    @Override
    public float getSprintSpeed() {
        return SPRINT_SPEED;
    }

    public MovementControl getMovementControl() { return movementControl; }
    @Override
    public Camera getCam() { return cam; }
    public CalistaGameEngine getEngine() { return engine; }
    public boolean isCrouching() { return isCrouching; }
    public float getInterpEyeHeight() { return interpEyeHeight; }
    @Override
    public InputManager getInput() { return input; }
    @Override
    public CameraEffectsControl getCamEffectsControl() { return camEffectsControl; }
    public PlayerHud getPlayerHud() { return playerHud; }

    @Override
    public PlayerCameraControl getCamControl() {
        return camControl;
    }

    public AnimComposer getAnimComposer() {
        return animComposer;
    }

    public SkinningControl getSkinningControl() {
        return skinningControl;
    }
    public AnimLayerControl getAnimLayerControl() {
        return animLayerControl;
    }
}