package org.foxesworld.cge.renderer.skyBox;

import com.jme3.app.Application;
import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import com.jme3.util.SkyFactory;
import jme3utilities.sky.SkyControl;
import jme3utilities.sky.StarsOption;
import jme3utilities.sky.Updater;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.renderer.RendererModule;

import java.time.LocalTime;

public class SkyBox extends EngineModule<SkyBoxConfig> {
    private CalistaGameEngine calistaGameEngine;
    private SkyControl skyControl;
    public SkyBox(RendererModule rendererModule) {
        super("skybox", SkyBoxConfig.class, rendererModule.getGameEngine());
        this.calistaGameEngine = rendererModule.getGameEngine();
    }

    @Override
    protected void onConfigReloaded() throws Exception {

    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        calistaGameEngine.enqueue(() -> {
            Spatial sky = SkyFactory.createSky(calistaGameEngine.getAssetManager(),
                    calistaGameEngine.getTexture(getConfig().getSkyBoxTexture()),
                    SkyFactory.EnvMapType.valueOf(getConfig().getEnvMap()));
            sky.setShadowMode(RenderQueue.ShadowMode.valueOf(getConfig().getShadowMode()));
            skyControl = new SkyControl(calistaGameEngine.getAssetManager(), calistaGameEngine.getCamera(),
                    getConfig().getCloudFlattering(),
                    StarsOption.valueOf(getConfig().getStarsOption()),
                    getConfig().isBottomDome());
            calistaGameEngine.getRootNode().addControl(skyControl);
            skyControl.setCloudiness(0.8f);
            skyControl.setCloudsYOffset(0.4f);
            skyControl.setTopVerticalAngle(1.78f);
            Updater updater = skyControl.getUpdater();
            updater.setAmbientLight(new AmbientLight(ColorRGBA.DarkGray));
            //updater.setMainLight(sun);
            //updater.addShadowRenderer(dlsr);
            skyControl.setEnabled(true);
        });
    }

    @Override
    public void update(float tpf) {
        LocalTime currentTime = LocalTime.now();
        float hour = currentTime.getHour() + currentTime.getMinute() / 60f;
        skyControl.getSunAndStars().setHour(hour);
    }

    @Override
    protected void updateModule(float tpf) throws Exception {
        System.out.println(tpf);
    }

    @Override
    protected void cleanupModule(Application app) throws Exception {

    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }
}