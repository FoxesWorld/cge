package org.foxesworld.cge.modules.effects;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

public class LensFlareFilter extends Filter {

    private Texture bloomTexture;
    private Vector2f lightPos = new Vector2f(0.5f, 0.5f);

    private Texture2D dirtTexture;
    private Texture2D lensColorTexture;

    private float globalIntensity = 1.0f;
    private ColorRGBA tint = ColorRGBA.White.clone();

    private float anamorphicIntensity = 3.0f;
    private float anamorphicStretch = 0.2f;
    private ColorRGBA anamorphicTint = new ColorRGBA(0.3f, 0.5f, 1.0f, 1.0f);

    private int ghostCount = 6;
    private float ghostIntensity = 1.0f;
    private float ghostDispersal = 0.4f;

    private float dirtIntensity = 2.5f;
    private float chromaticAberration = 0.5f;
    private float filmGrainAmount = 0.02f;

    public LensFlareFilter() {
        super("CinematicLensFlare");
    }

    public LensFlareFilter(Texture2D dirtTexture, Texture2D lensColorTexture) {
        super("CinematicLensFlare");
        this.dirtTexture = dirtTexture;
        this.lensColorTexture = lensColorTexture;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        material = new Material(manager, "assets/MatDefs/LensFlareCinematic.j3md");
        material.setTexture("Dirt", dirtTexture);
        material.setTexture("LensColor", lensColorTexture);
        updateAllMaterialParameters();
    }

    @Override
    protected Material getMaterial() {
        return this.material.clone();
    }

    @Override
    protected void preFrame(float tpf) {
        if (material == null) return;
        material.setTexture("Bloom", bloomTexture);
        material.setVector2("LightPos", lightPos);
    }

    private void updateAllMaterialParameters() {
        if (material == null) return;
        material.setFloat("GlobalIntensity", globalIntensity);
        material.setColor("Tint", tint);
        material.setFloat("AnamorphicIntensity", anamorphicIntensity);
        material.setFloat("AnamorphicStretch", anamorphicStretch);
        material.setColor("AnamorphicTint", anamorphicTint);
        material.setInt("GhostCount", ghostCount);
        material.setFloat("GhostIntensity", ghostIntensity);
        material.setFloat("GhostDispersal", ghostDispersal);
        material.setFloat("DirtIntensity", dirtIntensity);
        material.setFloat("ChromaticAberration", chromaticAberration);
        material.setFloat("FilmGrainAmount", filmGrainAmount);
    }

    public LensFlareFilter setBloomTexture(Texture bloomTexture) {
        this.bloomTexture = bloomTexture;
        return this;
    }

    public LensFlareFilter setLightPosition(Vector2f lightPos) {
        this.lightPos.set(lightPos);
        return this;
    }

    public LensFlareFilter setGlobalIntensity(float intensity) {
        this.globalIntensity = intensity;
        if (material != null) material.setFloat("GlobalIntensity", intensity);
        return this;
    }

    public LensFlareFilter setTint(ColorRGBA tint) {
        this.tint.set(tint);
        if (material != null) material.setColor("Tint", tint);
        return this;
    }

    public LensFlareFilter setAnamorphicIntensity(float intensity) {
        this.anamorphicIntensity = intensity;
        if (material != null) material.setFloat("AnamorphicIntensity", intensity);
        return this;
    }

    public LensFlareFilter setAnamorphicStretch(float stretch) {
        this.anamorphicStretch = stretch;
        if (material != null) material.setFloat("AnamorphicStretch", stretch);
        return this;
    }

    public LensFlareFilter setAnamorphicTint(ColorRGBA color) {
        this.anamorphicTint.set(color);
        if (material != null) material.setColor("AnamorphicTint", color);
        return this;
    }

    public LensFlareFilter setGhostCount(int count) {
        this.ghostCount = count;
        if (material != null) material.setInt("GhostCount", count);
        return this;
    }

    public LensFlareFilter setGhostIntensity(float intensity) {
        this.ghostIntensity = intensity;
        if (material != null) material.setFloat("GhostIntensity", intensity);
        return this;
    }

    public LensFlareFilter setGhostDispersal(float dispersal) {
        this.ghostDispersal = dispersal;
        if (material != null) material.setFloat("GhostDispersal", dispersal);
        return this;
    }

    public LensFlareFilter setDirtIntensity(float intensity) {
        this.dirtIntensity = intensity;
        if (material != null) material.setFloat("DirtIntensity", intensity);
        return this;
    }

    public LensFlareFilter setChromaticAberration(float amount) {
        this.chromaticAberration = amount;
        if (material != null) material.setFloat("ChromaticAberration", amount);
        return this;
    }

    public LensFlareFilter setFilmGrainAmount(float amount) {
        this.filmGrainAmount = amount;
        if (material != null) material.setFloat("FilmGrainAmount", amount);
        return this;
    }
}