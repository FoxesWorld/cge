package org.foxesworld.cge.renderer.postProcessing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import com.jme3.app.Application;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import org.foxesworld.cge.renderer.RendererModule;

public class PostProcessingModule extends EngineModule<PostProcessingConfig> {
    private static final Logger log = LogManager.getLogger(PostProcessingModule.class);
    private static final String CONFIG_FILE = "postprocessing_config";

    private FilterPostProcessor fpp;

    public PostProcessingModule(CalistaGameEngine calistaGameEngine) {
        super(CONFIG_FILE, PostProcessingConfig.class, calistaGameEngine);
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }

    @Override
    protected void onConfigReloaded() {
        log.info("PostProcessingConfig reloaded: {}", config);
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        fpp = new FilterPostProcessor(app.getAssetManager());
        if (config.enableBloom) {
            BloomFilter bloom = new BloomFilter();
            bloom.setBloomIntensity(config.bloomIntensity);
            fpp.addFilter(bloom);
        }
        if (config.enableFXAA) {
            fpp.addFilter(new FXAAFilter());
        }
        app.getViewPort().addProcessor(fpp);
    }

    @Override
    protected void updateModule(float tpf) {
        // можно динамически менять интенсивность
    }

    @Override
    protected void cleanupModule(Application app) {
        if (fpp != null) {
            app.getViewPort().removeProcessor(fpp);
        }
    }
}