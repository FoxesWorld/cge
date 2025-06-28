package org.foxesworld.cge.modules.renderer.skyBox;

import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FogFilter;
import com.jme3.post.filters.LightScatteringFilter;
import org.foxesworld.cge.CalistaGameEngine;

public class SunEffects {

    private final CalistaGameEngine engine;
    private FilterPostProcessor fpp;

    public SunEffects(CalistaGameEngine engine) {
        this.engine = engine;
    }

    public void initFilters(SkyBox skyBox) {
        // Создаём контейнер пост‑обработки
        fpp = skyBox.getGameEngine().getFilterPostProcessor();

        // 1) BloomFilter для мягкого свечения
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.SceneAndObjects);
        bloom.setDownSamplingFactor(2);       // разрешение свечения
        bloom.setExposureCutOff(0.5f);         // порог свечения
        bloom.setExposurePower(2f);            // интенсивность свечения
        fpp.addFilter(bloom);

        // 2) LightScatteringFilter («Божественные лучи»)
        LightScatteringFilter shafts = new LightScatteringFilter(skyBox.getSunLight().getDirection().negate());
        shafts.setLightDensity(0.8f);          // плотность лучей
        //shafts.setDecay(0.95f);                // скорость затухания
        //shafts.setWeight(0.5f);                // яркость
        //shafts.setExposure(0.3f);              // общая экспозиция эффекта
        fpp.addFilter(shafts);

        // 3) FogFilter для объёмного тумана
        FogFilter fog = new FogFilter();
        fog.setFogColor(new com.jme3.math.ColorRGBA(0.7f, 0.8f, 1f, 1f)); // цвет тумана
        fog.setFogDistance(300);               // где начинается туман
        fog.setFogDensity(0.02f);              // плотность
        fpp.addFilter(fog);

        // Добавляем пост‑процессинг в основной вьюпорт
        skyBox.viewPort().addProcessor(fpp);
    }
}
