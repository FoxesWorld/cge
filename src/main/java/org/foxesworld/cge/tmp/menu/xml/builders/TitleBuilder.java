package org.foxesworld.cge.tmp.menu.xml.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.components.ViceTitle;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.foxesworld.cge.tmp.menu.xml.TitleXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Builds a {@link ViceTitle} component from a {@link TitleXml} model,
 * supporting fontSize specified as absolute or percentage of screen height.
 */
public class TitleBuilder implements ComponentBuilder<TitleXml> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TitleBuilder.class);

    @Override
    public ViceTitle build(TitleXml model, Node parent, BuildContext context) {
        //context.app().getContext().getSettings().getWindowWidth() размер окна
        // Determine font size in pixels
        float fontSizePx = parseFontSize(model, context);
        // Instantiate title
        ViceTitle title = new ViceTitle(
                context.app().getAssetManager(),
                model,
                fontSizePx
        );

        // Calculate position
        Vector2f pos = MenuUtils.calculatePosition(
                model.x,
                model.y,
                model.alignX,
                context.app().getCamera()
        );
        title.setPosition(pos.x, pos.y);

        parent.attachChild(title.getNode());
        return title;
    }

    /**
     * Parses fontSize which may be an absolute value (e.g. "48")
     * or a percentage string (e.g. "5%" representing percent of screen height).
     */
    private float parseFontSize(TitleXml titleXml, BuildContext context) {
        String value = Optional.ofNullable(titleXml.fontSize)
                .map(String::trim)
                .map(String::toLowerCase)
                .orElse("");

        if (value.isEmpty()) {
            LOGGER.warn("fontSize not specified, defaulting to 32px");
            return 32f;
        }

        try {
            float percent;
            int width = context.app().getContext().getSettings().getWidth();
            int height = context.app().getContext().getSettings().getHeight();

            if (value.endsWith("vh")) {
                percent = Float.parseFloat(value.substring(0, value.length() - 2));
                return height * percent / 100f;
            }

            if (value.endsWith("vw")) {
                percent = Float.parseFloat(value.substring(0, value.length() - 2));
                return width * percent / 100f;
            }

            if (value.endsWith("%")) {
                percent = Float.parseFloat(value.substring(0, value.length() - 1));
                return height * percent / 100f;
            }

            return Float.parseFloat(value);

        } catch (NumberFormatException e) {
            LOGGER.error("Invalid fontSize '{}' for Title '{}', defaulting to 32px",
                    titleXml.fontSize, titleXml.text, e);
            return 32f;
        }
    }

}
