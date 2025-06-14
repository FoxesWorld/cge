package org.foxesworld.cge.effects;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.math.Vector2f;
import com.jme3.renderer.ViewPort;

public class VignetteFilter extends Filter {
    private float radius = 0.75f, softness = 0.5f, strength = 0.5f;
    private Material material;

    public VignetteFilter() {
        super("VignetteFilter");
    }

    @Override
    protected void initFilter(AssetManager assetManager, RenderManager rm, ViewPort vp, int w, int h) {
        material = new Material(assetManager, "MatDefs/Post/Vignette.j3md");
        material.setVector2("Resolution", new Vector2f(w, h));
        material.setFloat("Radius", radius);
        material.setFloat("Softness", softness);
        material.setFloat("Strength", strength);
    }

    @Override
    protected Material getMaterial() {
        return material;
    }

    public void setRadius(float r) { radius = r; }
    public void setSoftness(float s) { softness = s; }
    public void setStrength(float s) { strength = s; }

    public void setVignetteIntensity(float value) {
        strength = value;
        if (material != null) {
            material.setFloat("Strength", strength);
        }
    }

    public void setVignetteFade(float value) {
        softness = value;
        if (material != null) {
            material.setFloat("Softness", softness);
        }
    }
}