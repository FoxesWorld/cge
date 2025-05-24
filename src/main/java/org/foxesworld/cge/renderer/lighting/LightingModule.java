package org.foxesworld.cge.renderer.lighting;


import com.jme3.light.Light;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import com.jme3.app.Application;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class LightingModule extends EngineModule<LightingConfig> {
    private static final Logger log = LogManager.getLogger(LightingModule.class);
    private static final String CONFIG_FILE = "lighting_config";
    private final List<Light> extraLights = new ArrayList<>();

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
        var root =  app.getRootNode();
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
    }

    @Override
    protected void cleanupModule(Application app) {
        var root = ((com.jme3.app.SimpleApplication) app).getRootNode();
        root.getLocalLightList().clear();
    }

    public void addLight(Spatial target, float radius, float intensity) {
        PointLight light = new PointLight();
        light.setColor(ColorRGBA.Blue);
        light.setRadius(radius);

        Vector3f pos = target.getWorldTranslation();
        light.setPosition(pos);
        // Добавляем и запоминаем
        gameEngine.getRootNode().addLight(light);
        extraLights.add(light);
        logger.info("Added PointLight to {} @ {} (radius={}, intensity={})",
                target.getName(), pos, radius, intensity);
    }
}
