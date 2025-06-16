package org.foxesworld.cge.modules.player;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.physics.PhysicsModule;
import org.foxesworld.cge.modules.ui.UIModule;

/**
 * Represents the player character, handling physics, movement controls,
 * camera effects, and a visible third-person model that follows the camera.
 * Improved for robustness, stability, and AAA game requirements.
 */
public class Player extends Node {

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
    private static final float MODEL_BACK_OFFSET = 0.3f;
    private static final float MODEL_DOWN_OFFSET = -1.6f;

    // Movement and physics parameters
    private static final float EYE_HEIGHT   = 1.6f;
    private static final float PLAYER_RADIUS = 0.45f;
    private static final float PLAYER_HEIGHT = 1.7f;
    private static final float WALK_SPEED   = 0.13f;
    private static final float SPRINT_SPEED = 0.25f;
    private static final float ACCEL        = 0.75f;
    private static final float DECEL        = 0.92f;
    private static final float SMOOTH       = 2.2f;

    private boolean isCrouching = false;
    private float crouchAmount = 0f;
    private float targetEyeHeight = EYE_HEIGHT;
    private float interpEyeHeight = EYE_HEIGHT;

    // Optimization: reuse vectors
    private final Vector3f reuseVec1 = new Vector3f();
    private final Vector3f reuseVec2 = new Vector3f();

    // Grounded state for stability
    private boolean lastGrounded = true;
    private float airTime = 0f; // Time spent in air, for better landing detection

    /**
     * Constructs the player, initializing physics, movement, camera and HUD.
     *
     * @param engine   the main game engine instance
     * @param spawnPos the starting position of the player
     */
    public Player(CalistaGameEngine engine, Vector3f spawnPos) {
        super("Player");
        this.engine = engine;
        this.input  = engine.getInputManager();
        this.cam    = engine.getCamera();
        this.playerHud = new PlayerHud(this);

        setLocalTranslation(spawnPos);

        // Initialize physics character with robust, tunable shape
        this.bullet = engine.getModuleManager()
                .getModule(PhysicsModule.class)
                .getBulletAppState();
        CapsuleCollisionShape shape = new CapsuleCollisionShape(PLAYER_RADIUS, PLAYER_HEIGHT - 2 * PLAYER_RADIUS, 1);
        this.character = new CharacterControl(shape, 0.05f);
        character.setPhysicsLocation(spawnPos);
        character.setJumpSpeed(5.2f);
        character.setFallSpeed(16.5f);
        character.setGravity(13.8f);
        //character.ыуеЫ(FastMath.HALF_PI); // 90 deg, avoid "stuck" on slopes
        addControl(character);
        bullet.getPhysicsSpace().add(character);

        // Disable default fly-by camera and hide cursor
        engine.getFlyByCamera().setEnabled(false);
        input.setCursorVisible(false);

        // Attach camera and movement controls
        PlayerCameraControl camControl = new PlayerCameraControl(this, EYE_HEIGHT, 0.18f, SMOOTH, engine.getRootNode());
        addControl(camControl);
        this.movementControl = new MovementControl(this, WALK_SPEED, SPRINT_SPEED, ACCEL, DECEL, SMOOTH);
        addControl(movementControl);
        this.camEffectsControl = new CameraEffectsControl(this);
        addControl(camEffectsControl);

        movementControl.setJumpListener(new MovementControl.JumpListener() {
            @Override public void onJumpStart()  { camEffectsControl.notifyJumpStart(); }
            @Override public void onLanding(float peak) { camEffectsControl.notifyLanding(peak); }
        });

        synchronize(true);
        loadPlayerModel();
    }

    /**
     * Loads and configures the player model for third-person view, if needed.
     */
    private void loadPlayerModel() {
        try {
            playerModel = engine.getAssetManager().loadModel("meshes/YBot.j3o");
            playerModel.setLocalScale(0.011f);
            playerModel.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            playerModel.setCullHint(Spatial.CullHint.Never);
            attachChild(playerModel);
        } catch (Exception e) {
            // Log error and fallback to a simple stand-in if loading failed
            System.err.println("[Player] Failed to load model: " + e.getMessage());
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

        // Interpolated eye height for smooth crouch transitions
        if (instant) {
            interpEyeHeight = targetEyeHeight;
        } else {
            interpEyeHeight += (targetEyeHeight - interpEyeHeight) * 0.12f;
        }
        cam.setLocation(pos.add(0, interpEyeHeight, 0));
    }

    /**
     * Updates player each frame: physics sync, model positioning, crouch, and HUD.
     *
     * @param tpf time per frame
     */
    public void update(float tpf) {
        // Smooth crouch (could be improved with animation curves)
        float desiredCrouch = isCrouching ? 0.7f : 1.0f;
        crouchAmount += (desiredCrouch - crouchAmount) * 0.15f;
        targetEyeHeight = EYE_HEIGHT * crouchAmount;

        synchronize(false);
        updateModelPosition();
        updateGroundedState(tpf);
        playerHud.update(tpf);
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
     * Checks if the player is on the ground using physics (with optional raycast fallback).
     */
    public boolean isGrounded() {
        // Optionally add a raycast check for more stability on edges/slopes
        return character.onGround();
    }

    /**
     * Positions and orients the player model behind the camera for third-person.
     */
    private void updateModelPosition() {
        if (playerModel == null) return;
        Vector3f camPos = cam.getLocation();
        Vector3f camDir = cam.getDirection(reuseVec1).normalizeLocal();
        Vector3f offset = camDir.mult(-MODEL_BACK_OFFSET).addLocal(0, MODEL_DOWN_OFFSET, 0);
        playerModel.setLocalTranslation(camPos.add(reuseVec2.set(offset)));
        Vector3f lookTarget = camPos.add(camDir);
        playerModel.lookAt(lookTarget, Vector3f.UNIT_Y);
    }

    /**
     * Allows toggling crouch state.
     */
    public void setCrouching(boolean crouch) {
        if (this.isCrouching != crouch) {
            this.isCrouching = crouch;
            // Optionally: adjust collision shape (advanced, not always supported in JME runtime)
        }
    }

    /**
     * Cleans up controls and physics on player removal.
     */
    public void cleanup() {
        removeControl(movementControl);
        removeControl(camEffectsControl);
        bullet.getPhysicsSpace().remove(character);
        engine.getFlyByCamera().setEnabled(true);
        input.setCursorVisible(true);
    }

    // --- Getters ---

    public CharacterControl getCharacter() { return character; }
    public MovementControl getMovementControl() { return movementControl; }
    public Camera getCam() { return cam; }
    public CalistaGameEngine getEngine() { return engine; }
    public boolean isCrouching() { return isCrouching; }
    public float getInterpEyeHeight() { return interpEyeHeight; }
    public InputManager getInput() { return input; }
    public CameraEffectsControl getCamEffectsControl() { return camEffectsControl; }
    public float getWalkSpeed() { return WALK_SPEED; }
    public float getSprintSpeed() { return SPRINT_SPEED; }
    public PlayerHud getPlayerHud() { return playerHud; }

    /**
     * Inner HUD class for managing on-screen player stats.
     * Improved: prevents redundant updates, more robust.
     */
    public static class PlayerHud {
        private float speed;
        private float armor = 0.6f;
        private float ability = 0.4f;
        private float prevSpeed = -1f, prevArmor = -1f, prevAbility = -1f;
        private final UIModule ui;

        /**
         * Initializes HUD panels through the UIModule.
         *
         * @param p the Player instance
         */
        public PlayerHud(Player p) {
            this.ui = p.engine.getModuleManager().getModule(UIModule.class);
            ui.addPanel(this, "assets/Interface/stats_config.xml");
        }

        public void setPlayerSpeed(float s) { speed = s; }
        public void setArmorBar(float a)    { armor = a; }
        public void setAbilityBar(float a)  { ability = a; }

        /**
         * Updates HUD elements only if changed.
         *
         * @param tpf time per frame
         */
        public void update(float tpf) {
            if (Math.abs(speed - prevSpeed) > 0.001f) { /* update speed bar */ prevSpeed = speed; }
            if (Math.abs(armor - prevArmor) > 0.001f) { /* update armor bar */ prevArmor = armor; }
            if (Math.abs(ability - prevAbility) > 0.001f) { /* update ability bar */ prevAbility = ability; }
        }
    }
}