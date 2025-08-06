package org.foxesworld.cge.modules.effects;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

public class ChromaticAberrationFilter extends Filter {

    private float strength = 0.005f;
    private Vector2f resolution = new Vector2f(1280, 720); // по умолчанию

    public ChromaticAberrationFilter() {
        super("ChromaticAberrationFilter");
    }

    public ChromaticAberrationFilter(float strength) {
        this();
        this.strength = strength;
    }

    public void setStrength(float strength) {
        this.strength = strength;
        if (material != null) {
            material.setFloat("Strength", strength);
        }
    }

    public void setResolution(float width, float height) {
        this.resolution.set(width, height);
        if (material != null) {
            material.setVector2("Resolution", resolution);
        }
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        material = new Material(manager, "assets/MatDefs/Post/ChromaticAberration.j3md");
        setResolution(w, h);
        setStrength(strength);
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
