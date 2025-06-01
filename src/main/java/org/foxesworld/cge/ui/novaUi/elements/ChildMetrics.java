package org.foxesworld.cge.ui.novaUi.elements;

/**
 * Интерфейс для извлечения метрик (rawX, rawY, width, height) для разных типов UIElement.
 */
interface ChildMetrics {
    float getRawX(UIElement ue);
    float getRawY(UIElement ue);
    float getWidth(UIElement ue);
    float getHeight(UIElement ue);
}