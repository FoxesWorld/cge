package org.foxesworld.cge.renderer.skyBox;

import com.jme3.math.ColorRGBA;
import org.foxesworld.cge.core.module.ModuleConfig;

public class SkyBoxConfig extends ModuleConfig {
    private String skyBoxTexture = "cubemap_0";
    /*
    * Options
    * Off,
    * Cast,
    * Receive,
    * CastAndReceive,
    * Inherit
    * */
    private String shadowMode = "CastAndReceive";
    /*
    * Options
    *     Cube,
    * TopDome,
    * TwoDomes;
    * */
    private String envMap = "CubeMap";
    /* Options
    * CubeMap,
    * SphereMap,
    * EquirectMap
    * */
    private String starsOption = "TopDome";
    private float cloudFlattering = .5f;
    private boolean bottomDome = true;
    private float cloudiness = 0.8f;
    private float cloudsYOffset = 0.4f;
    private float topVerticalAngle = 1.78f;
    private ColorRGBA sunColor = ColorRGBA.White;
    private ColorRGBA moonColor = new ColorRGBA(0.4f, 0.4f, 0.6f, 1f);
    private ColorRGBA ambientColor = ColorRGBA.DarkGray;

    public float getCloudiness() {
        return cloudiness;
    }

    public float getCloudsYOffset() {
        return cloudsYOffset;
    }

    public float getTopVerticalAngle() {
        return topVerticalAngle;
    }

    public ColorRGBA getSunColor() {
        return sunColor;
    }

    public ColorRGBA getMoonColor() {
        return moonColor;
    }

    public ColorRGBA getAmbientColor() {
        return ambientColor;
    }

    public String getSkyBoxTexture() {
        return skyBoxTexture;
    }

    public float getCloudFlattering() {
        return cloudFlattering;
    }

    public String getShadowMode() {
        return shadowMode;
    }

    public String getStarsOption() {
        return starsOption;
    }

    public boolean isBottomDome() {
        return bottomDome;
    }

    public String getEnvMap() {
        return envMap;
    }
}
