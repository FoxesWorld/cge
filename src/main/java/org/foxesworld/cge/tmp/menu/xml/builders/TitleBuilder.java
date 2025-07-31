package org.foxesworld.cge.tmp.menu.xml.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.components.ViceTitle;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.foxesworld.cge.tmp.menu.xml.TitleXml;
import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;

/**
 * Builds a {@link ViceTitle} component from a {@link TitleXml} model.
 */
public class TitleBuilder implements ComponentBuilder<TitleXml> {

    @Override
    public ViceTitle build(TitleXml model, Node parent, BuildContext context) {
        ViceTitle title = new ViceTitle(
                context.app().getAssetManager(),
                model.text,
                context.buttonStyle().fontPath()
        );

        title.setSize(model.fontSize);

        Vector2f pos = XmlMenuBuilder.calculatePosition(
                model.x,
                model.y,
                model.align,
                context.app().getCamera()
        );
        title.setPosition(pos.x, pos.y);

        parent.attachChild(title.getNode());
        return title;
    }
}