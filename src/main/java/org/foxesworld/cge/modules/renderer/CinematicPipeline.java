package org.foxesworld.cge.modules.renderer;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FogFilter;
import com.jme3.post.filters.LightScatteringFilter;
import com.jme3.post.filters.ToneMapFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.ViewPort;
import com.jme3.texture.Texture2D;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.effects.ColorGradingFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Управляет конвейером пост-обработки для создания кинематографичной картины.
 * Является исполнительным механизмом для AtmosphericSky.
 */
public class CinematicPipeline {

    private final CalistaGameEngine engine;
    private final AssetManager assetManager;
    private final ViewPort viewPort;
    private final List<Filter> managedFilters = new ArrayList<>();

    private FilterPostProcessor fpp;
    private DirectionalLight sun;
    private boolean initialized = false;

    private final ColorRGBA dayFogColor = new ColorRGBA(0.7f, 0.8f, 1.0f, 1.0f);
    private final ColorRGBA sunsetFogColor = new ColorRGBA(0.9f, 0.6f, 0.4f, 1.0f);
    private final float godRaysDayIntensity = 0.5f;
    private final float godRaysSunsetIntensity = 1.0f;

    public CinematicPipeline(CalistaGameEngine engine) {
        this.engine = engine;
        this.assetManager = engine.getAssetManager();
        this.viewPort = engine.getViewPort();
    }

    public void initialize(DirectionalLight sun) {
        if (initialized) return;
        this.sun = sun;

        fpp = engine.getFilterPostProcessor();
        if (fpp == null) {
            fpp = new FilterPostProcessor(assetManager);
            viewPort.addProcessor(fpp);
            //engine.setFilterPostProcessor(fpp);
        }

        initFilters();

        initialized = true;
        setEnabled(true);
    }

    private void initFilters() {
        // Создаем и добавляем фильтры в правильном порядке
        addFilter(new SSAOFilter(12.0f, 25.0f, 0.4f, 0.6f));

        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
        bloom.setExposurePower(3.0f);
        bloom.setBloomIntensity(1.5f);
        bloom.setDownSamplingFactor(1.5f);
        addFilter(bloom);

        LightScatteringFilter godRays = new LightScatteringFilter(sun.getDirection().negate());
        godRays.setLightDensity(0.8f);
        godRays.setBlurStart(1.0f);
        godRays.setBlurWidth(0.8f);
        addFilter(godRays);

        addFilter(new FogFilter(dayFogColor, 1.0f, 300f));
        addFilter(new ToneMapFilter(new Vector3f(-1.0f, -10.0f, -0.1f)));

        Texture2D lut = (Texture2D) assetManager.loadTexture("assets/Textures/color_2d.png");
        addFilter(new ColorGradingFilter(lut, 16f));
    }

    private <T extends Filter> T addFilter(T filter) {
        managedFilters.add(filter);
        fpp.addFilter(filter);
        return filter;
    }

    public void update(float tpf) {
        if (!initialized) return;

        Vector3f sunDir = sun.getDirection();
        float sunElevation = sunDir.y;
        float sunsetFactor = 1.0f - FastMath.saturate(sunElevation * 2.0f + 0.5f);

        getFilter(LightScatteringFilter.class).setLightPosition(sunDir.negate());
        getFilter(LightScatteringFilter.class).setLightDensity(
                FastMath.interpolateLinear(sunsetFactor, godRaysDayIntensity, godRaysSunsetIntensity)
        );
        getFilter(FogFilter.class).getFogColor().interpolateLocal(dayFogColor, sunsetFogColor, sunsetFactor);
    }

    public void setEnabled(boolean enabled) {
        if (!initialized) return;
        managedFilters.forEach(f -> f.setEnabled(enabled));
    }

    public void cleanup() {
        if (!initialized || fpp == null) return;
        managedFilters.forEach(fpp::removeFilter);
        managedFilters.clear();
        initialized = false;
    }

    @SuppressWarnings("unchecked")
    public <T extends Filter> T getFilter(Class<T> filterClass) {
        for (Filter filter : managedFilters) {
            if (filterClass.isInstance(filter)) {
                return (T) filter;
            }
        }
        return null;
    }
}