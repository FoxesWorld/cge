package org.foxesworld.cge.modules.effects;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

/**
 * FlareFilter — пост-обработочный фильтр для создания эффекта линзовых бликов (lens flare).
 * Реализует простой flare вокруг ярких источников света (например, солнца).
 *
 * Требует custom-материал: "MatDefs/FlareFilter.j3md"
 */
public class FlareFilter extends Filter {

    private float flareIntensity = 1.2f;   // Усилен по умолчанию для большего эффекта
    private float flareThreshold = 0.85f;  // Порог — чем ниже, тем больше flare
    private ColorRGBA flareColor = ColorRGBA.White.clone();

    private transient boolean initialized = false;

    public FlareFilter() {
        super("FlareFilter");
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        material = new Material(manager, "assets/Post/FlareFilter.j3md");
        material.setFloat("FlareIntensity", flareIntensity);
        material.setFloat("FlareThreshold", flareThreshold);
        material.setColor("FlareColor", flareColor);
        initialized = true;
    }

    @Override
    protected Material getMaterial() {
        return material;
    }

    // --- Параметры ---

    public void setFlareIntensity(float intensity) {
        this.flareIntensity = intensity;
        if (material != null) {
            material.setFloat("FlareIntensity", intensity);
        }
    }

    public float getFlareIntensity() {
        return flareIntensity;
    }

    public void setFlareThreshold(float threshold) {
        this.flareThreshold = threshold;
        if (material != null) {
            material.setFloat("FlareThreshold", threshold);
        }
    }

    public float getFlareThreshold() {
        return flareThreshold;
    }

    public void setFlareColor(ColorRGBA color) {
        if (color == null) return;
        this.flareColor = color.clone();
        if (material != null) {
            material.setColor("FlareColor", this.flareColor);
        }
    }

    public ColorRGBA getFlareColor() {
        return flareColor;
    }

    /**
     * Быстрый пресет для "солнечного" flare.
     */
    public void applySunPreset() {
        setFlareIntensity(1.6f);
        setFlareThreshold(0.7f);
        setFlareColor(new ColorRGBA(1.0f, 0.93f, 0.75f, 1.0f));
    }

    /**
     * Быстрый пресет для "холодного" flare.
     */
    public void applyCoolPreset() {
        setFlareIntensity(1.2f);
        setFlareThreshold(0.92f);
        setFlareColor(new ColorRGBA(0.7f, 0.85f, 1.0f, 1.0f));
    }

    @Override
    public boolean isRequiresDepthTexture() {
        return false;
    }
}