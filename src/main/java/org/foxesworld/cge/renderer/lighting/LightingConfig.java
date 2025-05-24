package org.foxesworld.cge.renderer.lighting;

import com.jme3.math.ColorRGBA;

public class LightingConfig {
    public float[] sunDirection = {-1f, -2f, -3f};
    public ColorRGBA sunColor = ColorRGBA.White;
    public ColorRGBA ambientColor = ColorRGBA.White.mult(0.3f);
}