package org.foxesworld.cge.renderer.postProcessing;

public class PostProcessingConfig {
    private boolean enableBloom = true;
    private boolean enableLsf = true;
    private boolean enableDof = true;
    private int dofRange = 50, dofFocus = 9;
    private float[] lsfLightDir = {10,3,7};
    private float lsfDestiny = 5;
    private float bloomIntensity = 1.5f;
    private int bloomExposurePower = 60;
    private boolean enableFXAA = true;

    public boolean isEnableBloom() {
        return enableBloom;
    }

    public boolean isEnableLsf() {
        return enableLsf;
    }

    public boolean isEnableDof() {
        return enableDof;
    }

    public int getDofRange() {
        return dofRange;
    }

    public int getDofFocus() {
        return dofFocus;
    }

    public int getBloomExposurePower() {
        return bloomExposurePower;
    }

    public float[] getLsfLightDir() {
        return lsfLightDir;
    }

    public float getLsfDestiny() {
        return lsfDestiny;
    }

    public float getBloomIntensity() {
        return bloomIntensity;
    }

    public boolean isEnableFXAA() {
        return enableFXAA;
    }
}