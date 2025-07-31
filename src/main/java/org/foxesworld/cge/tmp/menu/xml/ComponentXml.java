package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * An abstract base class for all UI components defined in the menu XML.
 * <p>
 * This class holds common attributes that are shared across all components,
 * such as positioning (x, y), alignment, and dimensions (width, height).
 * Using string types for these attributes allows for flexible values, including
 * absolute pixels and percentages.
 */
public abstract class ComponentXml {

    /**
     * The display text for the component (e.g., a button's label, a title's content).
     */
    @XmlAttribute
    public String text;

    /**
     * The horizontal position or offset of the component.
     * Can be an absolute value. Defaults to "0".
     */
    @XmlAttribute
    public String x = "0";

    /**
     * The vertical position of the component.
     * Can be an absolute value or a percentage (e.g., "80%"). Defaults to "0".
     */
    @XmlAttribute
    public String y = "0";

    /**
     * The horizontal alignment of the component.
     * Common values are "CENTER_X", "LEFT", "RIGHT".
     */
    @XmlAttribute
    public String align;

    /**
     * The width of the component.
     * Can be an absolute value or a percentage. Can be null if not specified.
     */
    @XmlAttribute
    public String width;

    /**
     * The height of the component.
     * Can be an absolute value or a percentage. Can be null if not specified.
     */
    @XmlAttribute
    public String height;
}