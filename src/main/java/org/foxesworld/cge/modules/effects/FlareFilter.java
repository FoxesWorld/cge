package org.foxesworld.cge.modules.effects;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

/**
 * FlareFilter — пост-обработочный фильтр для создания эффекта линзовых бликов (lens flare).
 * Реализует простую имитацию flare вокруг ярких источников света (например, солнца).
 *
 * Требует custom материал: "MatDefs/FlareFilter.j3md"
 */
public class FlareFilter extends Filter {

    private float flareIntensity = 1.0f;
    private float flareThreshold = 1.0f;
    private ColorRGBA flareColor = ColorRGBA.White.clone();

    public FlareFilter() {
        super("FlareFilter");
    }

    @Override
    protected Material getMaterial() {
        return material;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        material = new Material(manager, "MatDefs/FlareFilter.j3md");
        material.setFloat("FlareIntensity", flareIntensity);
        material.setFloat("FlareThreshold", flareThreshold);
        material.setColor("FlareColor", flareColor);
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
        this.flareColor = color;
        if (material != null) {
            material.setColor("FlareColor", color);
        }
    }

    public ColorRGBA getFlareColor() {
        return flareColor;
    }
}