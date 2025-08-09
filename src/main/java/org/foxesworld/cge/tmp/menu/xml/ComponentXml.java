package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * Abstract base class for all UI components defined in menu XML.
 * <p>
 * Provides common attributes such as position, size, alignment, and color.
 * Position and size can be specified in absolute pixels or percentages (e.g., "50%").
 * When width or height are omitted, components may auto-fit based on their content or container.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class ComponentXml {

    /** Optional text content (e.g., button label, title). */
    @XmlAttribute
    public String text;

    @XmlAttribute
    private String anchor;

    @XmlAttribute
    public String id;

    /**
     * Horizontal position or offset.
     * Can be an absolute value (e.g., "100") or percentage (e.g., "50%").
     * Defaults to "0".
     */
    @XmlAttribute
    public String x = "0";

    /**
     * Vertical position or offset.
     * Can be an absolute value or percentage (e.g., "80%").
     * Defaults to "0".
     */
    @XmlAttribute
    public String y = "0";

    /**
     * Horizontal alignment relative to the container.
     * Common values: "LEFT", "CENTER_X", "RIGHT".
     */
    @XmlAttribute
    public String alignX;

    /**
     * Vertical alignment relative to the container.
     * Common values: "TOP", "CENTER_Y", "BOTTOM".
     */
    @XmlAttribute
    public String alignY;

    /**
     * Width of the component.
     * Can be an absolute value, percentage (e.g., "100%"), or omitted for auto-fit.
     */
    @XmlAttribute
    public String width;

    /**
     * Height of the component.
     * Can be an absolute value, percentage, or omitted for auto-fit.
     */
    @XmlAttribute
    public String height;

    /** Optional foreground or main color in HEX format (e.g., "#FFFFFF"). */
    @XmlAttribute
    public String color;

    public void setAnchor(String anchor) {
        this.anchor = anchor;
    }

    public String getAnchor() {
        return anchor;
    }
}
