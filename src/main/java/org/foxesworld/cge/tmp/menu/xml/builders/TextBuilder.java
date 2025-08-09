package org.foxesworld.cge.tmp.menu.xml.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.components.ViceText;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.foxesworld.cge.tmp.menu.xml.TextXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a {@link ViceText} component from a {@link TextXml} model,
 * supporting fontSize specified as absolute or percentage of screen height.
 */
public class TextBuilder implements ComponentBuilder<TextXml> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextBuilder.class);

    @Override
    public ViceText build(TextXml model, Node parent, BuildContext context) {

        // Instantiate title
        ViceText title = new ViceText(
                context.app(),
                context.app().getAssetManager(),
                model
        );

        // Calculate position
        Vector2f pos = MenuUtils.calculatePosition(
                model.x,
                model.y,
                model.alignX,
                context.app().getCamera()
        );
        title.setPosition(pos.x, pos.y);
        if(model.getAnchor() != null) {
            title.setAnchor(ViceText.Anchor.valueOf(model.getAnchor()));
        }

        parent.attachChild(title.getNode());
        return title;
    }
}
