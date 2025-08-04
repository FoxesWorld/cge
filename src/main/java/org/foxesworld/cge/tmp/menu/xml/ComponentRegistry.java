package org.foxesworld.cge.tmp.menu.xml;

import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;
import org.foxesworld.cge.tmp.menu.xml.builders.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the registration and retrieval of component builders.
 * This allows the UI framework to be extended with new component types.
 */
public class ComponentRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentRegistry.class);
    private final Map<Class<? extends ComponentXml>, org.foxesworld.cge.tmp.menu.xml.ComponentBuilder<?>> builders = new HashMap<>();

    public ComponentRegistry(XmlMenuBuilder mainBuilder) {
        registerDefaultBuilders(mainBuilder);
    }

    private void registerDefaultBuilders(XmlMenuBuilder mainBuilder) {
        register(TitleXml.class, new TitleBuilder());
        register(ButtonXml.class, new ButtonBuilder());
        register(SliderXml.class, new SliderBuilder());
        register(CheckboxXml.class, new CheckboxBuilder());
        register(TabsXml.class, new TabsBuilder(mainBuilder));
        register(PanelXml.class, new PanelBuilder(mainBuilder));
    }

    public <T extends ComponentXml> void register(Class<T> modelClass, org.foxesworld.cge.tmp.menu.xml.ComponentBuilder<T> builder) {
        builders.put(modelClass, builder);
        LOGGER.debug("Registered component builder for {}", modelClass.getSimpleName());
    }

    public ComponentBuilder<? extends ComponentXml> getBuilderFor(ComponentXml model) {
        return builders.get(model.getClass());
    }
}