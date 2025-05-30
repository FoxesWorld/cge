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

@SuppressWarnings("unused")
public class PostProcessingModule extends EngineModule<PostProcessingConfig> {
    private BloomFilter bloomFilter;
    private LightScatteringFilter lsf;
    private DepthOfFieldFilter dof;
    private FXAAFilter fxaaFilter;
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
        log.info("PostProcessingConfig reloaded: {}", getConfig());
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        fpp = new FilterPostProcessor(app.getAssetManager());
        int numSamples = app.getContext().getSettings().getSamples();
        if (numSamples > 0) fpp.setNumSamples(numSamples);
        if (getConfig().isEnableBloom()) {
            bloomFilter = new BloomFilter();
            bloomFilter.setBloomIntensity(getConfig().getBloomIntensity());
            bloomFilter.setExposurePower(getConfig().getBloomExposurePower());
            fpp.addFilter(bloomFilter);
        }
        if(getConfig().isEnableLsf()) {
            lsf = new LightScatteringFilter(new Vector3f(getConfig().getLsfLightDir()[0], getConfig().getLsfLightDir()[1], getConfig().getLsfLightDir()[2]));
            lsf.setLightDensity(getConfig().getLsfDestiny());
            this.fpp.addFilter(lsf);
        }
        if(getConfig().isEnableDof()) {
            dof = new DepthOfFieldFilter();
            dof.setFocusDistance(getConfig().getDofFocus());
            dof.setFocusRange(getConfig().getDofRange());
        }
        if (getConfig().isEnableFXAA()) {
            fxaaFilter = new FXAAFilter();
            fpp.addFilter(fxaaFilter);
        }

        app.getViewPort().addProcessor(fpp);
    }

    @Override
    protected void updateModule(float tpf) {

    }

    @Override
    protected void cleanupModule(Application app) {
        if (fpp != null) {
            app.getViewPort().removeProcessor(fpp);
        }
    }

    public BloomFilter getBloomFilter() {
        return bloomFilter;
    }

    public LightScatteringFilter getLsf() {
        return lsf;
    }

    public DepthOfFieldFilter getDof() {
        return dof;
    }

    public FXAAFilter getFxaaFilter() {
        return fxaaFilter;
    }

    public FilterPostProcessor getFpp() {
        return fpp;
    }
}