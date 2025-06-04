package org.foxesworld.cge.renderer.skyBox;

import org.foxesworld.cge.core.module.ModuleConfig;

/**
 * Конфигурация для SkyBox-модуля: задаёт параметры неба, облаков, света и теней.
 */
public class SkyBoxConfig extends ModuleConfig {
    /** Имя ресурса кубической текстуры неба (например, "cubemap_0"). */
    private String skyBoxTexture = "cubemap_0";

    /**
     * Режим теней:
     * Options:
     *   Off,
     *   Cast,
     *   Receive,
     *   CastAndReceive,
     *   Inherit
     */
    private String shadowMode = "CastAndReceive";

    /**
     * Тип окружения для SkyFactory:
     * Options:
     *   CubeMap,
     *   TopDome,
     *   TwoDomes
     */
    private String envMap = "CubeMap";

    /**
     * Тип отображения звёзд и луны:
     * Options:
     *   CubeMap,
     *   SphereMap,
     *   EquirectMap
     */
    private String starsOption = "EquirectMap";

    /** Интенсивность «плющения» облаков (0.0 … 1.0). */
    private float cloudFlattering = 0.5f;

    /** Включение нижнего полушария неба. */
    private boolean bottomDome = true;

    /** Размер карты теней для DirectionalLightShadowRenderer (например, 2048). */
    private int shadowMapSize = 2048;

    /** Количество фрустумов (каскадов) для DirectionalLightShadowRenderer (например, 3). */
    private int shadowFrustumCount = 3;

    /**
     * Режим фильтрации краёв теней:
     * Options:
     *   Nearest,
     *   Bilinear,
     *   PCF4,
     *   PCF8,
     *   PCFPOISSON
     */
    private String edgeFilteringMode = "PCF4";

    /** Максимальное расстояние по Z для теней (например, 1000.0). */
    private float shadowZExtend = 1000f;

    /** Интенсивность направленного света солнца (1.0 = стандартный белый). */
    private float sunLightIntensity = 1.2f;

    /** Интенсивность направленного света луны (1.0 = стандартный белый). */
    private float moonLightIntensity = 0.4f;

    // ----------------------------------
    // Геттеры
    // ----------------------------------

    public String getSkyBoxTexture() {
        return skyBoxTexture;
    }

    public String getShadowMode() {
        return shadowMode;
    }

    public String getEnvMap() {
        return envMap;
    }

    public String getStarsOption() {
        return starsOption;
    }

    public float getCloudFlattering() {
        return cloudFlattering;
    }

    public boolean isBottomDome() {
        return bottomDome;
    }

    public int getShadowMapSize() {
        return shadowMapSize;
    }

    public int getShadowFrustumCount() {
        return shadowFrustumCount;
    }

    public String getEdgeFilteringMode() {
        return edgeFilteringMode;
    }

    public float getShadowZExtend() {
        return shadowZExtend;
    }

    public float getSunLightIntensity() {
        return sunLightIntensity;
    }

    public float getMoonLightIntensity() {
        return moonLightIntensity;
    }

    // ----------------------------------
    // Сеттеры (если конфиг поддерживает изменение во время работы)
    // ----------------------------------

    public void setSkyBoxTexture(String skyBoxTexture) {
        this.skyBoxTexture = skyBoxTexture;
    }

    public void setShadowMode(String shadowMode) {
        this.shadowMode = shadowMode;
    }

    public void setEnvMap(String envMap) {
        this.envMap = envMap;
    }

    public void setStarsOption(String starsOption) {
        this.starsOption = starsOption;
    }

    public void setCloudFlattering(float cloudFlattering) {
        this.cloudFlattering = cloudFlattering;
    }

    public void setBottomDome(boolean bottomDome) {
        this.bottomDome = bottomDome;
    }

    public void setShadowMapSize(int shadowMapSize) {
        this.shadowMapSize = shadowMapSize;
    }

    public void setShadowFrustumCount(int shadowFrustumCount) {
        this.shadowFrustumCount = shadowFrustumCount;
    }

    public void setEdgeFilteringMode(String edgeFilteringMode) {
        this.edgeFilteringMode = edgeFilteringMode;
    }

    public void setShadowZExtend(float shadowZExtend) {
        this.shadowZExtend = shadowZExtend;
    }

    public void setSunLightIntensity(float sunLightIntensity) {
        this.sunLightIntensity = sunLightIntensity;
    }

    public void setMoonLightIntensity(float moonLightIntensity) {
        this.moonLightIntensity = moonLightIntensity;
    }
}
