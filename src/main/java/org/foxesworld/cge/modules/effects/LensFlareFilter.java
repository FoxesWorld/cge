package org.foxesworld.cge.modules.effects;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.texture.Texture2D;

/**
 * AAA-style cinematic Lens Flare Filter.
 * This corrected version follows jMonkeyEngine's best practices for filters:
 * 1. The material is created only once in initFilter.
 * 2. getMaterial() simply returns the existing material.
 * 3. Uniforms that change per frame are updated in preFrame().
 * 4. It uses the inherited assetManager from the parent Filter class, avoiding redundancy.
 */
public class LensFlareFilter extends Filter {

    private Texture2D dirtTex;
    private Texture2D bloomTex;
    private Vector2f lightPos = new Vector2f(0.5f, 0.5f);
    private Vector2f resolution; // Initialized in constructor/initFilter

    /**
     * Default constructor for serialization.
     */
    public LensFlareFilter() {
        super("LensFlare");
    }

    /**
     * Creates a new LensFlareFilter.
     *
     * @param dirtTexture The texture for the "dirty lens" effect. Can be null.
     * @param screenSize  The initial screen size. Will be updated when the filter is added to a viewport.
     */
    public LensFlareFilter(Texture2D dirtTexture, Vector2f screenSize) {
        super("LensFlare");
        this.dirtTex = dirtTexture;
        this.resolution = screenSize != null ? screenSize.clone() : new Vector2f(1, 1);
    }

    /**
     * Creates a new LensFlareFilter with just the dirt texture.
     *
     * @param dirtTexture The texture for the "dirty lens" effect. Can be null.
     */
    public LensFlareFilter(Texture2D dirtTexture) {
        this(dirtTexture, null);
    }


    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        // The material is created here ONCE.
        // The 'assetManager' field from the parent class is automatically set by JME.
        material = new Material(manager, "assets/MatDefs/LensFlare.j3md");

        if (dirtTex != null) {
            material.setTexture("Dirt", dirtTex);
        }

        // Update the resolution based on the actual viewport dimensions.
        if (this.resolution == null) {
            this.resolution = new Vector2f(w, h);
        } else {
            this.resolution.set(w, h);
        }
    }

    @Override
    protected Material getMaterial() {
        // This method should simply return the material created in initFilter.
        // Do not create new materials here.
        return material;
    }

    @Override
    protected void preFrame(float tpf) {
        // This method is called before rendering the filter each frame.
        // This is the correct place to update shader uniforms that can change.
        if (material == null) {
            return;
        }

        // The bloom texture comes from a previous pass and must be updated every frame.
        if (bloomTex != null) {
            material.setTexture("Bloom", bloomTex);
        }

        // Using a try-catch block for compatibility with older JME versions is acceptable,
        // but it's better to stick to one API if possible.
        // The logic is moved here from getMaterial().
        try {
            material.setVector2("LightPos", lightPos);
            material.setVector2("Resolution", resolution);
        } catch (NoSuchMethodError e) {
            // Fallback for older JME versions
            //material.setParam("LightPos", Vector2, lightPos);
            //material.setParam("Resolution", Vector2, resolution);
        }
    }

    /**
     * Sets the screen position of the light source.
     * @param pos The position in screen coordinates (0.0 to 1.0).
     */
    public void setLightScreenPosition(Vector2f pos) {
        this.lightPos.set(pos);
    }


    /**
     * Sets the bloom texture, which is typically the output of a preceding bloom filter.
     * @param bloom The bloom texture.
     */
    public void setBloomTexture(Texture2D bloom) {
        this.bloomTex = bloom;
    }

    /**
     * Sets the resolution of the screen.
     * Note: This is usually handled automatically by the filter system in initFilter.
     * @param resolution The screen resolution.
     */
    public void setResolution(Vector2f resolution) {
        this.resolution.set(resolution);
    }
}