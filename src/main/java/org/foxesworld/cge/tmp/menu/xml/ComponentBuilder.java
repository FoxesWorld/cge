package org.foxesworld.cge.tmp.menu.xml;

import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.components.MenuComponent;

/**
 * A functional interface for a factory that builds a specific UI component
 * from its corresponding XML model.
 *
 * @param <T> The type of the XML model class (e.g., ButtonXml, SliderXml).
 */
@FunctionalInterface
public interface ComponentBuilder<T extends ComponentXml> {
    /**
     * Builds a UI component.
     *
     * @param model   The JAXB model containing the component's properties from XML.
     * @param parent  The parent Node to which the component should be attached.
     * @param context The build context, providing access to shared resources like AssetManager.
     * @return The created UI component instance if it's interactive, otherwise null.
     */
    MenuComponent build(T model, Node parent, BuildContext context);
}