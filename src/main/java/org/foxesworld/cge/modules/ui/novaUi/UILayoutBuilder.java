package org.foxesworld.cge.modules.ui.novaUi;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class UILayoutBuilder {
    public static final String ATTR_ID = "id";

    private final CalistaGameEngine engine;
    private final ElementRegistry elementRegistry;
    private final UINodeDefinition rootDefinition;

    public UILayoutBuilder(CalistaGameEngine engine, ElementRegistry elementRegistry, UINodeDefinition rootDefinition) {
        this.engine = engine;
        this.elementRegistry = Objects.requireNonNull(elementRegistry, "ElementRegistry cannot be null");
        this.rootDefinition = Objects.requireNonNull(rootDefinition, "Root UINodeDefinition cannot be null");
    }

    public UILayoutLoader.ParseResult build() {
        Map<String, UIElement> allElements = new HashMap<>();
        PanelElement rootPanel = (PanelElement) buildRecursive(rootDefinition, null, allElements);
        return new UILayoutLoader.ParseResult(rootPanel, allElements);
    }

    private UIElement buildRecursive(UINodeDefinition definition, PanelElement parent, Map<String, UIElement> allElements) {
        ElementRegistry.CreationContext context = new ElementRegistry.CreationContext(engine, definition, parent);
        UIElement currentElement = elementRegistry.create(context);
        if (currentElement == null) return null;

        definition.getAttributes().forEach((key, value) -> {
            if (!key.equalsIgnoreCase(ATTR_ID)) {
                currentElement.setProperty(key, value);
            }
        });

        String id = currentElement.getId();
        if (id != null && !id.isEmpty()) {
            allElements.put(id, currentElement);
        }

        if (currentElement instanceof PanelElement currentPanel) {
            for (UINodeDefinition childDef : definition.getChildren()) {
                UIElement childElement = buildRecursive(childDef, currentPanel, allElements);
                if (childElement != null) {
                    currentPanel.addChild(childElement);
                }
            }
        }
        return currentElement;
    }
}