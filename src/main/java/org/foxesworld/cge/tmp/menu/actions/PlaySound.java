package org.foxesworld.cge.tmp.menu.actions;

import com.jme3.app.state.AppStateManager;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;

@Deprecated
public class PlaySound implements MenuAction {

    @Override

    public void execute(MainMenuAppState app) {
        AppStateManager stateManager = app.getStateManager();
        MainMenuAppState menuState = stateManager.getState(MainMenuAppState.class);
        menuState.getGameEngine().getSoundManager().play("ui.submit");
    }
}