package org.foxesworld.cge.modules.ui.novaUi.elements;

import com.jme3.scene.Node;
import com.jme3.math.ColorRGBA;

/**
 * AbstractRenderer is the base class for UI visual renderers.
 * <p>
 * Any renderer must provide access to its main Node,
 * may support color manipulation, and is expected to
 * support resizing operations.
 * </p>
 * <ul>
 *     <li>Subclasses must implement {@link #getNode()} to supply the main scene node for integration.</li>
 *     <li>Color and sizing methods are optional and may be overridden by subclasses if supported.</li>
 * </ul>
 */
public abstract class AbstractRenderer {
    /**
     * Returns the main node of the renderer, which can be attached
     * to the scene graph hierarchy.
     *
     * @return the root Node representing this renderer.
     */
    public abstract Node getNode();

    /**
     * Sets the color of the renderer, if supported.
     * Subclasses that do not support color can leave this method unimplemented.
     *
     * @param color the color to apply to the renderer, or null if unsupported.
     */
    public void setColor(ColorRGBA color) {}

    /**
     * Returns the current color of the renderer, if supported.
     * Subclasses that do not support color can return null.
     *
     * @return the current color, or null if unsupported.
     */
    public ColorRGBA getColor() { return null; }

    /**
     * Updates the size of the renderer, if supported.
     * Subclasses that do not support resizing can leave this method unimplemented.
     *
     * @param width  the new width
     * @param height the new height
     */
    public void setSize(float width, float height) {}

    /**
     * Returns the current width of the renderer, if supported.
     * Subclasses that do not support sizing can return 0.
     *
     * @return the width, or 0 if unsupported.
     */
    public float getWidth() { return 0f; }

    /**
     * Returns the current height of the renderer, if supported.
     * Subclasses that do not support sizing can return 0.
     *
     * @return the height, or 0 if unsupported.
     */
    public float getHeight() { return 0f; }
}