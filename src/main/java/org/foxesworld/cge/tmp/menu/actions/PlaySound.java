package org.foxesworld.cge.tmp.menu.actions;

import com.jme3.app.Application;
import com.jme3.app.state.AppStateManager;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaySound implements MenuAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoBackToMainMenuAction.class);

    @Override
    public void execute(Application app) {
        AppStateManager stateManager = app.getStateManager();
        MainMenuAppState menuState = stateManager.getState(MainMenuAppState.class);
        menuState.getCalistaGameEngine().getSoundManager().play("ui.submit");
    }
}