package org.foxesworld.cge.modules.renderer.postProcessing;

import com.jme3.app.Application;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.DepthOfFieldFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.ToneMapFilter;
import com.jme3.post.filters.LightScatteringFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.effects.MotionBlurFilter;
import org.foxesworld.cge.modules.effects.VignetteFilter;

@SuppressWarnings("unused")
public class PostProcessingModule extends EngineModule<PostProcessingConfig> {
    private static final Logger log = LogManager.getLogger(PostProcessingModule.class);
    private static final String CONFIG_FILE = "postprocessing_config";

    private FilterPostProcessor fpp;
    private BloomFilter bloom;
    private LightScatteringFilter lsf;
    private DepthOfFieldFilter dof;
    private FXAAFilter fxaa;
    private MotionBlurFilter motionBlur;
    private VignetteFilter vignette;
    private ToneMapFilter toneMap;

    public PostProcessingModule(CalistaGameEngine engine) {
        super(CONFIG_FILE, PostProcessingConfig.class, engine);
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        fpp = new FilterPostProcessor(app.getAssetManager());
        int samples = app.getContext().getSettings().getSamples();
        if (samples > 0) {
            fpp.setNumSamples(samples);
        }

        PostProcessingConfig cfg = getConfig();

        if (cfg.isEnableBloom()) {
            bloom = new BloomFilter();
            bloom.setBloomIntensity(cfg.getBloomIntensity());
            bloom.setExposurePower(cfg.getBloomExposurePower());
            bloom.setBlurScale(cfg.getBloomRadius());
            bloom.setDownSamplingFactor(cfg.getBloomThreshold());
            fpp.addFilter(bloom);
        }

        if (cfg.isEnableLsf()) {
            Vector3f dir = new Vector3f(
                    cfg.getLsfLightDir()[0],
                    cfg.getLsfLightDir()[1],
                    cfg.getLsfLightDir()[2]
            );
            lsf = new LightScatteringFilter(dir);
            lsf.setLightDensity(cfg.getLsfDestiny());
            lsf.setNbSamples(cfg.getLsfGhostCount());
            //lsf.setDecay(cfg.getLsfHaloWidth());
            fpp.addFilter(lsf);
        }

        if (cfg.isEnableDof()) {
            dof = new DepthOfFieldFilter();
            dof.setFocusDistance(cfg.getDofFocus());
            dof.setFocusRange(cfg.getDofRange());
            //dof.setAperture(cfg.getDofAperture());
            dof.setBlurScale(cfg.getDofMaxBlur());
            fpp.addFilter(dof);
        }

        if (cfg.isEnableMotionBlur()) {
            //motionBlur = new MotionBlurFilter();
            //motionBlur.setSampleCount(cfg.getMotionBlurSampleCount());
            //motionBlur.setExposureLength(cfg.getMotionBlurStrength());
            //fpp.addFilter(motionBlur);
        }

        if (cfg.isEnableVignette()) {
            vignette = new VignetteFilter();
            vignette.setVignetteIntensity(cfg.getVignetteIntensity());
            vignette.setVignetteFade(cfg.getVignetteSmoothness());
            //fpp.addFilter(vignette);
        }

        if (cfg.isEnableColorGrading()) {
            toneMap = new ToneMapFilter();
            //toneMap.setExposure(cfg.getExposure());
            //toneMap.setGamma(cfg.getContrast());
            //toneMap.setTonemapper(ToneMapFilter.FilmicToneMap.GALACTIC);
            //fpp.addFilter(toneMap);
        }

        if (cfg.isEnableFXAA()) {
            fxaa = new FXAAFilter();
            fxaa.setSpanMax(cfg.getFxaaQuality());
            fpp.addFilter(fxaa);
        }

        app.getViewPort().addProcessor(fpp);
    }

    @Override
    protected void onEnable() {
        // Filters automatically active when added
    }

    @Override
    protected void onDisable() {
        if (fpp != null) {
            getGameEngine().getViewPort().removeProcessor(fpp);
        }
    }

    @Override
    protected void onConfigReloaded() {
        log.info("PostProcessingConfig reloaded: {}", getConfig());
        // Could reinitialize filters here
    }

    @Override
    protected void updateModule(float tpf) {
        // Dynamic updates if needed
    }

    @Override
    protected void cleanupModule(Application app) {
        if (fpp != null) {
            app.getViewPort().removeProcessor(fpp);
        }
    }

    // Getters
    public BloomFilter getBloom() { return bloom; }
    public LightScatteringFilter getLsf() { return lsf; }
    public DepthOfFieldFilter getDof() { return dof; }
    public FXAAFilter getFxaa() { return fxaa; }
    public MotionBlurFilter getMotionBlur() { return motionBlur; }
    public VignetteFilter getVignette() { return vignette; }
    public ToneMapFilter getToneMap() { return toneMap; }
}
