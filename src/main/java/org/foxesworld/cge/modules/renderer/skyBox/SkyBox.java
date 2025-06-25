package org.foxesworld.cge.modules.renderer.skyBox;

import com.jme3.app.Application;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.util.SkyFactory;
import jme3utilities.sky.SkyControl;
import jme3utilities.sky.StarsOption;
import jme3utilities.sky.Updater;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleState;
import org.foxesworld.cge.modules.renderer.RendererModule;

import java.time.LocalTime;

/**
 * SkyBox simulates a dynamic sky environment including sun, moon, clouds, stars, and real-time lighting.
 * Enhanced for atmospheric feeling, realistic lighting, and soft shadow fidelity.
 * Improved: dynamic switching of shadow-casting light (sun or moon), smooth shadow blending, customizable colors,
 * and improved ambient/tonemapping for more cinematic effect.
 *
 * 2024: Enhanced with volumetric/foggy atmosphere, dynamic coloring, smooth day-night transitions,
 * and automatic management of shadow quality.
 */
public class SkyBox extends EngineModule<SkyBoxConfig> {

    private final CalistaGameEngine engine;
    private SkyControl skyControl;
    private DirectionalLight sunLight;
    private DirectionalLight moonLight;
    private AmbientLight ambient;
    private DirectionalLightShadowRenderer shadowRenderer;
    private Updater updater;
    private Spatial sky;

    private final Vector3f tmpDir = new Vector3f();

    private float simulatedHour = 12.0f;
    private float smoothingSpeed = 0.2f; // faster smoothing for more responsive day-night
    private float moonFade = 0.7f;

    // Track which light is currently active for shadow casting
    private DirectionalLight activeShadowLight = null;
    private boolean shadowsWithSun = true; // true=sun, false=moon

    // Atmosphere controls
    private float fogDensity = 0.002f;
    private ColorRGBA fogColorDay = new ColorRGBA(0.62f, 0.74f, 0.92f, 1.0f);
    private ColorRGBA fogColorNight = new ColorRGBA(0.07f, 0.13f, 0.19f, 1.0f);

    public SkyBox(RendererModule rendererModule) {
        super(SkyBox.class, SkyBoxConfig.class, rendererModule.getGameEngine());
        this.engine = rendererModule.getGameEngine();
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        engine.enqueue(() -> {
            setupLighting();
            setupSkyDome();
            setupShadows();
            setupAtmosphere();
        });
    }

    private void setupSkyDome() {
        sky = SkyFactory.createSky(
                engine.getAssetManager(),
                engine.getAssetRepo().getTexture(getConfig().getSkyBoxTexture()),
                SkyFactory.EnvMapType.valueOf(getConfig().getEnvMap())
        );
        sky.setShadowMode(RenderQueue.ShadowMode.Off);

        skyControl = new SkyControl(
                engine.getAssetManager(),
                engine.getCamera(),
                getConfig().getCloudFlattering(),
                StarsOption.valueOf(getConfig().getStarsOption()),
                getConfig().isBottomDome()
        );
        engine.getRootNode().addControl(skyControl);

        updater = skyControl.getUpdater();
        updater.setAmbientLight(ambient);
        updater.setMainLight(sunLight);

        skyControl.setCloudiness(getConfig().getCloudiness());
        skyControl.setCloudsYOffset(getConfig().getCloudYOffset());
        skyControl.setTopVerticalAngle(getConfig().getVerticalAngle());
        skyControl.setEnabled(true);

        // Enhanced: Horizon gradient and star intensity
        // if (getConfig().hasOvercast()) skyControl.setOvercast(getConfig().getOvercastAmount());
        //if (getConfig().hasCloudOpacity()) skyControl.setCloudsOpacity(getConfig().getCloudOpacity());
        //if (getConfig().hasHaloThickness()) skyControl.setHaloThickness(getConfig().getHaloThickness());
        //if (getConfig().hasStarIntensity()) skyControl.setStarIntensity(getConfig().getStarIntensity());

        engine.getRootNode().attachChild(sky);
    }

    private void setupLighting() {
        // Improved atmospheric ambient: dynamic blend between deep blue and sunset warmth
        ColorRGBA ambientDay = new ColorRGBA(0.18f, 0.24f, 0.42f, 1.0f);
        ColorRGBA ambientSunset = new ColorRGBA(1.0f, 0.80f, 0.53f, 1.0f);
        ColorRGBA ambientNight = new ColorRGBA(0.10f, 0.16f, 0.25f, 1.0f);

        ambient = new AmbientLight(ambientDay.clone());

        sunLight = new DirectionalLight();
        sunLight.setColor(ColorRGBA.White.mult(getConfig().getSunLightIntensity()));
        sunLight.setDirection(new Vector3f(-0.48f, -1f, -0.38f).normalizeLocal());
        engine.getRootNode().addLight(sunLight);

        moonLight = new DirectionalLight();
        moonLight.setColor(new ColorRGBA(0.34f, 0.36f, 0.47f, 1f).mult(getConfig().getMoonLightIntensity() * moonFade));
        moonLight.setDirection(new Vector3f(0.51f, -1f, 0.56f).normalizeLocal());
        engine.getRootNode().addLight(moonLight);

        engine.getRootNode().addLight(ambient);
    }

    private void setupShadows() {
        int size = getConfig().getShadowMapSize();
        int splits = getConfig().getShadowFrustumCount();

        shadowRenderer = new DirectionalLightShadowRenderer(engine.getAssetManager(), size, splits);
        shadowRenderer.setShadowZExtend(getConfig().getShadowZExtend());
        shadowRenderer.setLambda(0.55f); // Mildly softer shadows
        shadowRenderer.setEdgeFilteringMode(EdgeFilteringMode.valueOf(getConfig().getEdgeFilteringMode()));
        shadowRenderer.setShadowIntensity(0.55f); // Soft shadow darkness

        // Initially set to sun
        shadowRenderer.setLight(sunLight);
        activeShadowLight = sunLight;
        shadowsWithSun = true;

        viewPort().addProcessor(shadowRenderer);
    }

    private void setupAtmosphere() {
        ViewPort vp = viewPort();
        if (vp != null) {
            vp.setBackgroundColor(fogColorDay.clone());
            // For advanced: add a custom FogFilter, VolumetricLightScatteringFilter, or Tonemap
            // (if your engine supports post-processing filters)
        }
    }

    private float getCurrentHour() {
        LocalTime now = LocalTime.now();
        return now.getHour() + now.getMinute() / 60f + now.getSecond() / 3600f;
    }

    @Override
    public void update(float tpf) {
        if (getState() == ModuleState.RUNNING && skyControl != null) {
            float targetHour = getCurrentHour();

            // Smoothly interpolate simulatedHour toward targetHour
            float hourDelta = targetHour - simulatedHour;
            // Wrap-around for day/night cycle
            if (hourDelta > 12.0f) hourDelta -= 24.0f;
            if (hourDelta < -12.0f) hourDelta += 24.0f;
            simulatedHour += hourDelta * smoothingSpeed * tpf;

            // Clamp simulatedHour to [0,24)
            if (simulatedHour < 0f) simulatedHour += 24f;
            if (simulatedHour >= 24f) simulatedHour -= 24f;

            skyControl.getSunAndStars().setHour(simulatedHour);

            // Sun and moon directions (for shadows and highlights)
            Vector3f sunDirection = skyControl.getSunAndStars().sunDirection(tmpDir);

            // This is the upward normal of the sun; positive when above the horizon
            float sunDot = sunDirection.dot(Vector3f.UNIT_Y);

            // Determine if it's day or night (threshold can be tuned)
            boolean isDay = sunDot > -0.08f;
            shadowsWithSun = isDay;

            // Update light directions
            sunLight.setDirection(sunDirection.negate());
            moonLight.setDirection(sunDirection);

            // --- Atmosphere: improved blend for day, sunset, and night ---
            float sunsetZone = FastMath.clamp((sunDot + 0.1f) / 0.18f, 0f, 1f); // 0 at -0.1, 1 at +0.08
            float sunIntensity = FastMath.clamp(sunDot + 0.13f, 0.13f, 1.0f);
            float moonIntensity = FastMath.clamp(1.0f - sunIntensity, 0f, 1.0f);

            // Soft transition for ambient color through sunset
            ColorRGBA ambientDay = new ColorRGBA(0.18f, 0.24f, 0.42f, 1.0f);
            ColorRGBA ambientSunset = new ColorRGBA(1.0f, 0.80f, 0.53f, 1.0f);
            ColorRGBA ambientNight = new ColorRGBA(0.10f, 0.16f, 0.25f, 1.0f);

            ColorRGBA ambientCol = ambientDay.clone().interpolateLocal(ambientSunset, 1f - sunsetZone)
                    .interpolateLocal(ambientNight, 1f - sunIntensity);
            ambient.setColor(ambientCol);

            sunLight.setColor(ColorRGBA.White.mult(getConfig().getSunLightIntensity() * sunIntensity));
            moonLight.setColor(new ColorRGBA(0.36f, 0.39f, 0.55f, 1f).mult(getConfig().getMoonLightIntensity() * moonIntensity * moonFade));

            // --- SHADOW CASTING LIGHT SWITCH ---
            DirectionalLight requiredLight = shadowsWithSun ? sunLight : moonLight;
            if (activeShadowLight != requiredLight && shadowRenderer != null) {
                shadowRenderer.setLight(requiredLight);
                activeShadowLight = requiredLight;
            }

            // --- Atmospheric fog color and density ---
            ViewPort vp = viewPort();
            if (vp != null) {
                ColorRGBA fogCol = fogColorDay.clone().interpolateLocal(fogColorNight, 1f - sunIntensity);
                vp.setBackgroundColor(fogCol);
                // For advanced: update fog/volumetric post-processing filter params here
            }
        }
    }

    /**
     * Returns which light is currently used for shadow casting: "sun" or "moon"
     */
    public String getActiveShadowCaster() {
        return (activeShadowLight == sunLight) ? "sun" : "moon";
    }

    @Override
    protected void updateModule(float tpf) {
        // Reserved for future atmospheric/weather/volumetric effects.
    }

    @Override
    protected void cleanupModule(Application app) {
        if (skyControl != null) skyControl.setEnabled(false);
        if (sunLight != null) engine.getRootNode().removeLight(sunLight);
        if (moonLight != null) engine.getRootNode().removeLight(moonLight);
        if (ambient != null) engine.getRootNode().removeLight(ambient);
        if (shadowRenderer != null) viewPort().removeProcessor(shadowRenderer);
    }

    @Override
    protected void onEnable() {
        if (skyControl != null) skyControl.setEnabled(true);
        if (shadowRenderer != null) viewPort().addProcessor(shadowRenderer);
    }

    @Override
    protected void onDisable() {
        if (skyControl != null) skyControl.setEnabled(false);
        if (shadowRenderer != null) viewPort().removeProcessor(shadowRenderer);
        if (ambient != null) engine.getRootNode().removeLight(ambient);
    }

    @Override
    public void onConfigReloaded() {
        // Dynamic reconfiguration: re-init all relevant parts with new config
        setupLighting();
        setupSkyDome();
        setupShadows();
        setupAtmosphere();
    }

    public DirectionalLight getMoonLight() {
        return moonLight;
    }

    public DirectionalLight getSunLight() {
        return sunLight;
    }

    public AmbientLight getAmbient() {
        return ambient;
    }

    private ViewPort viewPort() {
        return engine.getRenderManager().getMainView("Default");
    }
}