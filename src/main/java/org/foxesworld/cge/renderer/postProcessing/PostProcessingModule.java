package org.foxesworld.cge.renderer.postProcessing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.EngineModule;
import com.jme3.app.Application;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;

public class PostProcessingModule extends EngineModule<PostProcessingConfig> {
    private static final Logger log = LogManager.getLogger(PostProcessingModule.class);
    private static final String CONFIG_FILE = "postprocessing_config.json";

    private FilterPostProcessor fpp;

    public PostProcessingModule() {
        super(CONFIG_FILE, PostProcessingConfig.class);
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
    protected void initModule(Application app) {
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