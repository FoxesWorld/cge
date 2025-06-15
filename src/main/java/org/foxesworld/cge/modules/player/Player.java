package org.foxesworld.cge.modules.player;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
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
    private static final float WALK_SPEED   = 0.1f;
    private static final float SPRINT_SPEED = 0.25f;
    private static final float ACCEL        = 0.6f;
    private static final float DECEL        = 0.8f;
    private static final float SMOOTH       = 2f;

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

        // Initialize physics character
        this.bullet = engine.getModuleManager()
                .getModule(PhysicsModule.class)
                .getBulletAppState();
        CapsuleCollisionShape shape = new CapsuleCollisionShape(0.5f, 1.0f);
        this.character = new CharacterControl(shape, 0.05f);
        character.setPhysicsLocation(spawnPos);
        character.setJumpSpeed(4f);
        character.setFallSpeed(9.8f);
        character.setGravity(9.8f);
        addControl(character);
        bullet.getPhysicsSpace().add(character);

        // Disable default fly-by camera and hide cursor
        engine.getFlyByCamera().setEnabled(false);
        input.setCursorVisible(false);

        // Attach camera and movement controls
        PlayerCameraControl camControl = new PlayerCameraControl(this, EYE_HEIGHT, 0.2f, SMOOTH, engine.getRootNode());
        addControl(camControl);
        this.movementControl = new MovementControl(this, WALK_SPEED, SPRINT_SPEED, ACCEL, DECEL, SMOOTH);
        addControl(movementControl);
        this.camEffectsControl = new CameraEffectsControl(this);
        addControl(camEffectsControl);

        movementControl.setJumpListener(new MovementControl.JumpListener() {
            @Override public void onJumpStart()  { camEffectsControl.notifyJumpStart(); }
            @Override public void onLanding(float peak) { camEffectsControl.notifyLanding(peak); }
        });

        synchronize();
        loadPlayerModel();
    }

    /**
     * Loads and configures the player model (YBot) for third-person view.
     */
    private void loadPlayerModel() {
        playerModel = engine.getAssetManager().loadModel("meshes/YBot.j3o");
        playerModel.setShadowMode(RenderQueue.ShadowMode.Cast);
        playerModel.setCullHint(Spatial.CullHint.Never);
        playerModel.setLocalScale(0.01f);
        attachChild(playerModel);
    }

    /**
     * Synchronizes the scene node and camera position with the physics character.
     */
    private void synchronize() {
        Vector3f pos = character.getPhysicsLocation();
        setLocalTranslation(pos);
        cam.setLocation(pos.add(0, EYE_HEIGHT, 0));
    }

    /**
     * Updates player each frame: physics sync, model positioning, and HUD.
     *
     * @param tpf time per frame
     */
    public void update(float tpf) {
        synchronize();
        updateModelPosition();
        playerHud.update(tpf);
    }

    /**
     * Positions and orients the player model behind the camera.
     */
    private void updateModelPosition() {
        Vector3f camPos = cam.getLocation();
        Vector3f camDir = cam.getDirection().normalize();
        Vector3f offset = camDir.mult(-MODEL_BACK_OFFSET).add(0, MODEL_DOWN_OFFSET, 0);
        playerModel.setLocalTranslation(camPos.add(offset));
        Vector3f lookTarget = camPos.add(camDir);
        playerModel.lookAt(lookTarget, Vector3f.UNIT_Y);
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

    /**
     * Inner HUD class for managing on-screen player stats.
     */
    public static class PlayerHud {
        private float speed;
        private float armor = 0.6f;
        private float ability = 0.4f;
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
         * Updates HUD elements each frame.
         *
         * @param tpf time per frame
         */
        public void update(float tpf) { /* update HUD elements */ }
    }

    public InputManager getInput() { return input; }
    public CameraEffectsControl getCamEffectsControl() { return camEffectsControl; }
    public float getWalkSpeed() { return WALK_SPEED; }
    public float getSprintSpeed() { return SPRINT_SPEED; }
    public PlayerHud getPlayerHud() { return playerHud; }
}
