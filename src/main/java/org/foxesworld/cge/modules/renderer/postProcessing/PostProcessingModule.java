package org.foxesworld.cge.modules.renderer.postProcessing;

import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.DepthOfFieldFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.ToneMapFilter;
import com.jme3.post.filters.LightScatteringFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.shadow.CompareMode;
import com.jme3.shadow.DirectionalLightShadowFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.effects.MotionBlurFilter;
import org.foxesworld.cge.modules.effects.VignetteFilter;
import org.foxesworld.cge.modules.effects.FlareFilter;

import static com.jme3.shadow.PssmShadowRenderer.FilterMode.PCF8;

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
    private FlareFilter flare;

    public PostProcessingModule(CalistaGameEngine engine) {
        super(CONFIG_FILE, PostProcessingConfig.class, engine);
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        fpp = app.getFilterPostProcessor();
        int samples = app.getContext().getSettings().getSamples();
        if (samples > 0) {
            fpp.setNumSamples(samples);
        }

        PostProcessingConfig cfg = getConfig();

        if (cfg.getBloom().isEnable()) {
            bloom = new BloomFilter();
            bloom.setBloomIntensity(cfg.getBloom().getIntensity());
            bloom.setExposurePower(cfg.getBloom().getExposurePower());
            bloom.setBlurScale(cfg.getBloom().getRadius());
            bloom.setDownSamplingFactor(cfg.getBloom().getThreshold());
            fpp.addFilter(bloom);
        }

        if(cfg.getSsaOfilter().isEnable()) {
            SSAOFilter ssaoFilter = new SSAOFilter(cfg.getSsaOfilter().getSampleRadius(), cfg.getSsaOfilter().getIntensity(), cfg.getSsaOfilter().getScale(), cfg.getSsaOfilter().getBias());
            fpp.addFilter(ssaoFilter);
        }

        if (cfg.getLsf().isEnable()) {
            float[] lightDir = cfg.getLsf().getLightDir();
            lsf = new LightScatteringFilter(new Vector3f(lightDir[0], lightDir[1], lightDir[2]));
            lsf.setLightDensity(cfg.getLsf().getDestiny());
            lsf.setNbSamples(cfg.getLsf().getGhostCount());
            fpp.addFilter(lsf);
        }

        DirectionalLightShadowFilter dlsf=new DirectionalLightShadowFilter(gameEngine.getAssetManager(),1024,2);
        fpp.addFilter(dlsf);
        dlsf.setRenderBackFacesShadows(false);
        dlsf.setEnabledStabilization(false);
        dlsf.setShadowIntensity(0.6f);
        dlsf.setShadowCompareMode(CompareMode.Hardware);


        if (cfg.getDof().isEnable()) {
            dof = new DepthOfFieldFilter();
            dof.setFocusDistance(cfg.getDof().getFocus());
            dof.setFocusRange(cfg.getDof().getRange());
            //dof.setAperture(cfg.getDofAperture());
            dof.setBlurScale(cfg.getDof().getMaxBlur());
            fpp.addFilter(dof);
        }



        if (cfg.getColorGrading().isEnable()) {
            toneMap=new ToneMapFilter(new Vector3f(cfg.getColorGrading().getContrast(),cfg.getColorGrading().getExposure(),cfg.getColorGrading().getSaturation()).mult(0.7f));
            //toneMap.setExposure(cfg.getExposure());
            //toneMap.setGamma(cfg.getContrast());
            //toneMap.setTonemapper(ToneMapFilter.FilmicToneMap.GALACTIC);
            fpp.addFilter(toneMap);
        }

        if (cfg.getFxaa().isEnable()) {
            fxaa = new FXAAFilter();
            fxaa.setSpanMax(cfg.getFxaa().getQuality());
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

    public FilterPostProcessor getFpp() {
        return fpp;
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
    public FlareFilter getFlare() { return flare; }
}