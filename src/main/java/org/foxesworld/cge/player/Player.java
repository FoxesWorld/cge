package org.foxesworld.cge.player;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.physics.PhysicsModule;

public class Player extends Node {

    private final CharacterControl character;
    private final MovementControl movementControl;
    private final FirstPersonCameraControl fpCamControl;
    private final CameraEffectsControl camEffectsControl;
    private final Node camNode;

    public Player(CalistaGameEngine engine, Vector3f spawnPos) {
        super("Player");
        setLocalTranslation(spawnPos);

        // --- Physics setup ---
        BulletAppState bullet = engine.getModuleManager()
                .getModule(PhysicsModule.class)
                .getBulletAppState();
        CapsuleCollisionShape shape = new CapsuleCollisionShape(0.5f, 1.8f, 1);
        character = new CharacterControl(shape, 0.05f);
        character.setJumpSpeed(4f);
        character.setFallSpeed(9.8f);
        character.setGravity(9.8f);
        character.setPhysicsLocation(spawnPos);
        addControl(character);
        bullet.getPhysicsSpace().add(character);

        // --- Movement control ---
        InputManager input = engine.getInputManager();
        // --- First-person camera setup ---
        Camera cam = engine.getCamera();
        camNode = new Node("CamNode");
        camNode.setLocalTranslation(0, 1.6f, 0);
        fpCamControl = new FirstPersonCameraControl(cam, input, this, 1.6f);
        addControl(fpCamControl);

        movementControl = new MovementControl(character, input, cam);
        addControl(movementControl);


        // --- Camera effects (bobbing & landing) ---
        camEffectsControl = new CameraEffectsControl(
                cam, camNode, movementControl,
                1.8f,                                  // bobFrequency
                new Vector3f(0.02f, 0.06f, 1.6f),      // bobAmplitude (x, y, base height)
                FastMath.DEG_TO_RAD * 3f,              // bobTilt
                30f,                                   // jumpSpringK
                4f,                                    // jumpDamping
                60f,                                   // landSpringK
                6f                                     // landDamping
        );

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
}
