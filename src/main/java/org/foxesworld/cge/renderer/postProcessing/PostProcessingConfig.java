package org.foxesworld.cge.renderer.postProcessing;

public class PostProcessingConfig {
    public boolean enableBloom = true;
    public boolean enableLsf = true;
    public boolean enableDof = true;
    public int dofRange = 50, dofFocus = 9;
    public float[] lsfLightDir = {10,3,7};
    public float lsfDestiny = 5;
    public float bloomIntensity = 1.5f;
    public boolean enableFXAA = true;
}