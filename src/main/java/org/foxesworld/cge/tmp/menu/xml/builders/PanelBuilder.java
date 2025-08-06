package org.foxesworld.cge.tmp.menu.xml.builders;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import org.foxesworld.cge.core.utils.ColorUtils;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;
import org.foxesworld.cge.tmp.menu.components.MenuComponent;
import org.foxesworld.cge.tmp.menu.components.Panel;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.foxesworld.cge.tmp.menu.xml.PanelXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PanelBuilder implements ComponentBuilder<PanelXml> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PanelBuilder.class);
    private final XmlMenuBuilder mainBuilder;

    public PanelBuilder(XmlMenuBuilder mainBuilder) {
        this.mainBuilder = mainBuilder;
    }

    @Override
    public Panel build(PanelXml model, Node parent, BuildContext context) {
        LOGGER.debug("Building Panel id='{}'.", model.getId());

        Panel.Style baseStyle = Panel.Style.getDefaultStyle();
        ColorRGBA bgColor = baseStyle.backgroundColor();

        if (model.bgColor != null) {
            try {
                bgColor = ColorUtils.fromHexString(model.bgColor);
            } catch (IllegalArgumentException ex) {
                LOGGER.error("Invalid color '{}' for Panel '{}'; using default.", model.bgColor, model.getId());
            }
        }

        if (model.bgAlpha != null) {
            bgColor.a = model.bgAlpha;
        }

        float radius = model.cornerRadius != null ? model.cornerRadius : baseStyle.cornerRadius();
        float padding = model.padding != null ? model.padding : 10f;
        float spacing = model.spacing != null ? model.spacing : 8f;

        Panel.Style style = new Panel.Style(bgColor, radius);
        Panel panel = new Panel(model.getId(), context.app(), style, padding, spacing);

        panel.setDpiScale(model.getDpiScale());
        panel.setAnchor(Panel.Anchor.valueOf(model.getAnchor().toUpperCase()));
        panel.setRelativeBounds(model.x, model.y, model.width, model.height);

        if (model.components != null) {
            LOGGER.debug("Panel '{}' has {} children.", model.getId(), model.components.size());
            for (var compXml : model.components) {
                MenuComponent child = mainBuilder.buildComponent(compXml, panel.getNode());
                if (child != null) {
                    panel.addComponent(child);
                } else {
                    LOGGER.warn("Failed to build child '{}' in Panel '{}'.", compXml.text, model.getId());
                }
            }
        }

        parent.attachChild(panel.getNode());
        LOGGER.debug("Panel '{}' attached to parent '{}'.", model.getId(), parent.getName());

        return panel;
    }
}
