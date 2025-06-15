package org.foxesworld.cge.modules.renderer;

import org.foxesworld.cge.core.module.ModuleConfig;

public class RendererConfig extends ModuleConfig {
    private int resolutionWidth = 1280;
    private int resolutionHeight = 720;
    private float nearClip = 0.1f;
    private float farClip = 1000f;
    private boolean enablePostEffects = true;

    public int getResolutionWidth() {
        return resolutionWidth;
    }

    public int getResolutionHeight() {
        return resolutionHeight;
    }

    public float getNearClip() {
        return nearClip;
    }

    public float getFarClip() {
        return farClip;
    }

    public boolean isEnablePostEffects() {
        return enablePostEffects;
    }
}
