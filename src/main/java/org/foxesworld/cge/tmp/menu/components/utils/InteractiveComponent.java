package org.foxesworld.cge.tmp.menu.components.utils;

import com.jme3.math.Vector2f;

/**
 * An extension of MenuComponent for elements that can directly handle user input events like
 * hover states and mouse clicks.
 */
public interface InteractiveComponent extends MenuComponent {


    /**
     * Sets the active state of the component. An inactive component should not
     * respond to hover or click events.
     *
     * @param active True if the component should be interactive, false otherwise.
     */
    void setActive(boolean active);

    /**
     * Sets the hover state of the component. The component's update loop is
     * responsible for visually reflecting this state (e.g., changing color).
     *
     * @param hovered True if the mouse cursor is over the component, false otherwise.
     */
    void setHovered(boolean hovered);

    /**
     * Handles a mouse press event over this component.
     *
     * @param cursor The cursor position in the component's parent coordinate space.
     */
    void handleMousePress(Vector2f cursor);

    /**
     * Handles a mouse drag event that originated on this component.
     *
     * @param cursor The current cursor position in the component's parent coordinate space.
     */
    void handleMouseDrag(Vector2f cursor);

    /**
     * Handles a mouse release event that originated on this component.
     */
    void handleMouseRelease();

}