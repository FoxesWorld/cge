package org.foxesworld.cge.renderer.postProcessing;

import com.jme3.math.Vector3f;
import com.jme3.post.filters.DepthOfFieldFilter;
import com.jme3.post.filters.LightScatteringFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import com.jme3.app.Application;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;

public class PostProcessingModule extends EngineModule<PostProcessingConfig> {
    private static final Logger log = LogManager.getLogger(PostProcessingModule.class);
    private static final String CONFIG_FILE = "postprocessing_config";
    private FilterPostProcessor fpp;

    public PostProcessingModule(CalistaGameEngine calistaGameEngine) {
        super(CONFIG_FILE, PostProcessingConfig.class, calistaGameEngine);
        initialize(calistaGameEngine);
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
        int numSamples = app.getContext().getSettings().getSamples();
        if (numSamples > 0) fpp.setNumSamples(numSamples);
        if (config.enableBloom) {
            BloomFilter bloom = new BloomFilter();
            bloom.setBloomIntensity(config.bloomIntensity);
            bloom.setExposurePower(60);
            fpp.addFilter(bloom);
        }
        if(config.enableLsf) {
            LightScatteringFilter lsf = new LightScatteringFilter(new Vector3f(config.lsfLightDir[0], config.lsfLightDir[1], config.lsfLightDir[2]));
            lsf.setLightDensity(config.lsfDestiny);
            this.fpp.addFilter(lsf);
        }
        if(config.enableDof) {
            DepthOfFieldFilter dof = new DepthOfFieldFilter();
            dof.setFocusDistance(config.dofFocus);
            dof.setFocusRange(config.dofRange);
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