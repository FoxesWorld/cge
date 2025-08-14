package org.foxesworld.cge.tmp.menu;

import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.components.*;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.xml.SceneXml;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An immutable data container that holds the complete result of parsing a menu XML file.
 * <p>
 * This record encapsulates all necessary information for the {@link MainMenuAppState}
 * to manage a menu scene. It provides a unified list of all components for generic
 * operations (like updates) and convenient filtered views for type-specific logic.
 * </p>
 *
 * @param sceneConfig   The configuration for the 3D background scene, parsed from the {@code <scene>} tag.
 * @param uiNode        The root {@link Node} of the generated 2D user interface.
 * @param allComponents A unified list of all {@link UIComponent} instances created from the XML.
 */
public record MenuData(
        SceneXml sceneConfig,
        Node uiNode,
        List<UIComponent> allComponents
) {
    /**
     * A factory method to create a safe, empty MenuData object.
     * This is used to prevent NullPointerExceptions if XML parsing fails.
     *
     * @return An empty, non-null MenuData instance.
     */
    public static MenuData createEmpty() {
        return new MenuData(
                new SceneXml(),
                new Node("EmptyUINode"),
                List.of() // Immutable empty list
        );
    }

    /**
     * @return A filtered, type-safe list of all {@link ViceButton} components.
     */
    public List<ViceButton> getButtons() {
        return allComponents.stream()
                .filter(ViceButton.class::isInstance)
                .map(ViceButton.class::cast)
                .collect(Collectors.toList());
    }

    /**
     * @return A filtered, type-safe list of all {@link ViceSlider} components.
     */
    public List<ViceSlider> getSliders() {
        return allComponents.stream()
                .filter(ViceSlider.class::isInstance)
                .map(ViceSlider.class::cast)
                .collect(Collectors.toList());
    }

    /**
     * @return A filtered, type-safe list of all {@link ViceCheckbox} components.
     */
    public List<ViceCheckbox> getCheckboxes() {
        return allComponents.stream()
                .filter(ViceCheckbox.class::isInstance)
                .map(ViceCheckbox.class::cast)
                .collect(Collectors.toList());
    }

    /**
     * @return A filtered, type-safe list of all {@link ViceTabs} components.
     */
    public List<ViceTabs> getTabGroups() {
        return allComponents.stream()
                .filter(ViceTabs.class::isInstance)
                .map(ViceTabs.class::cast)
                .collect(Collectors.toList());
    }
}