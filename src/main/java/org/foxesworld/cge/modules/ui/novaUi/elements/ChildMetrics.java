package org.foxesworld.cge.modules.ui.novaUi.elements;

/**
 * Интерфейс для извлечения метрик (rawX, rawY, width, height) для разных типов UIElement.
 */
public interface ChildMetrics {
    float getRawX(UIElement ue);
    float getRawY(UIElement ue);
    float getWidth(UIElement ue);
    float getHeight(UIElement ue);
}