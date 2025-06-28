package org.foxesworld.cge.modules.ui.novaUi.elements.panel.layout;

import com.jme3.math.Vector2f;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;

public interface UILayout {
    Vector2f calculateNeededSize(PanelElement panel);
    void arrangeChildren(PanelElement panel);
}