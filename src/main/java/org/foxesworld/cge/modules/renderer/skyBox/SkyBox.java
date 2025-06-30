package org.foxesworld.cge.modules.renderer.skyBox;

import com.jme3.app.Application;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
//import com.jme3.shadow.EdgeFilteringMode;
import jme3utilities.sky.SkyControl;
import jme3utilities.sky.StarsOption;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.renderer.CinematicPipeline;
import org.foxesworld.cge.modules.renderer.RendererModule;

import java.time.LocalTime;

/**
 * AAA-уровень системы управления атмосферой.
 * Управляет динамическим небом, освещением, тенями (PSSM) и пост-эффектами
 * для создания целостной и кинематографичной картины.
 */
public class SkyBox extends EngineModule<SkyBoxConfig> {

    private final CalistaGameEngine engine;
    private SkyControl skyControl;
    private DirectionalLight sunLight;
    private DirectionalLight moonLight;
    private AmbientLight ambientLight;
    private DirectionalLightShadowRenderer pssmShadowRenderer;
    private CinematicPipeline cinematicPipeline;

    private float simulationHour = 12.0f;
    private final float timeSmoothingFactor = 0.1f;

    private static final ColorRGBA SUN_COLOR_ZENITH = ColorRGBA.White.clone();
    private static final ColorRGBA SUN_COLOR_HORIZON = new ColorRGBA(1.0f, 0.6f, 0.4f, 1.0f);
    private static final ColorRGBA AMBIENT_DAY = new ColorRGBA(0.4f, 0.5f, 0.7f, 1.0f);
    private static final ColorRGBA AMBIENT_NIGHT = new ColorRGBA(0.08f, 0.12f, 0.2f, 1.0f);
    private static final ColorRGBA MOON_COLOR = new ColorRGBA(0.7f, 0.8f, 1.0f, 1.0f);

    public SkyBox(RendererModule rendererModule) {
        super(SkyBox.class, SkyBoxConfig.class, rendererModule.getGameEngine());
        this.engine = rendererModule.getGameEngine();
    }


    @Override
    public void onConfigReloaded() throws Exception {

    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        engine.enqueue(() -> {
            initSkyControl();
            initLights();
            initShadows();
            //initPostProcessing();
        });
    }

    private void initSkyControl() {
        skyControl = new SkyControl(
                engine.getAssetManager(),
                engine.getCamera(),
                getConfig().getCloudFlattering(),
                StarsOption.valueOf(getConfig().getStarsOption()),
                getConfig().isBottomDome()
        );
        engine.getRootNode().addControl(skyControl);
        skyControl.getUpdater().setMainLight(sunLight);
        skyControl.getUpdater().setAmbientLight(ambientLight);
        skyControl.setCloudiness(getConfig().getCloudiness());
        skyControl.setEnabled(true);
    }

    private void initLights() {
        sunLight = new DirectionalLight();
        sunLight.setColor(SUN_COLOR_ZENITH.mult(getConfig().getSunLightIntensity()));
        engine.getRootNode().addLight(sunLight);

        moonLight = new DirectionalLight();
        moonLight.setColor(MOON_COLOR.mult(getConfig().getMoonLightIntensity()));
        engine.getRootNode().addLight(moonLight);

        ambientLight = new AmbientLight(AMBIENT_DAY);
        engine.getRootNode().addLight(ambientLight);
    }

    private void initShadows() {
        pssmShadowRenderer = new DirectionalLightShadowRenderer(
                engine.getAssetManager(),
                getConfig().getShadowMapSize(),
                getConfig().getShadowFrustumCount()
        );
        pssmShadowRenderer.setLight(sunLight);
        pssmShadowRenderer.setShadowIntensity(0.6f);
        pssmShadowRenderer.setLambda(0.65f);
        pssmShadowRenderer.setShadowZExtend(getConfig().getShadowZExtend());
        //pssmShadowRenderer.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON);
        viewPort().addProcessor(pssmShadowRenderer);
    }

    private void initPostProcessing() {
        cinematicPipeline = new CinematicPipeline(engine);
        cinematicPipeline.initialize(sunLight);
    }

    @Override
    protected void updateModule(float tpf) {
        if (skyControl == null) return;

        updateTime(tpf);
        AtmosphereState state = calculateAtmosphereState();
        updateSkyControl(state);
        updateLighting(state);
        updateShadows(state);
        updatePostProcessing(state, tpf);
    }

    private void updateTime(float tpf) {
        float targetHour = getSystemHour(); //: getConfig().getFixedHour();
        float hourDelta = targetHour - simulationHour;
        if (hourDelta > 12f) hourDelta -= 24f;
        if (hourDelta < -12f) hourDelta += 24f;
        simulationHour += hourDelta * timeSmoothingFactor * tpf;
        simulationHour = (simulationHour + 24f) % 24f;
    }

    private AtmosphereState calculateAtmosphereState() {
        Vector3f sunDir = skyControl.getSunAndStars().sunDirection(null);
        float sunElevation = sunDir.dot(Vector3f.UNIT_Y);
        return new AtmosphereState(sunDir, sunElevation);
    }

    private void updateSkyControl(AtmosphereState state) {
        skyControl.getSunAndStars().setHour(simulationHour);
    }

    private void updateLighting(AtmosphereState state) {
        sunLight.setDirection(state.sunDirection.negate());
        moonLight.setDirection(state.sunDirection);

        float sunIntensity = FastMath.saturate(state.sunElevation * 10f);
        float moonIntensity = FastMath.saturate(-state.sunElevation * 5f);

        ColorRGBA sunColor = new ColorRGBA().interpolateLocal(SUN_COLOR_HORIZON, SUN_COLOR_ZENITH, FastMath.saturate(state.sunElevation * 2f));
        sunLight.setColor(sunColor.mult(getConfig().getSunLightIntensity() * sunIntensity));
        moonLight.setColor(MOON_COLOR.mult(getConfig().getMoonLightIntensity() * moonIntensity));

        ColorRGBA ambientColor = new ColorRGBA().interpolateLocal(AMBIENT_DAY, AMBIENT_NIGHT, 1f - FastMath.saturate(state.sunElevation * 4f + 0.5f));
        ambientLight.setColor(ambientColor);
    }

    private void updateShadows(AtmosphereState state) {
        pssmShadowRenderer.setShadowIntensity(0.6f * FastMath.saturate(state.sunElevation * 15f));
    }

    private void updatePostProcessing(AtmosphereState state, float tpf) {
        if(cinematicPipeline != null) {
            cinematicPipeline.update(tpf);
        }
    }

    private record AtmosphereState(Vector3f sunDirection, float sunElevation) {}

    private float getSystemHour() {
        LocalTime now = LocalTime.now();
        return now.getHour() + now.getMinute() / 60f + now.getSecond() / 3600f;
    }

    @Override
    protected void cleanupModule(Application app) {
        engine.enqueue(() -> {
            if (skyControl != null) engine.getRootNode().removeControl(skyControl);
            if (sunLight != null) engine.getRootNode().removeLight(sunLight);
            if (moonLight != null) engine.getRootNode().removeLight(moonLight);
            if (ambientLight != null) engine.getRootNode().removeLight(ambientLight);
            if (pssmShadowRenderer != null) viewPort().removeProcessor(pssmShadowRenderer);
            if (cinematicPipeline != null) cinematicPipeline.cleanup();
        });
    }

    @Override
    protected void onEnable() {
        if (skyControl != null) skyControl.setEnabled(true);
        if (pssmShadowRenderer != null) viewPort().addProcessor(pssmShadowRenderer);
        if (cinematicPipeline != null) cinematicPipeline.setEnabled(true);
    }

    @Override
    protected void onDisable() {
        if (skyControl != null) skyControl.setEnabled(false);
        if (pssmShadowRenderer != null) viewPort().removeProcessor(pssmShadowRenderer);
        if (cinematicPipeline != null) cinematicPipeline.setEnabled(false);
    }

    public ViewPort viewPort() {
        return engine.getViewPort();
    }

    public DirectionalLight getSunLight() {
        return sunLight;
    }

    public DirectionalLight getMoonLight() {
        return moonLight;
    }
}