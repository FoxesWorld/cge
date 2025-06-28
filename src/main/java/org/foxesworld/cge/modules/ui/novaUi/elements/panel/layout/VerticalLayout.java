package org.foxesworld.cge.modules.ui.novaUi.elements.panel.layout;

import com.jme3.math.Vector2f;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;

public class VerticalLayout implements UILayout {

    @Override
    public Vector2f calculateNeededSize(PanelElement panel) {
        float maxWidth = 0f;
        float totalHeight = 0f;
        boolean isFirst = true;

        for (UIElement child : panel.getChildren()) {
            if (!isFirst) {
                totalHeight += panel.getSpacing();
            }
            maxWidth = Math.max(maxWidth, child.getWidth() + child.getMarginH() * 2);
            totalHeight += child.getHeight() + child.getMarginV() * 2;
            isFirst = false;
        }
        return new Vector2f(maxWidth, totalHeight);
    }

    @Override
    public void arrangeChildren(PanelElement panel) {
        float panelHeight = panel.getHeight();
        float panelWidth = panel.getWidth();
        float currentY = panelHeight - panel.getPaddingV();

        for (UIElement child : panel.getChildren()) {
            currentY -= child.getMarginV();

            float childX;
            // Горизонтальное выравнивание внутри вертикальной компоновки
            switch (child.getAlign().toLowerCase()) {
                case "right":
                    childX = panelWidth - panel.getPaddingH() - child.getMarginH() - child.getWidth();
                    break;
                case "center":
                    childX = (panelWidth - child.getWidth()) / 2f;
                    break;
                case "left":
                default: // По умолчанию выравниваем по левому краю
                    childX = panel.getPaddingH() + child.getMarginH();
                    break;
            }

            child.getNode().setLocalTranslation(childX, currentY - child.getHeight(), child.getNode().getLocalTranslation().z);
            currentY -= (child.getHeight() + child.getMarginV() + panel.getSpacing());
        }
    }
}