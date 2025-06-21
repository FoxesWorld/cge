package org.foxesworld.cge.modules.player;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.tween.Tween;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.InputManager;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.es.EntityId;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.physics.PhysicsModule;
import org.foxesworld.cge.modules.player.animation.AnimLayerControl;
import org.foxesworld.cge.modules.player.animation.event.AnimEventListener;
import org.foxesworld.cge.modules.player.animation.event.AnimationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the player character, handling physics, movement controls,
 * camera effects, and a visible third-person model that follows the camera.
 * Improved for robustness, stability, and AAA game requirements.
 * Гонки состояния исключены: все публичные методы потокобезопасны,
 * все добавления/удаления узлов - только из игрового потока!
 */
public class Player extends Node implements PlayerContext, AnimEventListener {

    private static final Logger logger = LoggerFactory.getLogger(Player.class);

    private EntityId player;
    private AnimComposer anim;
    private SkinningControl skin;
    private AnimLayerControl layerControl;
    private final PlayerModule playerModule;
    private final CalistaGameEngine engine;
    private final InputManager input;
    private final Camera cam;
    private final BulletAppState bullet;
    private final CharacterControl character;
    private final MovementControl movementControl;
    private final CameraEffectsControl camEffectsControl;
    private final PlayerHud playerHud;

    // Visible third-person model offsets
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

    // Grounded state for stability
    private boolean lastGrounded = true;
    private PlayerCameraControl camControl;
    private float airTime = 0f; // Time spent in air, for better landing detection

    /**
     * Constructs the player, initializing physics, movement, camera and HUD.
     *
     * @param playerModule   the main game engine instance
     * @param spawnPos the starting position of the player
     */
    public Player(PlayerModule playerModule, Vector3f spawnPos) {
        super("Player");
        this.playerModule = playerModule;
        //this.player = playerModule.getGameEngine().getEcsModule().getEntityData().createEntity();
        this.engine = playerModule.getGameEngine();
        this.input  = engine.getInputManager();
        this.cam    = engine.getCamera();
        this.playerHud = new PlayerHud(this);

        setLocalTranslation(spawnPos);

        // Initialize physics character with robust, tunable shape
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

        // Disable default fly-by camera and hide cursor
        engine.getFlyByCamera().setEnabled(false);
        input.setCursorVisible(false);

        // Attach camera and movement controls
        camControl = new PlayerCameraControl(this, EYE_HEIGHT, 0.18f, playerModule.getConfig().getMovement().getSmoothing(), engine.getRootNode());
        addControl(camControl);
        this.movementControl = new MovementControl(this, playerModule.getConfig().getMovement());
        addControl(movementControl);
        this.camEffectsControl = new CameraEffectsControl(this);
        addControl(camEffectsControl);

        movementControl.setJumpListener(new MovementControl.JumpListener() {
            @Override public void onJumpStart()  { camEffectsControl.notifyJumpStart(); }
            @Override public void onLanding(float peak) { camEffectsControl.notifyLanding(peak); }
        });
        loadPlayerModel(playerModule.getConfig().getModel().getModelPath());


        //initAnimations();
        synchronize(true);
    }

    private void initAnimations() {
        // Получаем SkinningControl (опционально, если нужна смена скина)
        skin = playerModel.getControl(SkinningControl.class);

        // Создаем или получаем слой анимаций
        layerControl = new AnimLayerControl();
        playerModel.addControl(layerControl);

        // Пример конфигурации базовых анимаций (ходьба, бег, прыжок)
        // Это зависит от названий анимаций в вашей модели!
        anim.addAction("idle", anim.action("idle"));
        anim.addAction("move", anim.action("move"));
        anim.addAction("Run", anim.action("Run"));
        anim.addAction("jump", anim.action("jump"));

        // Запуск анимации по умолчанию
        anim.setCurrentAction("idle");
    }

    /**
     * Loads and configures the player model for third-person view, if needed.
     * Only loads once. All spatial operations выполняются в игровом потоке!
     */
    private void loadPlayerModel(String model) {
        try {
            playerModel = engine.getAssetManager().loadModel(model);
            playerModel.setLocalScale(playerModule.getConfig().getModel().getScale());
            playerModel.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            playerModel.setCullHint(Spatial.CullHint.Never);
            playerModel.setLocalTranslation(0, playerModule.getConfig().getModel().getDownOffset(), 0);
            attachChild(playerModel);
        } catch (Exception e) {
            logger.warn("Failed to load model: {}", e.getMessage());
            playerModel = null;
        }
    }

    /**
     * Synchronizes the scene node and camera position with the physics character.
     * @param instant true = no interpolation (e.g. on spawn/teleport)
     */
    private void synchronize(boolean instant) {
        Vector3f pos = character.getPhysicsLocation();
        setLocalTranslation(pos);
        if (instant) {
            interpEyeHeight = targetEyeHeight;
        } else {
            interpEyeHeight += (targetEyeHeight - interpEyeHeight) * 0.12f;
        }
    }

    /**
     * Updates player each frame: physics sync, model positioning, crouch, and HUD.
     * Камера НЕ трогается — этим занимается CameraEffectsControl!
     */
    public void update(float tpf) {
        float desiredCrouch = isCrouching ? 0.7f : 1.0f;
        crouchAmount += (desiredCrouch - crouchAmount) * 0.15f;
        targetEyeHeight = EYE_HEIGHT * crouchAmount;

        synchronize(true);
        updateModelPosition();
        updateGroundedState(tpf);
        playerHud.update(tpf);
        playerHud.setTest(getMovementControl().getPlayerState().getType());
    }

    /**
     * Robust grounded state check. Also handles transition events (landing).
     */
    private void updateGroundedState(float tpf) {
        boolean grounded = isGrounded();
        if (!grounded) {
            airTime += tpf;
        } else {
            if (!lastGrounded && airTime > 0.1f) { // landed after at least 0.1s in air
                camEffectsControl.notifyLanding(airTime);
            }
            airTime = 0;
        }
        lastGrounded = grounded;
    }

    /**
     * Checks if the player is on the ground using physics and raycast fallback.
     */
    public boolean isGrounded() {
        // Основная проверка через character.onGround()
        if (character.onGround()) {
            return true;
        }
        // Дополнительная проверка через raycast для большей стабильности на краях и неровностях
        return checkGroundWithRaycast();
    }

    /**
     * Private helper method to check ground presence below player with raycast.
     * Uses a ray starting slightly above player position, casting downward.
     */
    private boolean checkGroundWithRaycast() {
        Vector3f origin = character.getPhysicsLocation().add(0, 0.1f, 0);
        Vector3f down = new Vector3f(0, -1, 0);
        Ray ray = new Ray(origin, down);
        ray.setLimit(1.5f);

        CollisionResults results = new CollisionResults();
        engine.getRootNode().collideWith(ray, results);
        for (CollisionResult result : results) {
            if (result.getDistance() < 1.5f) {
                return true;
            }
        }
        return false;
    }

    /**
     * Positions and orients the player model behind the camera for third-person.
     */
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

    /**
     * Allows toggling crouch state. Потокобезопасно.
     */
    public void setCrouching(boolean crouch) {
        isCrouching = crouch;
        // Optionally: adjust collision shape (advanced, not always supported in JME runtime)
    }

    /**
     * Cleans up controls and physics on player removal.
     * Вызов только из игрового потока!
     */
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
    @Override
    public float getWalkSpeed() { return WALK_SPEED; }
    @Override
    public float getSprintSpeed() { return SPRINT_SPEED; }
    @Override
    public PlayerHud getPlayerHud() { return playerHud; }

    @Override
    public PlayerCameraControl getCamControl() {
        return camControl;
    }

    @Override
    public void animationEvent(AnimationEvent event) {
        layerControl.enter("move", "sneaking");
    }

    @Override
    public Tween tween(AnimationEvent event) {
        return AnimEventListener.super.tween(event);
    }
}
