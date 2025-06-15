package org.foxesworld.cge.player;

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
import org.foxesworld.cge.physics.PhysicsModule;
import org.foxesworld.cge.ui.UIModule;

/**
 * Represents the player character with physics, movement, camera effects,
 * and a visible third-person model that follows behind the camera.
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

    // Visible player model (third-person)
    private Spatial playerModel;
    private final float modelBackOffset = 0.3f;
    private final float modelDownOffset = -1.6f;

    private final float eyeHeight   = 1.6f;
    private final float walkSpeed   = 0.1f;
    private final float sprintSpeed = 0.25f;
    private final float accel       = 0.6f;
    private final float decel       = 0.8f;
    private final float smooth      = 2f;

    public Player(CalistaGameEngine engine, Vector3f spawnPos) {
        super("Player");
        this.engine = engine;
        this.input  = engine.getInputManager();
        this.cam    = engine.getCamera();
        this.playerHud = new PlayerHud(this);

        setLocalTranslation(spawnPos);

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

        engine.getFlyByCamera().setEnabled(false);
        input.setCursorVisible(false);

        PlayerCameraControl camControl = new PlayerCameraControl(this, eyeHeight, 0.2f, smooth);
        addControl(camControl);

        this.movementControl = new MovementControl(this, walkSpeed, sprintSpeed, accel, decel, smooth);
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

    private void loadPlayerModel() {
        playerModel = engine.getAssetManager().loadModel("meshes/YBot.j3o");
        playerModel.setShadowMode(RenderQueue.ShadowMode.Cast);
        playerModel.setCullHint(Spatial.CullHint.Never);
        playerModel.setLocalScale(.01f);
        attachChild(playerModel);
    }

    private void synchronize() {
        Vector3f pos = character.getPhysicsLocation();
        setLocalTranslation(pos);
        cam.setLocation(pos.add(0, eyeHeight, 0));
    }

    /**
     * Called each frame to update the player and model positions.
     */
    public void update(float tpf) {
        synchronize();
        updateModelPosition();
        playerHud.update(tpf);
    }

    private void updateModelPosition() {
        Vector3f camPos = cam.getLocation();
        Vector3f camDir = cam.getDirection().normalize();

        Vector3f offset = camDir.mult(-modelBackOffset).add(0, modelDownOffset, 0);
        playerModel.setLocalTranslation(camPos.add(offset));

        Vector3f lookTarget = camPos.add(camDir);
        playerModel.lookAt(lookTarget, Vector3f.UNIT_Y);
    }

    /**
     * Cleanup controls and physics when unloading.
     */
    public void cleanup() {
        removeControl(movementControl);
        removeControl(camEffectsControl);
        bullet.getPhysicsSpace().remove(character);
        engine.getFlyByCamera().setEnabled(true);
        input.setCursorVisible(true);
    }

    // --- Getters for controls and camera ---
    public CharacterControl getCharacter() { return character; }
    public MovementControl getMovementControl() { return movementControl; }
    public Camera getCam() { return cam; }
    public CalistaGameEngine getEngine() { return engine; }

    /**
     * HUD inner class for updating on-screen stats.
     */
    public static class PlayerHud {
        private float speed, armor = 0.6f, ability = 0.4f;
        private final UIModule ui;
        public PlayerHud(Player p) {
            this.ui = p.engine.getModuleManager().getModule(UIModule.class);
            ui.addPanel(this, "assets/Interface/stats_config.xml");
        }
        public void setPlayerSpeed(float s) { speed = s; }
        public void setArmorBar(float a)    { armor = a; }
        public void setAbilityBar(float a)  { ability = a; }
        public void update(float tpf) { /* update HUD elements */ }
    }

    public InputManager getInput() {
        return input;
    }

    public CameraEffectsControl getCamEffectsControl() {
        return camEffectsControl;
    }

    public float getWalkSpeed() {
        return walkSpeed;
    }

    public float getSprintSpeed() {
        return sprintSpeed;
    }

    public PlayerHud getPlayerHud() {
        return playerHud;
    }
}
