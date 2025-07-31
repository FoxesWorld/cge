package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import org.foxesworld.cge.tmp.menu.components.ViceButton;

// Используем record для простого контейнера
public record BuildContext(
        Application app,
        ViceButton.Style buttonStyle
) {}