package org.foxesworld.cge.modules.effects;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.texture.Texture2D;

/**
 * Фильтр для цветокоррекции с использованием 3D LUT (Look-Up Table).
 */
public class ColorGradingFilter extends Filter {

    private Texture2D lutTexture;
    private float lutSize;

    public ColorGradingFilter(Texture2D lutTexture, float lutSize) {
        super("ColorGradingFilter");
        this.lutTexture = lutTexture;
        this.lutSize = lutSize;
    }

    public ColorGradingFilter() {
        super("ColorGradingFilter");
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        material = new Material(manager, "assets/MatDefs/ColorGrading.j3md");
        if (lutTexture != null) {
            setLut(this.lutTexture, this.lutSize);
        }
    }

    @Override
    protected Material getMaterial() {
        return material;
    }

    public void setLut(Texture2D lutTexture, float lutSize) {
        this.lutTexture = lutTexture;
        this.lutSize = lutSize;
        if (material != null) {
            material.setTexture("ColorLUT", lutTexture);
            material.setFloat("LutSize", lutSize);
        }
    }

    public Texture2D getLutTexture() {
        return lutTexture;
    }

    public float getLutSize() {
        return lutSize;
    }
}