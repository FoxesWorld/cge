package org.foxesworld.cge.renderer.postProcessing;

import org.foxesworld.cge.core.module.ModuleConfig;

public class PostProcessingConfig extends ModuleConfig {
    private boolean enableBloom = true;
    private boolean enableLsf = true;
    private boolean enableDof = true;
    private boolean enableShadowFilter = true;

    private int size = 4096;           // Высокое разрешение теней
    private int nbSplits = 4;          // 4 каскада оптимальны по визуальному качеству и производительности
    private boolean shadowStabilization = true;
    private float shadowIntensity = 0.7f, shadowLambda = 0.55f;

    private int dofRange = 40;         // Мягкий диапазон размытия
    private int dofFocus = 15;         // Расстояние до фокуса камеры

    private float[] lsfLightDir = { -1.5f, -1.0f, -0.5f };  // Направление "солнца" в мировых координатах
    private float lsfDestiny = 0.9f;   // Плотность рассеивания (чем ближе к 1 — тем более насыщенно)

    private float bloomIntensity = 0.3f;      // Умеренное свечение
    private float bloomExposurePower = 0.5f;       // Естественная экспозиция

    private boolean enableFXAA = true; // Включение сглаживания краёв


    public boolean isEnableBloom() {
        return enableBloom;
    }

    public boolean isEnableLsf() {
        return enableLsf;
    }

    public boolean isEnableDof() {
        return enableDof;
    }

    public boolean isEnableShadowFilter() {
        return enableShadowFilter;
    }

    public int getSize() {
        return size;
    }

    public int getNbSplits() {
        return nbSplits;
    }

    public boolean isShadowStabilization() {
        return shadowStabilization;
    }

    public float getShadowIntensity() {
        return shadowIntensity;
    }

    public float getShadowLambda() {
        return shadowLambda;
    }

    public int getDofRange() {
        return dofRange;
    }

    public int getDofFocus() {
        return dofFocus;
    }

    public float getBloomExposurePower() {
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