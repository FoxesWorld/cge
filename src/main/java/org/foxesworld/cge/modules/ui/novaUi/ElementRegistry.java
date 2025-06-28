package org.foxesworld.cge.modules.ui.novaUi;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.button.ButtonElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.image.ImageElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.progress.ProgressElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.text.TextElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ElementRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElementRegistry.class);

    public record CreationContext(CalistaGameEngine engine, UINodeDefinition definition, PanelElement parent) {}

    private final Map<String, Function<CreationContext, UIElement>> constructors = new ConcurrentHashMap<>();

    public ElementRegistry(CalistaGameEngine engine) {
        registerDefaultElements(engine);
    }

    private void registerDefaultElements(CalistaGameEngine engine) {
        register("Panel", ctx -> new PanelElement(engine, ctx.definition.getAttribute("id"), ctx.parent));
        register("Image", ctx -> new ImageElement(engine, ctx.definition.getAttribute("id"), ctx.parent));
        register("Text", ctx -> new TextElement(engine, ctx.definition.getAttribute("id"), ctx.parent));
        register("Button", ctx -> new ButtonElement(engine, ctx.definition.getAttribute("id"), ctx.parent));
        register("Progress", ctx -> new ProgressElement(engine, ctx.definition.getAttribute("id"), ctx.parent));
    }

    public void register(String type, Function<CreationContext, UIElement> constructor) {
        constructors.put(type.toLowerCase(), constructor);
        LOGGER.debug("Registered UI element type: {}", type);
    }

    public UIElement create(CreationContext context) {
        String type = context.definition.getType().toLowerCase();
        Function<CreationContext, UIElement> constructor = constructors.get(type);

        if (constructor != null) {
            return constructor.apply(context);
        } else {
            LOGGER.warn("UI Element type '{}' is not registered. Cannot create element with id '{}'.",
                    type, context.definition.getAttribute("id"));
            return null;
        }
    }
}