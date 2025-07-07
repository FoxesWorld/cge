package org.foxesworld.cge.modules.player;

import com.jme3.input.InputManager;
import com.jme3.renderer.Camera;
import com.jme3.bullet.control.CharacterControl;
import org.foxesworld.cge.modules.player.camEffects.CameraEffectsControl;
import org.foxesworld.cge.modules.player.hud.PlayerHud;

public interface PlayerContext {
    InputManager getInput();
    Camera getCam();
    CharacterControl getCharacter();
    PlayerHud getPlayerHud();
    PlayerCameraControl getCamControl();
    PlayerAnimationController getAnimationController();
    CameraEffectsControl getCamEffectsControl();
}