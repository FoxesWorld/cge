package org.foxesworld.cge.modules.ecs;

import org.foxesworld.cge.core.module.ModuleConfig;

/**
 * ECSConfig defines runtime settings for the ECSModule,
 * including toggles for optional systems like networking or AI.
 */
public class ECSConfig extends ModuleConfig {

    private boolean enableNetworking = false;
    private boolean enableAI = false;

    public boolean isEnableNetworking() {
        return enableNetworking;
    }

    public void setEnableNetworking(boolean enableNetworking) {
        this.enableNetworking = enableNetworking;
    }

    public boolean isEnableAI() {
        return enableAI;
    }

    public void setEnableAI(boolean enableAI) {
        this.enableAI = enableAI;
    }
}
