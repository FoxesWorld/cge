package org.foxesworld.cge.tmp.menu;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.components.UIComponent;
import org.foxesworld.cge.tmp.menu.components.ViceButton;

import java.util.ArrayList;
import java.util.List;

/**
 * A context object that holds shared resources and collects generated components
 * during the menu building process.
 */
public record BuildContext(MainMenuAppState mainMenuAppState, List<UIComponent> allComponents) {

    public BuildContext(MainMenuAppState mainMenuAppState) {
        this(mainMenuAppState, new ArrayList<>());
    }

    /**
     * Adds a newly created component to the context's collection.
     */
    public void addComponent(Object component) {
        if (component instanceof UIComponent uiComponent) {
            allComponents.add(uiComponent);
        }
    }
}