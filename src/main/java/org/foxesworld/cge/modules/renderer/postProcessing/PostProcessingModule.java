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

/**
 * Улучшенный модуль пост-обработки с полной поддержкой "горячей" перезагрузки конфига:
 * - Без утечек и дублирования фильтров.
 * - Корректная зачистка и пересоздание фильтров по onConfigReloaded().
 * - Расширяемость для новых фильтров.
 * - Возможность динамического обновления (updateModule).
 */
@SuppressWarnings("unused")
public class PostProcessingModule extends EngineModule<PostProcessingConfig> {
    private static final Logger log = LogManager.getLogger(PostProcessingModule.class);

    private FilterPostProcessor fpp;
    private BloomFilter bloom;
    private SSAOFilter ssaoFilter;
    private LightScatteringFilter lsf;
    private DepthOfFieldFilter dof;
    private FXAAFilter fxaa;
    private MotionBlurFilter motionBlur;
    private VignetteFilter vignette;
    private ToneMapFilter toneMap;
    private FlareFilter flare;
    private DirectionalLightShadowFilter dlsf;

    public PostProcessingModule(CalistaGameEngine engine) {
        super(PostProcessingModule.class, PostProcessingConfig.class, engine);
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        createFilters(app);
    }

    /**
     * Создание всех фильтров согласно конфигу.
     * Гарантирует отсутствие повторных присоединений и утечек.
     */
    private void createFilters(CalistaGameEngine app) {
        cleanupFilters(app);

        fpp = new FilterPostProcessor(app.getAssetManager());
        int samples = app.getContext().getSettings().getSamples();
        if (samples > 0) {
            fpp.setNumSamples(samples);
        }

        PostProcessingConfig cfg = getConfig();

        // BLOOM
        if (cfg.getBloom().isEnable()) {
            bloom = new BloomFilter();
            bloom.setBloomIntensity(cfg.getBloom().getIntensity());
            bloom.setExposurePower(cfg.getBloom().getExposurePower());
            bloom.setBlurScale(cfg.getBloom().getRadius());
            bloom.setDownSamplingFactor(cfg.getBloom().getThreshold());
            fpp.addFilter(bloom);
        } else {
            bloom = null;
        }

        // SSAO
        if (cfg.getSsaOfilter().isEnable()) {
            ssaoFilter = new SSAOFilter(
                    cfg.getSsaOfilter().getSampleRadius(),
                    cfg.getSsaOfilter().getIntensity(),
                    cfg.getSsaOfilter().getScale(),
                    cfg.getSsaOfilter().getBias()
            );
            fpp.addFilter(ssaoFilter);
        } else {
            ssaoFilter = null;
        }

        // LIGHT SCATTERING
        if (cfg.getLsf().isEnable()) {
            float[] lightDir = cfg.getLsf().getLightDir();
            lsf = new LightScatteringFilter(new Vector3f(lightDir[0], lightDir[1], lightDir[2]));
            lsf.setLightDensity(cfg.getLsf().getDestiny());
            lsf.setNbSamples(cfg.getLsf().getGhostCount());
            fpp.addFilter(lsf);
        } else {
            lsf = null;
        }

        // SHADOWS
        dlsf = new DirectionalLightShadowFilter(app.getAssetManager(), 1024, 2);
        dlsf.setRenderBackFacesShadows(false);
        dlsf.setEnabledStabilization(false);
        dlsf.setShadowIntensity(0.6f);
        dlsf.setShadowCompareMode(CompareMode.Hardware);
        fpp.addFilter(dlsf);

        // DOF
        if (cfg.getDof().isEnable()) {
            dof = new DepthOfFieldFilter();
            dof.setFocusDistance(cfg.getDof().getFocus());
            dof.setFocusRange(cfg.getDof().getRange());
            dof.setBlurScale(cfg.getDof().getMaxBlur());
            fpp.addFilter(dof);
        } else {
            dof = null;
        }

        // COLOR GRADING (Tone Mapping)
        if (cfg.getColorGrading().isEnable()) {
            toneMap = new ToneMapFilter(new Vector3f(
                    cfg.getColorGrading().getContrast(),
                    cfg.getColorGrading().getExposure(),
                    cfg.getColorGrading().getSaturation()
            ).mult(0.7f));
            fpp.addFilter(toneMap);
        } else {
            toneMap = null;
        }

        // FXAA
        if (cfg.getFxaa().isEnable()) {
            fxaa = new FXAAFilter();
            fxaa.setSpanMax(cfg.getFxaa().getQuality());
            fpp.addFilter(fxaa);
        } else {
            fxaa = null;
        }
        app.getViewPort().addProcessor(fpp);
    }

    /**
     * Гарантированная зачистка фильтров и процессоров.
     */
    private void cleanupFilters(Application app) {
        if (fpp != null) {
            app.getViewPort().removeProcessor(fpp);
            fpp.cleanup();
            fpp = null;
        }
        bloom = null;
        lsf = null;
        dof = null;
        fxaa = null;
        ssaoFilter = null;
        motionBlur = null;
        vignette = null;
        toneMap = null;
        flare = null;
        dlsf = null;
    }

    @Override
    protected void onEnable() {
        if (fpp != null && !getGameEngine().getViewPort().getProcessors().contains(fpp)) {
            getGameEngine().getViewPort().addProcessor(fpp);
        }
    }

    @Override
    protected void onDisable() {
        if (fpp != null) {
            getGameEngine().getViewPort().removeProcessor(fpp);
        }
    }

    @Override
    public void onConfigReloaded() {
        log.info("PostProcessingConfig reloaded: {}", getConfig());
        createFilters(getGameEngine());
    }

    @Override
    protected void updateModule(float tpf) {
        // Здесь можно делать динамическое обновление параметров фильтров по tpf, если это требуется.
    }

    public FilterPostProcessor getFpp() { return fpp; }
    public BloomFilter getBloom() { return bloom; }
    public SSAOFilter getSsaoFilter() { return ssaoFilter; }
    public LightScatteringFilter getLsf() { return lsf; }
    public DepthOfFieldFilter getDof() { return dof; }
    public FXAAFilter getFxaa() { return fxaa; }
    public MotionBlurFilter getMotionBlur() { return motionBlur; }
    public VignetteFilter getVignette() { return vignette; }
    public ToneMapFilter getToneMap() { return toneMap; }
    public FlareFilter getFlare() { return flare; }
    public DirectionalLightShadowFilter getDlsf() { return dlsf; }

    @Override
    protected void cleanupModule(Application app) {
        cleanupFilters(app);
    }
}