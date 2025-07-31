package org.foxesworld.cge.tmp.menu.actions;

import com.jme3.app.Application;

/**
 * Represents a self-contained command that can be executed from a menu item.
 * This interface decouples the menu UI from the application logic.
 */
public interface MenuAction {
    /**
     * Executes the action.
     * @param app The main application instance, providing context and access to other systems.
     */
    void execute(Application app);
}