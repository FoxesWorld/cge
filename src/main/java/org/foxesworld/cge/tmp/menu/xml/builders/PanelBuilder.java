package org.foxesworld.cge.tmp.menu.xml.builders;

import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import org.foxesworld.cge.core.utils.ColorUtils;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;
import org.foxesworld.cge.tmp.menu.components.MenuComponent;
import org.foxesworld.cge.tmp.menu.components.Panel;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.foxesworld.cge.tmp.menu.xml.ComponentXml;
import org.foxesworld.cge.tmp.menu.xml.PanelXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a Panel component from its XML definition.
 * This builder now delegates layout logic to the Panel component itself.
 */
public class PanelBuilder implements ComponentBuilder<PanelXml> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PanelBuilder.class);

    private final XmlMenuBuilder mainBuilder;

    public PanelBuilder(XmlMenuBuilder mainBuilder) {
        this.mainBuilder = mainBuilder;
    }

    @Override
    public Panel build(PanelXml model, Node parent, BuildContext context) {
        // --- Лог о начале сборки ---
        LOGGER.debug("Building Panel with id='{}'.", model.getId());

        // --- 1. Создание и конфигурация панели ---

        // Определяем стиль из XML или используем значения по умолчанию
        Panel.Style defaultStyle = Panel.Style.getDefaultStyle();
        ColorRGBA bgColor = defaultStyle.backgroundColor();
        if (model.bgColor != null) {
            try {
                bgColor = ColorUtils.fromHexString(model.bgColor);
            } catch (Exception e) {
                LOGGER.error("Invalid HEX color format for panel '{}' bgColor: '{}'. Using default.", model.getId(), model.bgColor);
            }
        }
        if (model.bgAlpha != null) {
            bgColor.a = model.bgAlpha;
        }

        float cornerRadius = model.cornerRadius != null ? model.cornerRadius : defaultStyle.cornerRadius();
        Panel.Style style = new Panel.Style(bgColor, cornerRadius);

        // Определяем параметры компоновки из XML или используем значения по умолчанию
        float padding = model.padding != null ? model.padding : 10f;
        float spacing = model.spacing != null ? model.spacing : 8f;

        // --- Лог о параметрах компоновки ---
        LOGGER.trace("Panel '{}' layout params: padding={}, spacing={}, cornerRadius={}", model.getId(), padding, spacing, cornerRadius);

        // Создаем экземпляр "умной" панели
        Panel panel = new Panel(model.getId(), context.app().getAssetManager(), style, padding, spacing);

        // --- Безопасная установка базовых свойств панели ---
        try {
            float x = Float.parseFloat(model.x);
            float y = Float.parseFloat(model.y);
            float width = Float.parseFloat(model.width);
            float height = Float.parseFloat(model.height);

            panel.setPosition(x, y);
            panel.setSize(width, height);

            // --- Лог о геометрии ---
            LOGGER.trace("Panel '{}' geometry set: pos=({}, {}), size=({}, {})", model.getId(), x, y, width, height);

        } catch (NumberFormatException e) {
            LOGGER.error("Invalid numeric value for position or size in Panel '{}'. Panel might not be visible.", model.getId(), e);
            // Панель будет создана, но с размерами/позицией по умолчанию (0,0), что позволит избежать падения.
        }

        // --- 2. Создание и добавление дочерних компонентов ---

        if (model.components != null && !model.components.isEmpty()) {
            LOGGER.debug("Panel '{}' has {} child components to build.", model.getId(), model.components.size());
            for (ComponentXml componentModel : model.components) {

                // --- Лог о начале сборки дочернего элемента ---
                LOGGER.trace("--> Building child component '{}' for Panel '{}'...", componentModel.text, model.getId());

                MenuComponent childComponent = mainBuilder.buildComponent(componentModel, panel.getNode());

                if (childComponent != null) {
                    panel.addComponent(childComponent);
                } else {
                    LOGGER.warn("--> Failed to build child component with id '{}' for Panel '{}'.", componentModel.text, model.getId());
                }
            }
        } else {
            LOGGER.debug("Panel '{}' has no child components defined in XML.", model.getId());
        }

        // --- 3. Прикрепление готовой панели к сцене ---
        parent.attachChild(panel.getNode());
        LOGGER.debug("Panel '{}' built and attached to parent '{}'.", model.getId(), parent.getName());

        return panel;
    }
}