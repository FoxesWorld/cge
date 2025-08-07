package org.foxesworld.cge.tmp.menu.xml.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.components.ViceSlider;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.foxesworld.cge.tmp.menu.xml.SliderXml;
import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;

/**
 * Builds a {@link ViceSlider} component from a {@link SliderXml} model.
 */
public class SliderBuilder implements ComponentBuilder<SliderXml> {

    @Override
    public ViceSlider build(SliderXml model, Node parent, BuildContext context) {
        ViceSlider slider = new ViceSlider(
                context.app().getAssetManager(),
                model
        );

        float width = MenuUtils.parseSize(String.valueOf(model.width), context.app().getCamera().getWidth());
        slider.setSize(width);

        Vector2f pos = MenuUtils.calculatePosition(
                model.x,
                model.y,
                model.alignX,
                context.app().getCamera()
        );
        slider.setPosition(pos.x, pos.y);

        parent.attachChild(slider.getNode());
        return slider;
    }
}