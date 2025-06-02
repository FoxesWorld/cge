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

public class Player extends Node {

    private final CharacterControl character;
    private final  CalistaGameEngine calistaGameEngine;
    private final MovementControl movementControl;
    private final CameraEffectsControl camEffectsControl;
    //private final Node camNode;
    private final  PlayerHud playerHud;
    private final InputManager input;
    private final Camera cam;
    private final CapsuleCollisionShape shape;

    /**
     * Конструктор Player.
     *
     * @param engine   ссылка на движок (содержит InputManager, Camera, BulletAppState и т.д.)
     * @param spawnPos начальная позиция спавна
     */
    public Player(CalistaGameEngine engine, Vector3f spawnPos) {
        super("Player");
        setLocalTranslation(spawnPos);
        this.calistaGameEngine = engine;

        // Получаем InputManager и Camera из движка
        this.input = engine.getInputManager();
        this.cam   = engine.getCamera();

        // HUD
        this.playerHud = new PlayerHud(this);

        // Physics setup
        this.shape = new CapsuleCollisionShape(0.5f, 1.8f, 1);
        this.character = new CharacterControl(shape, 0.05f);
        character.setJumpSpeed(4f);
        character.setFallSpeed(9.8f);
        character.setGravity(9.8f);
        character.setPhysicsLocation(spawnPos);
        addControl(character);

        // Добавляем CharacterControl в физическую сцену
        var bullet = engine.getModuleManager()
                .getModule(PhysicsModule.class)
                .getBulletAppState();
        bullet.getPhysicsSpace().add(character);

        // --- Отключаем стандартный FlyCam и скрываем курсор ---
        engine.getFlyByCamera().setEnabled(false);
        input.setCursorVisible(false);
        // Если версия JME поддерживает, можно попытаться захватить курсор:
        // input.setCursorCaptured(true);

        // --- First-person camera setup (без CameraNode) ---
        FirstPersonCameraControl fpCamControl = new FirstPersonCameraControl(
                this,           // this == Player (имеет getInput() и getCam())
                1.6f,           // высота «глаз» над землёй в метрах
                0.2f,           // чувствительность мыши по горизонтали (yaw)
                0.2f            // чувствительность мыши по вертикали (pitch)
        );
        addControl(fpCamControl);

        // --- Movement control (Frostbite-like parameters) ---
        movementControl = new MovementControl(
                this,
                0.1f,   // walking speed ~5 m/s
                0.25f,  // sprint speed ~10 m/s
                0.6f,  // acceleration ~20 m/s²
                0.8f   // deceleration ~16 m/s²
        );
        addControl(movementControl);

        // --- Camera effects (увязка с прыжками) ---
        camEffectsControl = new CameraEffectsControl(this);
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
    }

    @SuppressWarnings("unused")
    public static class PlayerHud {
        private float playerSpeed;
        private float armorBar = 0.6f;
        private float abilityBar = 0.4f;

        PlayerHud(Player player){
            UIModule uiModule = player.calistaGameEngine.getModuleManager().getModule(UIModule.class);
            uiModule.addPanel(this, "Interface/stats_config.xml");
        }

        public void setPlayerSpeed(float playerSpeed) {
            this.playerSpeed = playerSpeed * 10;
        }
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
