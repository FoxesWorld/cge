package org.foxesworld.cge.tmp.menu.layout.components.elements;

import com.jme3.app.Application;
import com.jme3.audio.AudioNode;
import com.jme3.texture.Texture2D;
import org.foxesworld.cge.tmp.menu.layout.components.ElementLayout;
import com.jme3.awt.AWTErrorDialog;

public class ButtonElement {

    public static Button create(Application app, ElementLayout layout, Runnable action) {
        String label = layout.attributes.getOrDefault("text", "Button");
        String iconPath = layout.attributes.get("icon");
        Texture2D icon = null;

        if (iconPath != null && !iconPath.isEmpty()) {
            try {
                icon = (Texture2D) app.getAssetManager().loadTexture(iconPath);
            } catch (Exception ignore) {}
        }

        Button btn = new Button(app, label, icon, action,
                layout.position.x, layout.position.y,
                layout.size.x, layout.size.y
        );

        btn.place(layout.position.x, layout.position.y, false);

        btn.setSelectionListener(button -> {
            AudioNode selectSound = new AudioNode(app.getAssetManager(), "assets/Sounds/select.ogg", false);
            selectSound.setPositional(false);
            selectSound.playInstance();
        });

        return btn;
    }
}
