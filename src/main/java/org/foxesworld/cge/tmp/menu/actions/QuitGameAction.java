package org.foxesworld.cge.tmp.menu.actions;

import com.jme3.app.Application;

public class QuitGameAction implements MenuAction {
    @Override
    public void execute(Application app) {
        System.out.println("EXECUTING: Quit Game Action!");
        app.stop();
    }
}