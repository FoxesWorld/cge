package org.foxesworld.cge.tmp.menu.xml.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.components.ViceTitle;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.foxesworld.cge.tmp.menu.xml.TitleXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                model.id,
                context.app().getAssetManager(),
                model.text,
                Math.round(fontSizePx),
                model.color,
                context.buttonStyle().fontPath()
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
        if (titleXml.fontSize == null || titleXml.fontSize.isEmpty()) {
            LOGGER.warn("fontSize not specified, defaulting to 32px");
            return 32f;
        }

        String fontSizeStr = titleXml.fontSize.trim().toLowerCase();
        try {
            int windowWidth = context.app().getContext().getSettings().getWidth();
            int windowHeight = context.app().getContext().getSettings().getHeight();

            if (fontSizeStr.endsWith("vh")) {
                float pct = Float.parseFloat(fontSizeStr.substring(0, fontSizeStr.length() - 2));
                return windowHeight * pct / 100f;
            } else if (fontSizeStr.endsWith("vw")) {
                float pct = Float.parseFloat(fontSizeStr.substring(0, fontSizeStr.length() - 2));
                return windowWidth * pct / 100f;
            } else if (fontSizeStr.endsWith("%")) {
                // Процент по умолчанию = vh
                float pct = Float.parseFloat(fontSizeStr.substring(0, fontSizeStr.length() - 1));
                return windowHeight * pct / 100f;
            } else {
                // Абсолютное значение в пикселях
                return Float.parseFloat(fontSizeStr);
            }
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid fontSize '{}' for Title '{}', defaulting to 32px", titleXml.fontSize, titleXml.text, e);
            return 32f;
        }
    }

}
