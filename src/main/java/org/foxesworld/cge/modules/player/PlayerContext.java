package org.foxesworld.cge.modules.player;

import com.jme3.input.InputManager;
import com.jme3.renderer.Camera;
import com.jme3.bullet.control.CharacterControl;
import org.foxesworld.cge.modules.player.modules.PlayerHudModule;

/**
 * Контекст игрока для получения нужных зависимостей всеми модулями Player.
 */
public interface PlayerContext {
    InputManager getInput();
    Camera getCam();
    CharacterControl getCharacter();
    float getWalkSpeed();
    float getSprintSpeed();
    PlayerHudModule getPlayerHud();
    PlayerCameraControl getCamControl();
}