package org.foxesworld.cge.tmp.menu.xml.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.components.ViceCheckbox;
import org.foxesworld.cge.tmp.menu.xml.CheckboxXml;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;

/**
 * Builds a {@link ViceCheckbox} component from a {@link CheckboxXml} model.
 */
public class CheckboxBuilder implements ComponentBuilder<CheckboxXml> {

    @Override
    public ViceCheckbox build(CheckboxXml model, Node parent, BuildContext context) {
        ViceCheckbox checkbox = new ViceCheckbox(
                context.app().getAssetManager(),
                model.text,
                context.buttonStyle().fontPath(),
                model.checked,
                model.bind
        );

        float size = MenuUtils.parseSize(model.size, context.app().getCamera().getHeight());
        checkbox.setSize(size);

        Vector2f pos = MenuUtils.calculatePosition(
                model.x,
                model.y,
                model.alignX,
                context.app().getCamera()
        );
        checkbox.setPosition(pos.x, pos.y);

        parent.attachChild(checkbox.getNode());
        return checkbox;
    }
}