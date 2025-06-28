package org.foxesworld.cge.modules.ui.novaUi.elements;

import com.jme3.scene.Node;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;

/**
 * The fundamental contract for any object that can exist within the NovaUI element tree.
 * This interface is kept minimal, defining only the absolute essentials for tree management,
 * layout, and updates.
 */
public interface UIElement {

    /** Returns the unique identifier of this element. */
    String getId();

    /** Returns the JME3 scene graph Node for this element. */
    Node getNode();

    /** Returns the parent container of this element, or null if it's the root. */
    PanelElement getParentPanel();

    /** Sets the parent container for this element. */
    void setParentPanel(PanelElement parent);

    /** Called every frame by the UI system to update the element's state (e.g., animations). */
    void update(float tpf);

    /**
     * Sets a property from a key-value pair, typically parsed from a layout file.
     * This is the primary mechanism for configuring elements.
     */
    void setProperty(String key, String value);

    // --- Layout Properties ---

    /** The calculated width of the element after layout. */
    float getWidth();

    /** The calculated height of the element after layout. */
    float getHeight();

    /** The horizontal space this element requests outside its border. */
    float getMarginH();

    /** The vertical space this element requests outside its border. */
    float getMarginV();

    /** The horizontal space this element maintains inside its border. */
    float getPaddingH();

    /** The vertical space this element maintains inside its border. */
    float getPaddingV();

    /** The alignment hint for the parent layout strategy (e.g., "center", "top-right"). */
    String getAlign();

    /** The explicit X position, used primarily by AbsoluteLayout. */
    float getPosX();

    /** The explicit Y position, used primarily by AbsoluteLayout. */
    float getPosY();

    // --- Event Handling ---

    /**
     * Connects this element to its event handler target object.
     * Called by the system after the UI is built and event handlers are registered.
     */
    void setEventHandler(Object target);

    /**
     * Triggers the "onClick" action for this element, if one is defined.
     * Called by the UI system when a click event is detected on this element.
     */
    void triggerClick();
}