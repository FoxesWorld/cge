package org.foxesworld.cge.tmp.menu.layout.components;

import com.jme3.app.Application;
import org.foxesworld.cge.tmp.menu.layout.components.elements.*;
import org.foxesworld.cge.tmp.menu.layout.components.MenuComponentFactory.ActionResolver;

public class MenuComponentFactory {

    private final Application app;

    public MenuComponentFactory(Application app) {
        this.app = app;
    }

    public Object createComponent(ElementLayout layout, ActionResolver actionResolver) {
        return switch (layout.type) {
            case "button" -> ButtonElement.create(app, layout, actionResolver.resolveAction(layout.id));
            case "panel"  -> PanelElement.create(app, layout);
            case "image"  -> ImageElement.create(app, layout);
            case "label"  -> LabelElement.create(app, layout);
            default       -> null;
        };
    }

    public interface ActionResolver {
        Runnable resolveAction(String id);
    }
}
