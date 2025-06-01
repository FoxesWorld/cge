package org.foxesworld.cge.ui.novaUi.tmp;

import org.foxesworld.cge.ui.novaUi.elements.panel.PanelElement;

/** Интерфейс для элементов, поддерживающих привязку к полям обработчика */
interface BindableUIElement {
    /**
     * Инициализация из значения поля (возвращает приведённое к нужному типу значение)
     */
    Object initializeFromField(Object raw);
    /**
     * Преобразование значения поля перед применением (например, ограничение диапазона)
     */
    Object transformFieldValue(Object raw);
    /**
     * Непосредственное применение преобразованного значения к элементу (установка текста/прогресса)
     */
    void applyFieldValue(Object value);
    /**
     * Возвращает значение по умолчанию (если привязка не удалась)
     */
    Object defaultValue();
    /**
     * Вызывается каждый кадр после применения значения (например, update ProgressElement)
     */
    void onUpdate(float tpf);
    /**
     * Возвращает parentPanel для пометки "грязной" зоны
     */
    PanelElement getParentPanel();
    String getId();
}