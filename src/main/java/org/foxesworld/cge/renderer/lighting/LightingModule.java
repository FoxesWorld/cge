package org.foxesworld.cge.renderer.lighting;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import com.jme3.app.Application;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.Vector3f;
import org.foxesworld.cge.renderer.RendererModule;

public class LightingModule extends EngineModule<LightingConfig> {
    private static final Logger log = LogManager.getLogger(LightingModule.class);
    private static final String CONFIG_FILE = "lighting_config";

    public LightingModule(CalistaGameEngine calistaGameEngine) {
        super(CONFIG_FILE, LightingConfig.class, calistaGameEngine);
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }

    @Override
    protected void onConfigReloaded() {
        log.info("LightingConfig reloaded: {}", config);
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        var root = ((com.jme3.app.SimpleApplication) app).getRootNode();
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(config.ambientColor);
        root.addLight(ambient);

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(
                config.sunDirection[0],
                config.sunDirection[1],
                config.sunDirection[2]
        ).normalizeLocal());
        sun.setColor(config.sunColor);
        root.addLight(sun);
    }

    @Override
    protected void updateModule(float tpf) {
        // динамика освещения при необходимости
    }

    @Override
    protected void cleanupModule(Application app) {
        var root = ((com.jme3.app.SimpleApplication) app).getRootNode();
        root.getLocalLightList().clear();
    }
}
