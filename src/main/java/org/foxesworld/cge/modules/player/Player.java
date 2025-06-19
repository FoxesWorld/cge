package org.foxesworld.cge.modules.player;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.physics.PhysicsModule;
import org.foxesworld.cge.modules.player.modules.CameraEffectsModule;
import org.foxesworld.cge.modules.player.modules.MovementModule;
import org.foxesworld.cge.modules.player.modules.PlayerHudModule;
import org.foxesworld.cge.modules.player.modules.PlayerSubModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Модульный игрок Calista Game Engine.
 */
public class Player extends Node implements PlayerContext {
    private static final Logger logger = LoggerFactory.getLogger(Player.class);

    private final CalistaGameEngine engine;
    private final InputManager input;
    private final Camera cam;
    private final BulletAppState bullet;
    private final CharacterControl character;
    private PlayerHudModule playerHud;
    private PlayerCameraControl camControl;

    private final List<PlayerSubModule> modules = new ArrayList<>();

    // Параметры физики и перемещения
    public static final float EYE_HEIGHT = 1.6f;
    public static final float PLAYER_RADIUS = 0.45f;
    public static final float PLAYER_HEIGHT = 1.7f;
    public static final float WALK_SPEED = 0.13f;
    public static final float SPRINT_SPEED = 0.18f;

    public Player(CalistaGameEngine engine, Vector3f spawnPos) {
        super("Player");
        this.engine = engine;
        this.input = engine.getInputManager();
        this.cam = engine.getCamera();
        //this.playerHud = new PlayerHudModule(this);

        setLocalTranslation(spawnPos);

        // Physics
        this.bullet = engine.getModuleManager()
                .getModule(PhysicsModule.class)
                .getBulletAppState();
        CapsuleCollisionShape shape = new CapsuleCollisionShape(PLAYER_RADIUS, PLAYER_HEIGHT - 2 * PLAYER_RADIUS, 1);
        this.character = new CharacterControl(shape, 0.05f);
        character.setPhysicsLocation(spawnPos);
        character.setJumpSpeed(5.2f);
        character.setFallSpeed(16.5f);
        character.setGravity(13.8f);
        addControl(character);
        bullet.getPhysicsSpace().add(character);

        engine.getFlyByCamera().setEnabled(false);
        input.setCursorVisible(false);

        // Camera control (по желанию можно сделать модулем)
        camControl = new PlayerCameraControl(this, EYE_HEIGHT, 0.18f, 2.2f, engine.getRootNode());
        addControl(camControl);

        // Подключение модулей:
        addModule(new MovementModule());
        addModule(new CameraEffectsModule());
        addModule(new PlayerHudModule());
    }

    /** Подключить модуль игрока */
    public void addModule(PlayerSubModule module) {
        modules.add(module);
        module.onAttach(this);
    }

    /** Отключить модуль игрока */
    public void removeModule(PlayerSubModule module) {
        modules.remove(module);
        module.onDetach();
    }

    /** Апдейт всех модулей (вызывать из update цикла приложения) */
    public void update(float tpf) {
        for (PlayerSubModule m : modules) m.update(tpf);
    }

    // --- PlayerContext реализация ---
    @Override public InputManager getInput() { return input; }
    @Override public Camera getCam() { return cam; }
    @Override public CharacterControl getCharacter() { return character; }
    @Override public float getWalkSpeed() { return WALK_SPEED; }
    @Override public float getSprintSpeed() { return SPRINT_SPEED; }
    @Override public PlayerHudModule getPlayerHud() { return playerHud; }
    @Override public PlayerCameraControl getCamControl() { return camControl; }
}