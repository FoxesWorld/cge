package org.foxesworld.cge.modules.effects;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Texture2D;
import com.jme3.texture.Image.Format;

/**
 * Simple motion blur based on previous frame blending.
 * Original author: Phate666, enhanced by Nehon :contentReference[oaicite:1]{index=1}
 */
public class MotionBlurFilter extends Filter {
    private Texture2D prevFrame;
    private Material material;

    public MotionBlurFilter() {
        super("MotionBlurFilter");
    }

    @Override
    protected void initFilter(AssetManager assetManager, RenderManager rm, ViewPort vp, int w, int h) {
        prevFrame = new Texture2D(w, h, Format.RGBA8);
        FrameBuffer fb = new FrameBuffer(w, h, 1);
        fb.setColorTexture(prevFrame);
        fb.setDepthBuffer(Format.Depth);
        //fb.setTarget(FrameBufferTarget.Texture);
        material = new Material(assetManager, "MatDefs/Post/MotionBlur.j3md");
        material.setTexture("PrevTex", prevFrame);
        material.setFloat("BlurStrength", 0.6f);
    }

    @Override
    protected Material getMaterial() {
        return material;
    }

    /*
    @Override
    public void postQueue(RenderManager rm, ViewPort vp, FrameBuffer prev) {
        super.postQueue(rm, vp, prev);
        rm.getRenderer().copyFrameBuffer(prev, prevFrame);
    }
    */
}