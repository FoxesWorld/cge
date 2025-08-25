package org.foxesworld.cge.tmp.menu.components.utils;

import com.jme3.math.Vector2f;

/**
 * An extension of MenuComponent for elements that can directly handle user input events like
 * hover states, mouse clicks, and advanced mouse interactions.
 */
public interface InteractiveComponent {

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
     * Handles a mouse enter event (cursor entered component bounds).
     *
     * @param cursor The cursor position in the component's parent coordinate space.
     */
    void handleMouseEnter(Vector2f cursor);

    /**
     * Handles a mouse exit event (cursor left component bounds).
     *
     * @param cursor The cursor position in the component's parent coordinate space.
     */
    void handleMouseExit(Vector2f cursor);

    /**
     * Handles a mouse move event inside this component.
     *
     * @param cursor The cursor position in the component's parent coordinate space.
     */
    void handleMouseMove(Vector2f cursor);

    /**
     * Handles a full click (press + release inside).
     */
    void handleMouseClick(Vector2f cursor);

    /**
     * Handles a mouse press event over this component.
     */
    void handleMousePress(Vector2f cursor);

    /**
     * Handles a double click.
     */
    void handleMouseDoubleClick(Vector2f cursor);

    /**
     * Handles a mouse release event that originated on this component.
     *
     * @param cursor The cursor position in the component's parent coordinate space.
     */
    void handleMouseRelease(Vector2f cursor);

    /**
     * Handles a mouse drag event that originated on this component.
     *
     * @param cursor The current cursor position in the component's parent coordinate space.
     */
    void handleMouseDrag(Vector2f cursor);

    /**
     * Handles a mouse wheel scroll event.
     *
     * @param cursor The cursor position in the component's parent coordinate space.
     * @param delta  The scroll amount (positive = up, negative = down).
     */
    void handleMouseScroll(Vector2f cursor, float delta);

    /**
     * Handles a double click event on this component.
     *
     * @param cursor The cursor position in the component's parent coordinate space.
     */
    void handleDoubleClick(Vector2f cursor);

    /**
     * Checks if the given cursor position intersects this component.
     *
     * @param cursor The cursor position in the component's parent coordinate space.
     * @return true if the cursor is inside this component, false otherwise.
     */
    boolean intersects(Vector2f cursor);
}
