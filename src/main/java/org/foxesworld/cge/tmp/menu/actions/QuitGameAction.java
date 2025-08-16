package org.foxesworld.cge.tmp.menu.actions;

import com.jme3.app.Application;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;

public class QuitGameAction implements MenuAction {
    @Override
    public void execute(MainMenuAppState menuAppState) {
        System.out.println("EXECUTING: Quit Game Action!");
        menuAppState.getGameEngine().stop();
    }
}