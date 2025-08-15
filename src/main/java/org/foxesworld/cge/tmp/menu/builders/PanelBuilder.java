package org.foxesworld.cge.tmp.menu.builders;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import org.foxesworld.cge.core.utils.ColorUtils;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;
import org.foxesworld.cge.tmp.menu.components.UIComponent;
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
        String panelId = model.getId();
        LOGGER.debug("--- Building Panel id='{}'.", panelId + " ---");

        // === Style Initialization ===
        Panel.Style defaultStyle = Panel.Style.getDefaultStyle();
        ColorRGBA bgColor = defaultStyle.backgroundColor();

        if (model.getBgColor() != null) {
            try {
                bgColor = ColorUtils.fromHexString(model.getBgColor());
            } catch (IllegalArgumentException ex) {
                LOGGER.error("Invalid bgColor '{}' for Panel '{}'; using default.", model.getBgColor(), panelId);
            }
        }

        if (model.getBgAlpha() != null) {
            bgColor.a = model.getBgAlpha();
        }

        float cornerRadius = model.getCornerRadius() != null ? model.getCornerRadius() : defaultStyle.cornerRadius();
        float padding = model.getPadding() != null ? model.getPadding() : 10f;
        float spacing = model.getSpacing() != null ? model.getSpacing() : 8f;

        Panel.Style style = new Panel.Style(bgColor, cornerRadius);

        // === Create Panel ===
        Panel panel = new Panel(panelId, context.app(), style, padding, spacing);
        panel.setDpiScale(model.getDpiScale());

        // === Set Anchor ===
        try {
            Panel.Anchor anchor = Panel.Anchor.valueOf(model.getAnchor().toUpperCase());
            panel.setAnchor(anchor);
        } catch (IllegalArgumentException | NullPointerException e) {
            LOGGER.warn("Invalid or missing anchor '{}' for Panel '{}'; defaulting to TOP_LEFT.",
                    model.getAnchor(), panelId);
            panel.setAnchor(Panel.Anchor.TOP_LEFT);
        }

        // === Set Bounds ===
        panel.setRelativeBounds(model.x, model.y, model.width, model.height, model.getZindex());

        // === Children ===
        if (model.getComponents() != null && !model.getComponents().isEmpty()) {
            LOGGER.debug("Panel '{}' has {} children.", panelId, model.getComponents().size());
            for (var compXml : model.getComponents()) {
                UIComponent child = mainBuilder.buildComponent(compXml, panel.getNode());
                LOGGER.info("Child {}", child.getId());
                //LOGGER.debug("  - Child's parent is {} ", child.getParentComponent().getId());
                //LOGGER.debug("  - Child's parent size is {}x{}", child.getParentComponent().getWidth(), child.getParentComponent().getHeight());
                panel.addComponent(child);
            }
        }

        // === Attach ===
        parent.attachChild(panel.getNode());
        LOGGER.debug("Panel '{}' attached to parent '{}'.", panelId, parent.getName());

        return panel;
    }
}
