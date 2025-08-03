package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.components.MenuComponent;
import org.foxesworld.cge.tmp.menu.components.ViceButton;

import java.util.ArrayList;
import java.util.List;

/**
 * A context object that holds shared resources and collects generated components
 * during the menu building process.
 */
public record BuildContext(
        CalistaGameEngine app,
        ViceButton.Style buttonStyle,
        List<MenuComponent> allComponents
) {
    public BuildContext(CalistaGameEngine app, ViceButton.Style buttonStyle) {
        this(app, buttonStyle, new ArrayList<>());
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