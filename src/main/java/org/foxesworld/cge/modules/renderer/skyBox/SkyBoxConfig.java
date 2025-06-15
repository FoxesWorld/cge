package org.foxesworld.cge.modules.renderer.skyBox;

import com.jme3.math.ColorRGBA;
import org.foxesworld.cge.core.module.ModuleConfig;

/**
 * Configuration class for the SkyBox module: defines parameters for sky, clouds, lighting, and shadows.
 */
public class SkyBoxConfig extends ModuleConfig {

    // ----------------------------------
    // Sky & Environment
    // ----------------------------------

    /** Name of the cube map texture resource (e.g., "cubemap_0"). */
    private String skyBoxTexture = "cubemap_0";

    /** Environment type for the SkyFactory. Options: CubeMap, TopDome, TwoDomes. */
    private String envMap = "CubeMap";

    /** Option for rendering stars and the moon. Options: Cube, TopDome, TwoDomes. */
    private String starsOption = "Cube";

    /** Enables rendering of the bottom hemisphere. */
    private boolean bottomDome = true;

    // ----------------------------------
    // Clouds
    // ----------------------------------

    /** Flattening effect of the clouds (0.0 to 1.0). */
    private float cloudFlattering = 0.5f;

    /** Cloud density (0.0 to 1.0). */
    private float cloudiness = 0.5f;

    /** Vertical offset of the clouds. */
    private float cloudYOffset = 0.4f;

    /** Sky dome top angle. */
    private float topAngle = 1.78f;

    /** Enables rendering of clouds. */
    private boolean cloudsEnabled = true;

    // ----------------------------------
    // Lighting
    // ----------------------------------

    /** Shadow mode. Options: Off, Cast, Receive, CastAndReceive, Inherit. */
    private String shadowMode = "CastAndReceive";

    /** Size of the shadow map texture (e.g., 2048). */
    private int shadowMapSize = 2048;

    /** Number of frustums (cascades) for DirectionalLightShadowRenderer. */
    private int shadowFrustumCount = 3;

    /** Edge filtering mode. Options: Nearest, Bilinear, PCF4, PCF8, PCFPOISSON. */
    private String edgeFilteringMode = "PCF4";

    /** Maximum Z extent for shadows. */
    private float shadowZExtend = 1000f;

    /** Enables shadows via DirectionalLightShadowRenderer. */
    private boolean enableShadows = true;

    /** Directional sunlight intensity. */
    private float sunLightIntensity = 1.2f;

    /** Directional moonlight intensity. */
    private float moonLightIntensity = 0.4f;

    /** Ambient light color. */
    private ColorRGBA ambientColor = new ColorRGBA(0.2f, 0.2f, 0.2f, 1.0f);

    /** Enables animated sun direction. */
    private boolean animatedSun = true;

    /** Enables animated moon direction. */
    private boolean animatedMoon = true;

    /** Color of sunlight. */
    private ColorRGBA sunColor = ColorRGBA.White.clone();

    /** Color of moonlight. */
    private ColorRGBA moonColor = new ColorRGBA(0.6f, 0.7f, 1.0f, 1.0f);

    /** Daytime light intensity. */
    private float dayIntensity = 1.5f;

    /** Nighttime ambient intensity. */
    private float nightIntensity = 0.1f;

    /** Moonlight intensity. */
    private float moonIntensity = 0.3f;

    // ----------------------------------
    // Time
    // ----------------------------------

    /** Length of a full virtual day in seconds (e.g., 600.0f for 10 minutes). */
    private float dayLengthSec = 600f;

    private float verticalAngle = 64f;

    // ----------------------------------
    // Post-Processing
    // ----------------------------------

    /** Enables bloom (glow) effect via FilterPostProcessor. */
    private boolean bloomEnabled = true;

    /** Overall exposure for the sky. */
    private float skyExposure = 1.0f;

    /** Bloom intensity. */
    private float bloomIntensity = 1.2f;

    /** Bloom exposure. */
    private float bloomExposure = 2.0f;

    /** Daytime HDR exposure. */
    private float dayExposure = 1.2f;

    /** Nighttime HDR exposure. */
    private float nightExposure = 0.3f;

    /** Enables star rendering. */
    private boolean starsEnabled = true;

    // ----------------------------------
    // Getters & Setters
    // ----------------------------------


    public String getSkyBoxTexture() {
        return skyBoxTexture;
    }

    public String getEnvMap() {
        return envMap;
    }

    public String getStarsOption() {
        return starsOption;
    }

    public boolean isBottomDome() {
        return bottomDome;
    }

    public float getCloudFlattering() {
        return cloudFlattering;
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

    public boolean isCloudsEnabled() {
        return cloudsEnabled;
    }

    public String getShadowMode() {
        return shadowMode;
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

    public boolean isEnableShadows() {
        return enableShadows;
    }

    public float getSunLightIntensity() {
        return sunLightIntensity;
    }

    public float getMoonLightIntensity() {
        return moonLightIntensity;
    }

    public ColorRGBA getAmbientColor() {
        return ambientColor;
    }

    public boolean isAnimatedSun() {
        return animatedSun;
    }

    public boolean isAnimatedMoon() {
        return animatedMoon;
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

    public float getDayLengthSec() {
        return dayLengthSec;
    }

    public boolean isBloomEnabled() {
        return bloomEnabled;
    }

    public float getSkyExposure() {
        return skyExposure;
    }

    public float getBloomIntensity() {
        return bloomIntensity;
    }

    public float getBloomExposure() {
        return bloomExposure;
    }

    public float getDayExposure() {
        return dayExposure;
    }

    public float getNightExposure() {
        return nightExposure;
    }

    public boolean isStarsEnabled() {
        return starsEnabled;
    }

    public float getVerticalAngle() {
        return verticalAngle;
    }
}