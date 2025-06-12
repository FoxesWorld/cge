package org.foxesworld.cge.renderer.postProcessing;

import org.foxesworld.cge.core.module.ModuleConfig;

/**
 * Конфигурация пост-обработки: кинематографические эффекты.
 */
public class PostProcessingConfig extends ModuleConfig {
    // Bloom
    private boolean enableBloom = true;
    private float bloomIntensity = 0.25f;
    private float bloomThreshold = 1.2f;
    private float bloomRadius = 0.7f;
    private int bloomExposurePower = 60;

    // Lens Flare
    private boolean enableLsf = true;
    private float[] lsfLightDir = {10f, 3f, 7f};
    private int lsfGhostCount = 4;
    private float lsfHaloWidth = 0.4f;
    private float lsfDestiny = 0.2f;

    // Depth of Field
    private boolean enableDof = true;
    private float dofFocus = 12f;
    private float dofRange = 40f;
    private float dofAperture = 2.8f;
    private float dofMaxBlur = 1.5f;

    // Motion Blur
    private boolean enableMotionBlur = true;
    private int motionBlurSampleCount = 16;
    private float motionBlurStrength = 0.6f;

    // Vignette
    private boolean enableVignette = true;
    private float vignetteIntensity = 0.45f;
    private float vignetteSmoothness = 0.75f;

    // Color Grading
    private boolean enableColorGrading = true;
    private String colorGradingLUT = "Assets/LUTs/Cinematic.cube";
    private float exposure = 1.1f;
    private float contrast = 1.2f;
    private float saturation = 1.1f;

    // FXAA
    private boolean enableFXAA = true;
    private int fxaaQuality = 2;

    // --- Getters ---
    public boolean isEnableBloom() { return enableBloom; }
    public float getBloomIntensity() { return bloomIntensity; }
    public float getBloomThreshold() { return bloomThreshold; }
    public float getBloomRadius() { return bloomRadius; }
    public int getBloomExposurePower() { return bloomExposurePower; }

    public boolean isEnableLsf() { return enableLsf; }
    public float[] getLsfLightDir() { return lsfLightDir; }
    public int getLsfGhostCount() { return lsfGhostCount; }
    public float getLsfHaloWidth() { return lsfHaloWidth; }
    public float getLsfDestiny() { return lsfDestiny; }

    public boolean isEnableDof() { return enableDof; }
    public float getDofFocus() { return dofFocus; }
    public float getDofRange() { return dofRange; }
    public float getDofAperture() { return dofAperture; }
    public float getDofMaxBlur() { return dofMaxBlur; }

    public boolean isEnableMotionBlur() { return enableMotionBlur; }
    public int getMotionBlurSampleCount() { return motionBlurSampleCount; }
    public float getMotionBlurStrength() { return motionBlurStrength; }

    public boolean isEnableVignette() { return enableVignette; }
    public float getVignetteIntensity() { return vignetteIntensity; }
    public float getVignetteSmoothness() { return vignetteSmoothness; }

    public boolean isEnableColorGrading() { return enableColorGrading; }
    public String getColorGradingLUT() { return colorGradingLUT; }
    public float getExposure() { return exposure; }
    public float getContrast() { return contrast; }
    public float getSaturation() { return saturation; }

    public boolean isEnableFXAA() { return enableFXAA; }
    public int getFxaaQuality() { return fxaaQuality; }
}
