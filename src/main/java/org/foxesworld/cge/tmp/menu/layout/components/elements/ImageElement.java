package org.foxesworld.cge.tmp.menu.layout.components.elements;

import com.jme3.app.Application;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture2D;
import org.foxesworld.cge.tmp.menu.layout.components.ElementLayout;

public class ImageElement {

    public static Geometry create(Application app, ElementLayout layout) {
        float x = layout.position.x;
        float y = layout.position.y;
        float w = layout.size.x;
        float h = layout.size.y;
        String src = layout.attributes.get("src");
        if (src == null) return null;

        Texture2D texture = null;
        try {
            texture = (Texture2D) app.getAssetManager().loadTexture(src);
        } catch (Exception ignore) {}

        if (texture == null) return null;

        Geometry img = new Geometry("MenuImg_" + layout.id, new Quad(w, h));
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", texture);
        mat.setColor("Color", ColorRGBA.White);
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        img.setMaterial(mat);
        img.setLocalTranslation(x, y, 1.2f);
        img.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);

        return img;
    }
}
