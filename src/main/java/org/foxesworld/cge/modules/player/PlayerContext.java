package org.foxesworld.cge.modules.player;

import com.jme3.input.InputManager;
import com.jme3.renderer.Camera;
import com.jme3.bullet.control.CharacterControl;

public interface PlayerContext {
    InputManager getInput();
    Camera getCam();
    CharacterControl getCharacter();
    float getWalkSpeed();
    float getSprintSpeed();
    PlayerHud getPlayerHud();
    PlayerCameraControl getCamControl();
    PlayerAnimationController getAnimationController();

    CameraEffectsControl getCamEffectsControl();
    // Добавьте другие необходимые методы
}