package org.foxesworld.cge.modules.renderer.skyBox;

import com.jme3.app.Application;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
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
 * Improved: dynamic switching of shadow-casting light (sun or moon), smooth shadow blending.
 */
public class SkyBox extends EngineModule<SkyBoxConfig> {

    private final CalistaGameEngine engine;
    private SkyControl skyControl;
    private DirectionalLight sunLight;
    private DirectionalLight moonLight;
    private AmbientLight ambient;
    private DirectionalLightShadowRenderer shadowRenderer;
    private Updater updater;

    private final Vector3f tmpDir = new Vector3f();

    private float simulatedHour = 12.0f;
    private float smoothingSpeed = 0.1f;
    private float moonFade = 0.5f;

    // Track which light is currently active for shadow casting
    private DirectionalLight activeShadowLight = null;
    private boolean shadowsWithSun = true; // true=sun, false=moon

    public SkyBox(RendererModule rendererModule) {
        super("skybox", SkyBoxConfig.class, rendererModule.getGameEngine());
        this.engine = rendererModule.getGameEngine();
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        engine.enqueue(() -> {
            setupLighting();
            setupSkyDome();
            setupShadows();
        });
    }

    private void setupSkyDome() {
        Spatial sky = SkyFactory.createSky(
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

        engine.getRootNode().attachChild(sky);
    }

    private void setupLighting() {
        // Atmospheric ambient: глубокий голубой с мягким теплом заката
        ColorRGBA ambientColor = new ColorRGBA(0.18f, 0.25f, 0.36f, 1.0f).multLocal(0.7f)
                .addLocal(new ColorRGBA(0.8f, 0.6f, 0.45f, 1.0f).multLocal(0.15f));
        ambient = new AmbientLight(ambientColor);

        sunLight = new DirectionalLight();
        sunLight.setColor(ColorRGBA.White.mult(getConfig().getSunLightIntensity()));
        sunLight.setDirection(new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal());
        engine.getRootNode().addLight(sunLight);

        moonLight = new DirectionalLight();
        moonLight.setColor(new ColorRGBA(0.32f, 0.34f, 0.45f, 1f).mult(getConfig().getMoonLightIntensity() * moonFade));
        moonLight.setDirection(new Vector3f(0.5f, -1f, 0.5f).normalizeLocal());
        engine.getRootNode().addLight(moonLight);

        engine.getRootNode().addLight(ambient);
    }

    private void setupShadows() {
        int size = getConfig().getShadowMapSize();
        int splits = getConfig().getShadowFrustumCount();

        shadowRenderer = new DirectionalLightShadowRenderer(engine.getAssetManager(), size, splits);
        shadowRenderer.setShadowZExtend(getConfig().getShadowZExtend());
        shadowRenderer.setLambda(0.60f); // Soft, smooth shadows

        // Initially set to sun
        shadowRenderer.setLight(sunLight);
        activeShadowLight = sunLight;
        shadowsWithSun = true;

        //ViewPort vp = viewPort();
        gameEngine.getViewPort().addProcessor(shadowRenderer);
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
            simulatedHour += (targetHour - simulatedHour) * smoothingSpeed * tpf;

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

            // Atmosphere: плавный рассвет/закат, холодная ночь
            float sunIntensity = Math.max(0.13f, Math.min(1f, sunDot + 0.13f));
            float moonIntensity = Math.max(0f, 1f - sunIntensity);

            sunLight.setColor(ColorRGBA.White.mult(getConfig().getSunLightIntensity() * sunIntensity));
            moonLight.setColor(new ColorRGBA(0.36f, 0.39f, 0.55f, 1f).mult(getConfig().getMoonLightIntensity() * moonIntensity * moonFade));

            // Ambient color transitions
            float ambientBlend = 0.18f + 0.82f * sunIntensity;
            ColorRGBA ambientDay = new ColorRGBA(0.18f, 0.25f, 0.36f, 1.0f).mult(ambientBlend)
                    .add(new ColorRGBA(0.8f, 0.6f, 0.45f, 1.0f).mult(1f - ambientBlend));
            ambient.setColor(ambientDay);

            // --- SHADOW CASTING LIGHT SWITCH ---
            DirectionalLight requiredLight = shadowsWithSun ? sunLight : moonLight;
            if (activeShadowLight != requiredLight && shadowRenderer != null) {
                shadowRenderer.setLight(requiredLight);
                activeShadowLight = requiredLight;
            }

            // Optionally, allow smooth transition (blend) between sun and moon shadows if desired.
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
    protected void onConfigReloaded() {
        // Support dynamic reconfiguration if needed
    }

    private ViewPort viewPort() {
        return engine.getRenderManager().getMainView("Default");
    }
}