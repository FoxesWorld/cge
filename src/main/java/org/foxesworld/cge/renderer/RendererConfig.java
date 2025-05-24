package org.foxesworld.cge.renderer;

import com.jme3.math.ColorRGBA;

/**
 * Конфигурация модуля рендеринга.
 */
public class RendererConfig {
    public float fov = 60f;
    public int resolutionWidth = 1280;
    public int resolutionHeight = 720;
    public float nearClip = 0.1f;
    public float farClip = 1000f;
    public float cameraSpeed = 10f;
    public ColorRGBA ambientColor = ColorRGBA.White;
    public ColorRGBA sunColor = ColorRGBA.White;
    public boolean enablePostEffects = true;
    public float bloomIntensity = 2.0f;
}
