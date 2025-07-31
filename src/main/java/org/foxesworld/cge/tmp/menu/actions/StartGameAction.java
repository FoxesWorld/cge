package org.foxesworld.cge.tmp.menu.actions;

import com.jme3.app.Application;
import org.foxesworld.cge.CalistaGameEngine;

public class StartGameAction implements MenuAction {
    @Override
    public void execute(Application app) {
        System.out.println("EXECUTING: Start Game Action!");
         ((CalistaGameEngine) app).startGameFromMenu();
    }
}