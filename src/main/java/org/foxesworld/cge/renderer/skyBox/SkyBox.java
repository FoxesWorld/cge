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

/**
 * SkyBox simulates a dynamic sky environment including sun, moon, clouds, stars, and real-time lighting.
 * Powered by SkyControl, it bridges visual fidelity and realism.
 */
public class SkyBox extends EngineModule<SkyBoxConfig> {

    private final CalistaGameEngine engine;
    private SkyControl skyControl;
    private DirectionalLight sunLight;
    private DirectionalLight moonLight;
    private DirectionalLightShadowRenderer shadows;

    private final Vector3f tmpDir = new Vector3f();

    private float simulatedHour = 12.0f;
    private float smoothingSpeed = 0.1f;
    private AmbientLight gi;
    private Updater updater;

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

        Updater updater = skyControl.getUpdater();
        updater.setAmbientLight(createAmbientLight());
        updater.setMainLight(sunLight);

        skyControl.setCloudiness(getConfig().getCloudiness());
        skyControl.setCloudsYOffset(getConfig().getCloudYOffset());
        skyControl.setTopVerticalAngle(getConfig().getVerticalAngle());

        skyControl.setEnabled(true);
    }

    private void setupLighting() {
        gi = new AmbientLight(ColorRGBA.DarkGray);
        sunLight = new DirectionalLight();
        sunLight.setColor(ColorRGBA.White.mult(getConfig().getSunLightIntensity()));
        engine.getRootNode().addLight(sunLight);

        moonLight = new DirectionalLight();
        moonLight.setColor(new ColorRGBA(0.5f, 0.5f, 0.8f, 1f).mult(getConfig().getMoonLightIntensity()));
        engine.getRootNode().addLight(moonLight);
    }

    private AmbientLight createAmbientLight() {
        ColorRGBA ambient = ColorRGBA.Gray.mult(0.6f).add(ColorRGBA.White.mult(0.4f));
        return new AmbientLight(ambient);
    }

    private void setupShadows() {
        shadows = new DirectionalLightShadowRenderer(
                engine.getAssetManager(),
                getConfig().getShadowMapSize(),
                getConfig().getShadowFrustumCount()
        );
        shadows.setLight(sunLight);
        shadows.setShadowZExtend(getConfig().getShadowZExtend());
        viewPort().addProcessor(shadows);
    }

    private float getCurrentHour() {
        LocalTime now = LocalTime.now();
        return now.getHour() + now.getMinute() / 60f + now.getSecond() / 3600f;
    }

    @Override
    public void update(float tpf) {
        if(getState() == ModuleState.RUNNING) {
            updater = skyControl.getUpdater();
            float targetHour = getCurrentHour();

            // Плавно интерполируем simulatedHour к targetHour
            simulatedHour += (targetHour - simulatedHour) * smoothingSpeed * tpf;

            //updater.setAmbientLight(gi);
            updater.setMainLight(this.sunLight);
            //updater.addShadowRenderer(shadows);
            skyControl.getSunAndStars().setHour(simulatedHour);

            Vector3f sunDirection = skyControl.getSunAndStars().sunDirection(tmpDir);
            sunLight.setDirection(sunDirection.negate());
            moonLight.setDirection(sunDirection);

            if (shadows != null) {
                shadows.setLight(sunLight);
            }
        }
    }

    @Override
    protected void updateModule(float tpf) {
        // reserved for atmospheric effects, weather systems, etc.
    }

    @Override
    protected void cleanupModule(Application app) {
        if (skyControl != null) skyControl.setEnabled(false);
        if (sunLight != null) engine.getRootNode().removeLight(sunLight);
        if (moonLight != null) engine.getRootNode().removeLight(moonLight);
        if (shadows != null) viewPort().removeProcessor(shadows);
    }

    @Override
    protected void onEnable() {
        if (skyControl != null) skyControl.setEnabled(true);
        if (shadows != null) viewPort().addProcessor(shadows);
    }

    @Override
    protected void onDisable() {
        if (skyControl != null) skyControl.setEnabled(false);
        if (shadows != null) viewPort().removeProcessor(shadows);
    }

    @Override
    protected void onConfigReloaded() {
        // Support dynamic reconfiguration if needed
    }

    private ViewPort viewPort() {
        return engine.getRenderManager().getMainView("Default");
    }
}
