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
import org.foxesworld.cge.ui.UIPanel;

public class Player extends Node {

    private final CharacterControl character;
    private final  CalistaGameEngine calistaGameEngine;
    private final MovementControl movementControl;
    private final CameraEffectsControl camEffectsControl;
    private final Node camNode;
    private final  PlayerHud playerHud;
    private final InputManager input;
    private final Camera cam;
    private final CapsuleCollisionShape shape;

    public Player(CalistaGameEngine engine, Vector3f spawnPos) {
        super("Player");
        setLocalTranslation(spawnPos);
        this.calistaGameEngine = engine;
        input = engine.getInputManager();
        BulletAppState bullet = engine.getModuleManager().getModule(PhysicsModule.class).getBulletAppState();
        //stats = new StatsDisplay(engine.getAssetManager(), engine.getGuiNode(), this, "Interface/stats_config.xml");
        playerHud = new PlayerHud(this);
        cam = engine.getCamera();

        // --- Physics setup ---
        shape = new CapsuleCollisionShape(0.5f, 1.8f, 1);
        character = new CharacterControl(shape, 0.05f);
        character.setJumpSpeed(4f);
        character.setFallSpeed(9.8f);
        character.setGravity(9.8f);
        character.setPhysicsLocation(spawnPos);
        addControl(character);
        bullet.getPhysicsSpace().add(character);

        // --- Movement control ---

        // --- First-person camera setup ---
        FirstPersonCameraControl fpCamControl = new FirstPersonCameraControl(this, 1.6f);
        addControl(fpCamControl);
        this.camNode = fpCamControl.getCamNode();
        movementControl = new MovementControl(this);
        addControl(movementControl);

        camEffectsControl = new CameraEffectsControl(this);

        camNode.addControl(camEffectsControl);
        attachChild(camNode);

        // --- Hook jump start to camera effects ---
        movementControl.setJumpListener(new MovementControl.JumpListener() {
            @Override public void onJumpStart() {
                camEffectsControl.notifyJumpStart();
            }
            @Override public void onLanding(float peak) {
                camEffectsControl.notifyLanding(peak);
            }
        });
    }

    public static class PlayerHud {
        private float playerSpeed;

        PlayerHud(Player player){
            UIModule uiModule = player.calistaGameEngine.getModuleManager().getModule(UIModule.class);
            uiModule.addPanel(this, "Interface/stats_config.xml");
        }

        public void setPlayerSpeed(float playerSpeed) {
            this.playerSpeed = playerSpeed;
        }
    }

    public CharacterControl getCharacter() {
        return character;
    }

    public MovementControl getMovementControl() {
        return movementControl;
    }

    public Node getCamNode() {
        return camNode;
    }

    public InputManager getInput() {
        return input;
    }

    public Camera getCam() {
        return cam;
    }

    public CapsuleCollisionShape getShape() {
        return shape;
    }

    public CalistaGameEngine getCalistaGameEngine() {
        return calistaGameEngine;
    }

    public PlayerHud getPlayerHud() {
        return playerHud;
    }
}
