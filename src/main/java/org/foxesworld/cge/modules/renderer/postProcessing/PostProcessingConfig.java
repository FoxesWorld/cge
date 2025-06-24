package org.foxesworld.cge.modules.renderer.postProcessing;

import com.jme3.math.ColorRGBA;
import org.foxesworld.cge.core.module.ModuleConfig;

/**
 * Кинематографический стандарт конфигурации пост-обработки
 * для AAA-игрового визуала: агрессивный Bloom, DOF, Film Grain, хроматическая аберрация, цветокоррекция, FXAA, Flare.
 */
public class PostProcessingConfig extends ModuleConfig {

    // --- BLOOM ---
    public static class Bloom {
        private boolean enable = true;
        private float intensity = 0.35f;
        private float threshold = 1.10f;
        private float radius = 0.92f;
        private int exposurePower = 65;

        public boolean isEnable() { return enable; }
        public float getIntensity() { return intensity; }
        public float getThreshold() { return threshold; }
        public float getRadius() { return radius; }
        public int getExposurePower() { return exposurePower; }

        public void setEnable(boolean enable) { this.enable = enable; }
        public void setIntensity(float intensity) { this.intensity = intensity; }
        public void setThreshold(float threshold) { this.threshold = threshold; }
        public void setRadius(float radius) { this.radius = radius; }
        public void setExposurePower(int exposurePower) { this.exposurePower = exposurePower; }
    }
    private final Bloom bloom = new Bloom();

    // --- LENS FLARE (Godrays) ---
    public static class Lsf {
        private boolean enable = false;
        private float[] lightDir = {10f, 3f, 7f};
        private int ghostCount = 0;
        private float haloWidth = 0.5f;
        private float destiny = 0.25f;

        public boolean isEnable() { return enable; }
        public float[] getLightDir() { return lightDir; }
        public int getGhostCount() { return ghostCount; }
        public float getHaloWidth() { return haloWidth; }
        public float getDestiny() { return destiny; }

        public void setEnable(boolean enable) { this.enable = enable; }
        public void setLightDir(float[] lightDir) { this.lightDir = lightDir; }
        public void setGhostCount(int ghostCount) { this.ghostCount = ghostCount; }
        public void setHaloWidth(float haloWidth) { this.haloWidth = haloWidth; }
        public void setDestiny(float destiny) { this.destiny = destiny; }
    }
    private final Lsf lsf = new Lsf();

    // --- DEPTH OF FIELD (DOF) ---
    public static class Dof {
        private boolean enable = true;
        private float focus = 18f;
        private float range = 60f;
        private float aperture = 1.8f;
        private float maxBlur = 2.2f;

        public boolean isEnable() { return enable; }
        public float getFocus() { return focus; }
        public float getRange() { return range; }
        public float getAperture() { return aperture; }
        public float getMaxBlur() { return maxBlur; }

        public void setEnable(boolean enable) { this.enable = enable; }
        public void setFocus(float focus) { this.focus = focus; }
        public void setRange(float range) { this.range = range; }
        public void setAperture(float aperture) { this.aperture = aperture; }
        public void setMaxBlur(float maxBlur) { this.maxBlur = maxBlur; }
    }
    private final Dof dof = new Dof();

    // --- COLOR GRADING & TONEMAPPING ---
    public static class ColorGrading {
        private boolean enable = true;
        private String lut = "Assets/LUTs/Cinematic.cube";
        private float exposure = 1.22f;
        private float contrast = 1.29f;
        private float saturation = 1.18f;

        public boolean isEnable() { return enable; }
        public String getLut() { return lut; }
        public float getExposure() { return exposure; }
        public float getContrast() { return contrast; }
        public float getSaturation() { return saturation; }

        public void setEnable(boolean enable) { this.enable = enable; }
        public void setLut(String lut) { this.lut = lut; }
        public void setExposure(float exposure) { this.exposure = exposure; }
        public void setContrast(float contrast) { this.contrast = contrast; }
        public void setSaturation(float saturation) { this.saturation = saturation; }
    }
    private final ColorGrading colorGrading = new ColorGrading();

    // --- FXAA ---
    public static class FXAA {
        private boolean enable = true;
        private int quality = 3;

        public boolean isEnable() { return enable; }
        public int getQuality() { return quality; }

        public void setEnable(boolean enable) { this.enable = enable; }
        public void setQuality(int quality) { this.quality = quality; }
    }
    private final FXAA fxaa = new FXAA();

    public static class  SSAOfilter {
        private boolean enable = true;
        private float sampleRadius = 2.9299974f;
        private float intensity = 25f;
        private float scale = 5.8100376f;
        private float bias = 0.091000035f;

        public boolean isEnable() {
            return enable;
        }

        public float getSampleRadius() {
            return sampleRadius;
        }

        public float getIntensity() {
            return intensity;
        }

        public float getScale() {
            return scale;
        }

        public float getBias() {
            return bias;
        }
    }

    private final SSAOfilter ssaOfilter = new SSAOfilter();

    // --- Getters for all post-process classes ---
    public Bloom getBloom() { return bloom; }
    public Lsf getLsf() { return lsf; }
    public Dof getDof() { return dof; }

    public SSAOfilter getSsaOfilter() {return ssaOfilter;}

    public ColorGrading getColorGrading() { return colorGrading; }
    public FXAA getFxaa() { return fxaa; }
}