package org.foxesworld.cge.tmp.menu.components;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;

/**
 * A common interface for all interactive UI components built by the XmlMenuBuilder.
 */
public interface MenuComponent {
    /**
     * @return The JME3 Node that represents this component.
     */
    Node getNode();

    /**
     * Updates the component's state and animations.
     */
    void update(float tpf);

    /**
     * Checks if a point (like a cursor) is within the component's bounds.
     */
    boolean intersects(Vector2f cursor);

    float getWidth();
    float getHeight();

    void setSize(final float width, final float height);
}