package org.foxesworld.cge.ui;

import org.foxesworld.cge.core.module.ModuleConfig;

public class UIConfig extends ModuleConfig {
    private Object eventHandlerTarget;

    public Object getEventHandlerTarget() {
        return eventHandlerTarget;
    }

    public void setEventHandlerTarget(Object eventHandlerTarget) {
        this.eventHandlerTarget = eventHandlerTarget;
    }
}
