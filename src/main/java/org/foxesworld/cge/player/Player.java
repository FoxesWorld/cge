package org.foxesworld.cge.player;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.physics.PhysicsModule;
import org.foxesworld.cge.ui.UIModule;

/**
 * Represents the player character with physics, movement, and camera effects.
 * Relies on CharacterControl for collision, MovementControl for input-based movement,
 * and CameraEffectsControl for camera bobbing, jump, and landing effects.
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

    private final float eyeHeight = 1.6f;
    private final  float walkSpeed = 0.1f, sprintSped = 0.25f, acceleration = 0.6f, deceleration = 0.8f
            ,maxStamina = 1.0f,staminaDrainRate = 0.3f, staminaRegenRate = 0.05f;

    /**
     * Creates a new Player instance.
     *
     * @param engine   reference to the game engine (provides InputManager, Camera, BulletAppState, etc.)
     * @param spawnPos initial spawn position of the character's capsule center
     */
    public Player(CalistaGameEngine engine, Vector3f spawnPos) {
        super("Player");

        setLocalTranslation(spawnPos);
        this.engine = engine;
        this.input = engine.getInputManager();
        this.cam = engine.getCamera();
        this.playerHud = new PlayerHud(this);

        this.bullet = engine.getModuleManager()
                .getModule(PhysicsModule.class)
                .getBulletAppState();

        CapsuleCollisionShape shape = new CapsuleCollisionShape(0.5f, 1.8f, 1);
        this.character = new CharacterControl(shape, 0.05f);
        character.setJumpSpeed(4f);
        character.setFallSpeed(9.8f);
        character.setGravity(9.8f);
        character.setPhysicsLocation(spawnPos);

        addControl(character);
        bullet.getPhysicsSpace().add(character);

        engine.getFlyByCamera().setEnabled(false);
        input.setCursorVisible(false);

        FirstPersonCameraControl fpCamControl = new FirstPersonCameraControl(
                this, eyeHeight, 0.2f, 0.2f
        );
        addControl(fpCamControl);

        this.movementControl = new MovementControl(this, walkSpeed, sprintSped, acceleration,deceleration);
        addControl(movementControl);

        this.camEffectsControl = new CameraEffectsControl(this);
        addControl(camEffectsControl);

        movementControl.setJumpListener(new MovementControl.JumpListener() {
            @Override
            public void onJumpStart() {
                camEffectsControl.notifyJumpStart();
            }
            @Override
            public void onLanding(float peak) {
                camEffectsControl.notifyLanding(peak);
            }
        });

        synchronizeCamera();
    }

    /**
     * Aligns the Node and camera position with the CharacterControl.
     */
    private void synchronizeCamera() {
        Vector3f charPos = character.getPhysicsLocation();
        setLocalTranslation(charPos);
        cam.setLocation(charPos.add(0, eyeHeight, 0));
    }

    /**
     * Updates the player each frame.
     *
     * @param tpf time per frame in seconds
     */
    public void update(float tpf) {
        synchronizeCamera();
        playerHud.update(tpf);
        // MovementControl and CameraEffectsControl are updated automatically as Controls.
    }

    /**
     * Cleans up resources when unloading the player.
     */
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

    public MovementControl getMovementControl() {
        return movementControl;
    }

    public InputManager getInput() {
        return input;
    }

    public Camera getCam() {
        return cam;
    }

    public CalistaGameEngine getCalistaGameEngine() {
        return engine;
    }

    /**
     * Displays HUD elements such as speed, armor, and abilities.
     */
    @SuppressWarnings("unused")
    public static class PlayerHud {
        private float playerSpeed;
        private float armorBar = 0.6f;
        private float abilityBar = 0.4f;
        private final UIModule ui;

        /**
         * @param player reference to the Player to obtain the UIModule
         */
        public PlayerHud(Player player) {
            this.ui = player.engine
                    .getModuleManager()
                    .getModule(UIModule.class);

            // Load main HUD panel
            ui.addPanel(this, "Interface/stats_config.xml");

        }

        public void setPlayerSpeed(float speed) {
            this.playerSpeed = speed * 10;
        }

        public void setArmorBar(float armor) {
            this.armorBar = armor;
        }

        public void setAbilityBar(float ability) {
            this.abilityBar = ability;
        }

        /**
         * Called each frame to update HUD values.
         *
         * @param tpf time per frame in seconds
         */
        public void update(float tpf) {
            // Update HUD if needed
        }
    }


    public PlayerHud getPlayerHud() {
        return playerHud;
    }

    public float getEyeHeight() {
        return eyeHeight;
    }

    public float getWalkSpeed() {
        return walkSpeed;
    }

    public float getSprintSped() {
        return sprintSped;
    }

    public float getAcceleration() {
        return acceleration;
    }

    public float getDeceleration() {
        return deceleration;
    }

    public float getMaxStamina() {
        return maxStamina;
    }

    public float getStaminaDrainRate() {
        return staminaDrainRate;
    }

    public float getStaminaRegenRate() {
        return staminaRegenRate;
    }
}