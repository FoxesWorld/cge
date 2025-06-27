package org.foxesworld.cge.tmp.menu.layout.components.elements;

import com.jme3.app.Application;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.tmp.menu.layout.components.ElementLayout;

public class PanelElement {

    /**
     * Вспомогательная структура для управления панелью
     */
    public static class Panel {
        private final Geometry geometry;
        private final Material material;

        public Panel(Geometry geometry, Material material) {
            this.geometry = geometry;
            this.material = material;
        }

        public Geometry getGeometry() {
            return geometry;
        }

        /**
         * Затемнить панель на заданную величину.
         * Например, amount = 0.5f сделает панель вполовину темнее.
         */
        public void darken(float amount) {
            ColorRGBA color = (ColorRGBA) material.getParam("Color").getValue();
            float factor = Math.max(0f, 1f - amount);
            color.r *= factor;
            color.g *= factor;
            color.b *= factor;
            material.setColor("Color", color);
        }

        /**
         * Установить полностью новый цвет
         */
        public void setColor(ColorRGBA newColor) {
            material.setColor("Color", newColor);
        }
    }

    public static Panel create(Application app, ElementLayout layout) {
        float x = layout.position.x;
        float y = layout.position.y;
        float w = layout.size.x;
        float h = layout.size.y;
        ColorRGBA color = parseColor(layout.attributes.getOrDefault("color", "0.1 0.14 0.2 0.8"));

        Geometry panel = new Geometry("MenuPanel_" + layout.id, new Quad(w, h));
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        panel.setMaterial(mat);
        panel.setLocalTranslation(x, y, 1.5f);
        panel.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);

        return new Panel(panel, mat);
    }

    private static ColorRGBA parseColor(String colorStr) {
        try {
            String[] comps = colorStr.trim().split("[ ,]+");
            float r = Float.parseFloat(comps[0]);
            float g = Float.parseFloat(comps[1]);
            float b = Float.parseFloat(comps[2]);
            float a = comps.length > 3 ? Float.parseFloat(comps[3]) : 1f;
            return new ColorRGBA(r, g, b, a);
        } catch (Exception e) {
            return ColorRGBA.White.clone();
        }
    }
}
