package org.foxesworld.cge.renderer.skyBox;

public class SkyBoxConfig {
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
