package org.foxesworld.cge.modules.ui;

import org.foxesworld.cge.core.module.ModuleConfig;

/**
 * Конфигурация UIModule: путь к XML, объект-обработчик событий (HUDController и т.п.).
 */
public class UIConfig extends ModuleConfig {

    /**
     * Объект-обработчик: у него в полях должны лежать переменные с именами,
     * совпадающими с id TextElement из XML, чтобы биндинг отработал в UIPanel.
     */
    private Object eventHandlerTarget = null;

    // Геттеры и сеттеры
    public Object getEventHandlerTarget() {
        return eventHandlerTarget;
    }

    public void setEventHandlerTarget(Object eventHandlerTarget) {
        this.eventHandlerTarget = eventHandlerTarget;
    }
}
