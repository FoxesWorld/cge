package org.foxesworld.cge.modules.renderer.postProcessing;

import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.DepthOfFieldFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.ToneMapFilter;
import com.jme3.post.filters.LightScatteringFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.scene.Node;
import com.jme3.shadow.CompareMode;
import com.jme3.shadow.DirectionalLightShadowFilter;
//import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.effects.*;
import org.foxesworld.cge.modules.player.PlayerModule;
import org.foxesworld.cge.modules.renderer.RendererModule;
import org.foxesworld.cge.modules.renderer.skyBox.SkyBox;

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

    private SkyBox skyBox;
    Vector3f sunDirection;
    private FilterPostProcessor fpp;
    private BloomFilter bloom;
    private SSAOFilter ssaoFilter;
    private LightScatteringFilter lsf;
    private BetterDepthOfFieldFilter dof;
    private FXAAFilter fxaa;
    private MotionBlurFilter motionBlur;
    private VignetteFilter vignette;
    private ToneMapFilter toneMap;
    private BLFFilter flare;
    private DirectionalLightShadowFilter dlsf;

    public PostProcessingModule(RendererModule rendererModule) {
        super(PostProcessingModule.class, PostProcessingConfig.class, rendererModule.getGameEngine());
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        app.getAssetLoader().onAssetsLoaded(() -> {
            cleanupFilters(app);
            skyBox = app.getModuleManager().getModule(RendererModule.class).getSkyBox();
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
                //fpp.addFilter(bloom);
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
            if (getConfig().getDlsf().isEnable()) {
                dlsf = new DirectionalLightShadowFilter(app.getAssetManager(), getConfig().getDlsf().getShadowMapSize(), getConfig().getDlsf().getNbSplits());

                dlsf.setRenderBackFacesShadows(getConfig().getDlsf().isRenderBackFacesShadows());           // Мягкие края теней, меньше артефактов на тонких объектах
                dlsf.setEnabledStabilization(getConfig().getDlsf().isStabilization());             // Стабилизация теней при движении камеры (минимум дрожания)
                dlsf.setShadowIntensity(getConfig().getDlsf().getShadowIntensity());                 // Более мягкие тени, киношный эффект
                dlsf.setEdgeFilteringMode(EdgeFilteringMode.valueOf(getConfig().getDlsf().getEdgeFilteringMode())); // Улучшено сглаживание границ теней
                dlsf.setShadowCompareMode(CompareMode.valueOf(getConfig().getDlsf().getShadowCompareMode()));   // Аппаратное сравнение теней для лучшей производительности

                dlsf.setLight(skyBox.getSunLight());
                fpp.addFilter(dlsf);
            }

            // DOF
            if (cfg.getDof().isEnable()) {
                dof = new BetterDepthOfFieldFilter();//new DepthOfFieldFilter();
                //dof.setFocusDistance(cfg.getDof().getFocus());
                //dof.setFocusRange(cfg.getDof().getRange());
                //dof.setBlurScale(cfg.getDof().getMaxBlur());
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
        });
    }

    public float getDistance3D(Vector3f originPoint,Vector3f targetPoint )
    {
        float v0 = originPoint.x - targetPoint.x;
        float v1 = originPoint.y - targetPoint.y;
        float v2 = originPoint.z - targetPoint.z;
        return (float)Math.sqrt(v0*v0 + v1*v1 + v2*v2);
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
        Application app = getGameEngine();
        if (fpp != null && app.getViewPort().getProcessors().contains(fpp)) {
            app.getViewPort().removeProcessor(fpp);
        }
        if (fpp != null) {
            fpp.cleanup();
            fpp = null;
        }

        bloom = null;
        ssaoFilter = null;
        lsf = null;
        dof = null;
        fxaa = null;
        motionBlur = null;
        vignette = null;
        toneMap = null;
        flare = null;
        dlsf = null;
    }


    @Override
    public void onConfigReloaded() {
        log.info("PostProcessingConfig reloaded: {}", getConfig());
        initModule(gameEngine);
    }

    @Override
    public void update(float tpf) {
        if(skyBox != null) {
            sunDirection = skyBox.getSunLight().getDirection();
            Vector2f sunScreenPos2D = new Vector2f(
                    sunDirection.x / getApplication().getCamera().getWidth(),
                    sunDirection.y / getApplication().getCamera().getHeight()
            );
            if(flare != null) {
                flare.setEnabled(true);
                //flare.setLightPosition(sunScreenPos2D);
                //flare.setBloomTexture(bloom.getBloomTexture());
            }
        }
    }

    @Override
    protected void updateModule(float tpf) {


    }

    public FilterPostProcessor getFpp() {
        return fpp;
    }

    public BloomFilter getBloom() {
        return bloom;
    }

    public SSAOFilter getSsaoFilter() {
        return ssaoFilter;
    }

    public LightScatteringFilter getLsf() {
        return lsf;
    }

    public BetterDepthOfFieldFilter getDof() {
        return dof;
    }

    public FXAAFilter getFxaa() {
        return fxaa;
    }

    public MotionBlurFilter getMotionBlur() {
        return motionBlur;
    }

    public VignetteFilter getVignette() {
        return vignette;
    }

    public ToneMapFilter getToneMap() {
        return toneMap;
    }

    public DirectionalLightShadowFilter getDlsf() {
        return dlsf;
    }

    @Override
    protected void cleanupModule(Application app) {
        cleanupFilters(app);
    }
}