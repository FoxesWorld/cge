package org.foxesworld.cge.tmp.menu.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.components.ViceSlider;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.foxesworld.cge.tmp.menu.xml.SliderXml;

/**
 * Builds a {@link ViceSlider} component from a {@link SliderXml} model.
 */
public class SliderBuilder implements ComponentBuilder<SliderXml> {

    @Override
    public ViceSlider build(SliderXml model, Node parent, BuildContext context) {
        ViceSlider slider = new ViceSlider(
                context.mainMenuAppState().getGameEngine().getAssetManager(),
                model
        );


            String[] binds = model.bind.split("\\.");
            slider.setValue((Float) MainMenuAppState.getSettingsValue(binds[0], binds[1]));

        float width = MenuUtils.parseSize(String.valueOf(model.width), context.mainMenuAppState().getGameEngine().getCamera().getWidth());
        slider.setSize(width);

        Vector2f pos = MenuUtils.calculatePosition(
                model.x,
                model.y,
                model.alignX,
                context.mainMenuAppState().getGameEngine().getCamera()
        );
        slider.setPosition(pos.x, pos.y);

        parent.attachChild(slider.getNode());
        return slider;
    }
}