package org.foxesworld.cge.renderer.skyBox;

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
import org.foxesworld.cge.renderer.RendererModule;

import java.time.LocalTime;

public class SkyBox extends EngineModule<SkyBoxConfig> {

    private final CalistaGameEngine engine;
    private final Vector3f tempSunDir = new Vector3f();
    private final ViewPort mainViewPort;

    private SkyControl skyControl;
    private DirectionalLight sunLight;
    private DirectionalLight moonLight;
    private DirectionalLightShadowRenderer shadowRenderer;

    public SkyBox(RendererModule rendererModule) {
        super("skybox", SkyBoxConfig.class, rendererModule.getGameEngine());
        this.engine = rendererModule.getGameEngine();
        this.mainViewPort = engine.getRenderManager().getMainView("Default");
    }

    @Override
    protected void onConfigReloaded() {
        // TODO: Update sky settings dynamically, if necessary
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        engine.enqueue(this::initializeSkyBox);
    }

    private void initializeSkyBox() {
        Spatial sky = SkyFactory.createSky(
                engine.getAssetManager(),
                engine.getTexture(getConfig().getSkyBoxTexture()),
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

        skyControl.setCloudiness(0.8f);
        skyControl.setCloudsYOffset(0.4f);
        skyControl.setTopVerticalAngle(1.78f);
        engine.getRootNode().addControl(skyControl);

        setupLighting();
        setupShadows();

        skyControl.setEnabled(true);
    }

    private void setupLighting() {
        // Ambient Light
        ColorRGBA ambientColor = ColorRGBA.DarkGray.mult(0.7f).add(ColorRGBA.LightGray.mult(0.3f));
        AmbientLight ambientLight = new AmbientLight(ambientColor);
        engine.getRootNode().addLight(ambientLight);

        // Sun
        sunLight = new DirectionalLight();
        sunLight.setColor(ColorRGBA.White.mult(getConfig().getSunLightIntensity()));
        engine.getRootNode().addLight(sunLight);

        // Moon
        moonLight = new DirectionalLight();
        moonLight.setColor(new ColorRGBA(0.6f, 0.6f, 0.8f, 1f).mult(getConfig().getMoonLightIntensity()));
        engine.getRootNode().addLight(moonLight);

        // Bind to SkyControl
        skyControl.getUpdater().setMainLight(sunLight);
        skyControl.getUpdater().setAmbientLight(ambientLight);
    }

    private void setupShadows() {
        shadowRenderer = new DirectionalLightShadowRenderer(
                engine.getAssetManager(),
                getConfig().getShadowMapSize(),
                getConfig().getShadowFrustumCount()
        );
        shadowRenderer.setLight(sunLight);
        shadowRenderer.setShadowZExtend(getConfig().getShadowZExtend());
        mainViewPort.addProcessor(shadowRenderer);
    }

    @Override
    public void update(float tpf) {
        if (getState() != ModuleState.RUNNING || skyControl == null) return;

        LocalTime now = LocalTime.now();
        float hour = now.getHour() + now.getMinute() / 60f;

        skyControl.getSunAndStars().setHour(hour);

        // Получаем нормализованное направление солнца
        Vector3f sunDir = skyControl.getSunAndStars().sunDirection(tempSunDir).normalizeLocal();
        sunLight.setDirection(sunDir.negate());

        // Луна противоположна солнцу
        moonLight.setDirection(sunDir);

        // Изменение интенсивности света в зависимости от положения солнца
        float elevation = sunDir.y; // Высота солнца над горизонтом
        float sunIntensity = clamp(elevation, 0f, 1f) * getConfig().getSunLightIntensity();
        float moonIntensity = (1f - clamp(elevation, 0f, 1f)) * getConfig().getMoonLightIntensity();

        sunLight.setColor(ColorRGBA.White.mult(sunIntensity));
        moonLight.setColor(new ColorRGBA(0.6f, 0.6f, 0.8f, 1f).mult(moonIntensity));

        // Обновляем тень только при необходимости (можно закешировать предыдущее значение)
        if (shadowRenderer != null) {
            shadowRenderer.setLight(sunLight);
        }
    }
    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void updateModule(float tpf) {
        // Optional: add your own logic here
    }

    @Override
    protected void cleanupModule(Application app) {
        if (skyControl != null) skyControl.setEnabled(false);
        if (sunLight != null) engine.getRootNode().removeLight(sunLight);
        if (moonLight != null) engine.getRootNode().removeLight(moonLight);
        if (shadowRenderer != null) mainViewPort.removeProcessor(shadowRenderer);
    }

    @Override
    protected void onEnable() {
        if (skyControl != null) skyControl.setEnabled(true);
        if (shadowRenderer != null && !mainViewPort.getProcessors().contains(shadowRenderer)) {
            mainViewPort.addProcessor(shadowRenderer);
        }
    }

    @Override
    protected void onDisable() {
        if (skyControl != null) skyControl.setEnabled(false);
        if (shadowRenderer != null) mainViewPort.removeProcessor(shadowRenderer);
    }

    // Getters

    public DirectionalLight getSunLight() {
        return sunLight;
    }

    public DirectionalLight getMoonLight() {
        return moonLight;
    }

    public SkyControl getSkyControl() {
        return skyControl;
    }

    /**
     * Возвращает текущий главный источник света (солнце или луна)
     * в зависимости от времени суток.
     */
    public DirectionalLight getCurrentLight() {
        if (skyControl == null) return sunLight; // по умолчанию

        float hour = LocalTime.now().getHour() + LocalTime.now().getMinute() / 60f;
        skyControl.getSunAndStars().setHour(hour);

        // Получаем высоту солнца
        Vector3f sunDir = skyControl.getSunAndStars().sunDirection(new Vector3f()).normalizeLocal();
        float elevation = sunDir.y;

        // Если солнце выше горизонта — это дневной свет
        return elevation > 0f ? sunLight : moonLight;
    }


    public DirectionalLightShadowRenderer getShadowRenderer() {
        return shadowRenderer;
    }
}