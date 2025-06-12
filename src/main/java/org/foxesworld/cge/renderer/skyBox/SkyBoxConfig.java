package org.foxesworld.cge.renderer.skyBox;

import com.jme3.math.ColorRGBA;
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
     * Cube,
     * TopDome,
     * TwoDomes;
     */
    private String starsOption = "Cube";

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
    private float cloudiness = 0.5f;
    private float cloudYOffset = 0.4f;
    private float topAngle = 1.78f;
    /** Продолжительность полного виртуального дня в секундах (например, 600.0f для 10 минут). */
    private float dayLengthSec = 600f;

    /** Включает блюм (световые ореолы) через FilterPostProcessor. */
    private boolean bloomEnabled = true;

    /** Яркость экспозиции для небесного бокса (обычно 1.0). */
    private float skyExposure = 1.0f;

    /** Включает отображение облаков (SkyControl). */
    private boolean cloudsEnabled = true;

    /** Включает отображение звёзд. */
    private boolean starsEnabled = true;

    /** Включает включение DirectionalLightShadowRenderer (генерация теней). */
    private boolean enableShadows = true;

    /** Включает динамическое направление солнца (анимация). */
    private boolean animatedSun = true;

    /** Включает динамическое направление луны (анимация). */
    private boolean animatedMoon = true;

    /** Цвет фонового (окружающего) освещения — AmbientLight. */
    private ColorRGBA ambientColor = new ColorRGBA(0.2f, 0.2f, 0.2f, 1.0f);

    /** Интенсивность блюма (например, 1.2f). */
    private float bloomIntensity = 1.2f;

    /** Параметр экспозиции для блюма (например, 2.0f). */
    private float bloomExposure = 2.0f;

    /** Цвет солнца (RGB), применяется к направленному свету днём. */
    private ColorRGBA sunColor = ColorRGBA.White.clone();

    /** Цвет луны (обычно серовато-голубой, RGB). */
    private ColorRGBA moonColor = new ColorRGBA(0.6f, 0.7f, 1.0f, 1.0f);

    /** Интенсивность солнечного света днём. */
    private float dayIntensity = 1.5f;

    /** Интенсивность освещения ночью. */
    private float nightIntensity = 0.1f;

    /** Интенсивность лунного света. */
    private float moonIntensity = 0.3f;

    /** Экспозиция HDR в дневное время. */
    private float dayExposure = 1.2f;

    /** Экспозиция HDR в ночное время. */
    private float nightExposure = 0.3f;



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

    public float getCloudiness() {
        return cloudiness;
    }

    public float getCloudYOffset() {
        return cloudYOffset;
    }

    public float getTopAngle() {
        return topAngle;
    }
    public float getDayLengthSec() {
        return dayLengthSec;
    }

    public boolean isBloomEnabled() {
        return bloomEnabled;
    }

    public float getSkyExposure() {
        return skyExposure;
    }

    public boolean isCloudsEnabled() {
        return cloudsEnabled;
    }

    public boolean isStarsEnabled() {
        return starsEnabled;
    }

    public boolean isEnableShadows() {
        return enableShadows;
    }

    public boolean isAnimatedSun() {
        return animatedSun;
    }

    public boolean isAnimatedMoon() {
        return animatedMoon;
    }

    public float getBloomIntensity() {
        return bloomIntensity;
    }

    public float getBloomExposure() {
        return bloomExposure;
    }

    public ColorRGBA getSunColor() {
        return sunColor;
    }

    public ColorRGBA getMoonColor() {
        return moonColor;
    }

    public float getDayIntensity() {
        return dayIntensity;
    }

    public float getNightIntensity() {
        return nightIntensity;
    }

    public float getMoonIntensity() {
        return moonIntensity;
    }

    public float getDayExposure() {
        return dayExposure;
    }

    public float getNightExposure() {
        return nightExposure;
    }

    public ColorRGBA getAmbientColor() {
        return ambientColor;
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

    public void setCloudiness(float cloudiness) {
        this.cloudiness = cloudiness;
    }

    public void setCloudYOffset(float cloudYOffset) {
        this.cloudYOffset = cloudYOffset;
    }

    public void setTopAngle(float topAngle) {
        this.topAngle = topAngle;
    }
    public void setDayLengthSec(float dayLengthSec) {
        this.dayLengthSec = dayLengthSec;
    }

    public void setBloomEnabled(boolean bloomEnabled) {
        this.bloomEnabled = bloomEnabled;
    }

    public void setSkyExposure(float skyExposure) {
        this.skyExposure = skyExposure;
    }

    public void setCloudsEnabled(boolean cloudsEnabled) {
        this.cloudsEnabled = cloudsEnabled;
    }

    public void setStarsEnabled(boolean starsEnabled) {
        this.starsEnabled = starsEnabled;
    }

    public void setEnableShadows(boolean enableShadows) {
        this.enableShadows = enableShadows;
    }

    public void setAnimatedSun(boolean animatedSun) {
        this.animatedSun = animatedSun;
    }

    public void setAnimatedMoon(boolean animatedMoon) {
        this.animatedMoon = animatedMoon;
    }

}
