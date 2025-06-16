package org.foxesworld.cge.modules.renderer.postProcessing;

import org.foxesworld.cge.core.module.ModuleConfig;

/**
 * Кинематографический стандарт конфигурации пост-обработки
 * для AAA-игрового визуала: агрессивный Bloom, DOF, Film Grain, хроматическая аберрация, цветокоррекция, FXAA.
 */
public class PostProcessingConfig extends ModuleConfig {
    // --- BLOOM ---
    private boolean enableBloom = true;
    private float bloomIntensity = 0.35f;        // ярче для glow
    private float bloomThreshold = 1.10f;        // чуть ниже порог
    private float bloomRadius = 0.92f;           // больше размытости
    private int bloomExposurePower = 65;

    // --- LENS FLARE (Godrays) ---
    private boolean enableLsf = false;            // как правило, только для особых сцен
    private float[] lsfLightDir = {10f, 3f, 7f};
    private int lsfGhostCount = 0;
    private float lsfHaloWidth = 0.5f;
    private float lsfDestiny = 0.25f;

    // --- DEPTH OF FIELD (DOF) ---
    private boolean enableDof = true;
    private float dofFocus = 18f;                 // ближе к кинофокусам
    private float dofRange = 60f;
    private float dofAperture = 1.8f;             // малое f-number для сильной ГРИП
    private float dofMaxBlur = 2.2f;

    // --- MOTION BLUR ---
    private boolean enableMotionBlur = true;
    private int motionBlurSampleCount = 24;       // больше сэмплов для плавности
    private float motionBlurStrength = 0.7f;

    // --- VIGNETTE ---
    private boolean enableVignette = true;
    private float vignetteIntensity = 0.52f;      // заметный виньет
    private float vignetteSmoothness = 0.88f;

    // --- FILM GRAIN ---
    private Boolean enableFilmGrain = true;
    private Float filmGrainIntensity = 0.22f;     // чистый AAA grain
    private Float filmGrainScale = 1.0f;

    // --- CHROMATIC ABERRATION ---
    private Boolean enableChromaticAberration = true;
    private Float chromaticAberrationStrength = 0.012f;
    private Boolean chromaticRadial = true;

    // --- COLOR GRADING & TONEMAPPING ---
    private boolean enableColorGrading = true;
    private String colorGradingLUT = "Assets/LUTs/Cinematic.cube";
    private float exposure = 1.22f;
    private float contrast = 1.29f;
    private float saturation = 1.18f;

    // --- FXAA ---
    private boolean enableFXAA = true;
    private int fxaaQuality = 3;                  // максимальное качество

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

    public Boolean isEnableFilmGrain() { return enableFilmGrain; }
    public Float getFilmGrainIntensity() { return filmGrainIntensity; }
    public Float getFilmGrainScale() { return filmGrainScale; }

    public Boolean isEnableChromaticAberration() { return enableChromaticAberration; }
    public Float getChromaticAberrationStrength() { return chromaticAberrationStrength; }
    public Boolean isChromaticRadial() { return chromaticRadial; }

    public boolean isEnableColorGrading() { return enableColorGrading; }
    public String getColorGradingLUT() { return colorGradingLUT; }
    public float getExposure() { return exposure; }
    public float getContrast() { return contrast; }
    public float getSaturation() { return saturation; }

    public boolean isEnableFXAA() { return enableFXAA; }
    public int getFxaaQuality() { return fxaaQuality; }
}