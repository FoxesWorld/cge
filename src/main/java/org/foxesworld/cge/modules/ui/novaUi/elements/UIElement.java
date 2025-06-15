package org.foxesworld.cge.modules.ui.novaUi.elements;

import com.jme3.scene.Node;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;

/**
 * Base interface for all UI elements (text, image, button, panel).
 */
public interface UIElement {
    /** Unique identifier of this element (from XML attribute "id"). */
    String getId();

    /** Node containing the visual representation of this element. */
    Node getNode();

    /**
     * Whether this element specifies its own alignment/anchor
     * (for example, TextElement might have align="center").
     */
    boolean hasOwnAlign();

    /**
     * If hasOwnAlign() == true, returns the alignment string
     * (e.g., "center", "top-right", or "100,50").
     */
    String getOwnAlign();

    /** Reference to the parent panel (or null if this element is at root). */
    PanelElement getParentPanel();

    /**
     * Set a property by key (everything that comes from an XML attribute,
     * except reserved: id, type, onClick). Examples: color="1,1,1,1",
     * fontSize="24", posX="100", posY="200", etc.
     */
    void setProperty(String key, String value);

    /**
     * Set an onClick handler (when XML has onClick="methodName").
     * When clicked (inside TextElement/ImageElement), the method
     * will be invoked on the given eventHandlerTarget.
     */
    void setOnClickHandler(String methodName, Object eventHandlerTarget);

    /**
     * Raw X coordinate inside the parent panel. Default returns 0,
     * but TextElement, ImageElement, and PanelElement override this
     * so the layout algorithm can read rawPos.
     */
    default float getRawPosX() {
        return 0f;
    }

    /**
     * Raw Y coordinate inside the parent panel. Default returns 0,
     * but TextElement, ImageElement, and PanelElement override this
     * so the layout algorithm can read rawPos.
     */
    default float getRawPosY() {
        return 0f;
    }

    /** Actual displayed width of this element (in pixels). */
    float getWidth();

    /** Actual displayed height of this element (in pixels). */
    float getHeight();
}
