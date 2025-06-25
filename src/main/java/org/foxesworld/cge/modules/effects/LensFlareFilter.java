package org.foxesworld.cge.modules.effects;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.texture.Texture2D;
import com.jme3.shader.VarType;

/**
 * AAA-style cinematic Lens Flare Filter.
 */
public class LensFlareFilter extends Filter {
    private Texture2D dirtTex;
    private Texture2D bloomTex;
    private Vector2f lightPos = new Vector2f(0.5f, 0.5f);
    private Vector2f resolution = new Vector2f(1280, 720);
    private transient AssetManager assetManager;

    public LensFlareFilter(AssetManager assetManager, Texture2D dirt, Vector2f screenSize) {
        super("LensFlare");
        this.assetManager = assetManager;
        this.dirtTex = dirt;
        this.resolution = screenSize.clone();
    }

    public void setLightScreenPosition(Vector2f pos) {
        this.lightPos.set(pos);
    }

    public void setBloomTexture(Texture2D bloom) {
        this.bloomTex = bloom;
    }

    public void setResolution(Vector2f resolution) {
        this.resolution.set(resolution);
    }

    @Override
    protected void initFilter(AssetManager assetManager, RenderManager renderManager, ViewPort viewPort, int w, int h) {
        this.assetManager = assetManager;
        this.resolution.set(w, h);
    }

    @Override
    protected Material getMaterial() {
        Material mat = new Material(assetManager, "Shaders/LensFlare.j3md");
        if (bloomTex != null) mat.setTexture("Bloom", bloomTex);
        if (dirtTex != null) mat.setTexture("Dirt", dirtTex);
        try {
            mat.setVector2("LightPos", lightPos);
            mat.setVector2("Resolution", resolution);
        } catch (NoSuchMethodError e) {
            mat.setParam("LightPos", VarType.Vector2, lightPos);
            mat.setParam("Resolution", VarType.Vector2, resolution);
        }
        return mat;
    }
}