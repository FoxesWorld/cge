package org.foxesworld.cge.renderer.camera;

import org.foxesworld.cge.core.module.ModuleConfig;

public class CameraConfig extends ModuleConfig {
    public float fov = 60f;
    public int resolutionWidth = 1280;
    public int resolutionHeight = 720;
    public float nearClip = 0.1f;
    public float farClip = 1000f;
    public float moveSpeed = 20f;
}
