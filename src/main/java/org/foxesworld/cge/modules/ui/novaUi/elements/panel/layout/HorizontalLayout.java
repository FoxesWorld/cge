package org.foxesworld.cge.modules.ui.novaUi.elements.panel.layout;

import com.jme3.math.Vector2f;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;

public class HorizontalLayout implements UILayout {

    @Override
    public Vector2f calculateNeededSize(PanelElement panel) {
        float totalWidth = 0f;
        float maxHeight = 0f;
        boolean isFirst = true;

        for (UIElement child : panel.getChildren()) {
            if (!isFirst) {
                totalWidth += panel.getSpacing();
            }
            totalWidth += child.getWidth() + child.getMarginH() * 2;
            maxHeight = Math.max(maxHeight, child.getHeight() + child.getMarginV() * 2);
            isFirst = false;
        }
        return new Vector2f(totalWidth, maxHeight);
    }

    @Override
    public void arrangeChildren(PanelElement panel) {
        float currentX = panel.getPaddingH();
        float panelHeight = panel.getHeight();

        for (UIElement child : panel.getChildren()) {
            currentX += child.getMarginH();

            float childY;
            // Вертикальное выравнивание внутри горизонтальной компоновки
            switch (child.getAlign().toLowerCase()) {
                case "top":
                    childY = panelHeight - panel.getPaddingV() - child.getMarginV() - child.getHeight();
                    break;
                case "bottom":
                    childY = panel.getPaddingV() + child.getMarginV();
                    break;
                case "center":
                default: // По умолчанию выравниваем по центру
                    childY = (panelHeight - child.getHeight()) / 2f;
                    break;
            }

            child.getNode().setLocalTranslation(currentX, childY, child.getNode().getLocalTranslation().z);
            currentX += child.getWidth() + child.getMarginH() + panel.getSpacing();
        }
    }
}