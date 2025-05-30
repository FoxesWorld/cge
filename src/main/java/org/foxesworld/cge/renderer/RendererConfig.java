package org.foxesworld.cge.renderer;

import com.jme3.math.ColorRGBA;

public class RendererConfig {
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
