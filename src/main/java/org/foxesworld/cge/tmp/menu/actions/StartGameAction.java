package org.foxesworld.cge.tmp.menu.actions;

import com.jme3.app.Application;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;

public class StartGameAction implements MenuAction {
    @Override
    public void execute(MainMenuAppState menuAppState) {
        System.out.println("EXECUTING: Start Game Action!");
         menuAppState.getGameEngine().startGameFromMenu();
    }
}