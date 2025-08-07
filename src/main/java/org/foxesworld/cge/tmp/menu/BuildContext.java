package org.foxesworld.cge.tmp.menu;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.components.utils.MenuComponent;
import org.foxesworld.cge.tmp.menu.components.ViceButton;

import java.util.ArrayList;
import java.util.List;

/**
 * A context object that holds shared resources and collects generated components
 * during the menu building process.
 */
public record BuildContext(
        CalistaGameEngine app,
        List<MenuComponent> allComponents
) {
    public BuildContext(CalistaGameEngine app) {
        this(app, new ArrayList<>());
    }

    /**
     * Adds a newly created component to the context's collection.
     */
    public void addComponent(Object component) {
        if (component instanceof MenuComponent menuComponent) {
            allComponents.add(menuComponent);
        }
    }
}