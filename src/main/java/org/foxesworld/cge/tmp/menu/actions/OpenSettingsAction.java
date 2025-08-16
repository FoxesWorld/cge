package org.foxesworld.cge.tmp.menu.actions;

import com.jme3.app.Application;
import com.jme3.app.state.AppStateManager;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSettingsAction implements MenuAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSettingsAction.class);

    @Override
    public void execute(MainMenuAppState menuState) {

        if (menuState != null) {
            LOGGER.info("Executing action: Open Settings Screen");
            menuState.showSettingsScreen();
        } else {
            LOGGER.warn("Cannot open settings: MainMenuAppState is not active.");
        }
    }
}