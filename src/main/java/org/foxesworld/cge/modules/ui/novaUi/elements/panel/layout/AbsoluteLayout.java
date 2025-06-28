package org.foxesworld.cge.modules.ui.novaUi.elements.panel.layout;

import com.jme3.math.Vector2f;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;

public class AbsoluteLayout implements UILayout {
    @Override
    public Vector2f calculateNeededSize(PanelElement panel) {
        float maxX = 0;
        float maxY = 0;
        for (UIElement child : panel.getChildren()) {
            maxX = Math.max(maxX, child.getPosX() + child.getWidth());
            maxY = Math.max(maxY, child.getPosY() + child.getHeight());
        }
        return new Vector2f(maxX, maxY);
    }

    @Override
    public void arrangeChildren(PanelElement panel) {
        for (UIElement child : panel.getChildren()) {
            child.getNode().setLocalTranslation(
                    child.getPosX() + panel.getPaddingH() + child.getMarginH(),
                    child.getPosY() + panel.getPaddingV() + child.getMarginV(),
                    child.getNode().getLocalTranslation().z
            );
        }
    }
}