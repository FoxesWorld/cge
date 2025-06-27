package org.foxesworld.cge.tmp.menu.layout.components.elements;

import com.jme3.app.Application;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import org.foxesworld.cge.tmp.menu.layout.components.ElementLayout;

public class LabelElement {

    public static BitmapText create(Application app, ElementLayout layout) {
        float x = layout.position.x;
        float y = layout.position.y;
        float size = layout.size.y > 0 ? layout.size.y : 32f;

        String fontPath = layout.attributes.getOrDefault("font", "Interface/Fonts/Default.fnt");
        String text = layout.attributes.getOrDefault("text", "");
        ColorRGBA color = parseColor(layout.attributes.getOrDefault("color", "1 1 1 1"));

        BitmapFont font;
        try {
            font = app.getAssetManager().loadFont(fontPath);
        } catch (Exception e) {
            font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        }

        BitmapText bt = new BitmapText(font, false);
        bt.setText(text);
        bt.setSize(size);
        bt.setColor(color);
        bt.setLocalTranslation(x, y + size, 2.1f);
        return bt;
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
