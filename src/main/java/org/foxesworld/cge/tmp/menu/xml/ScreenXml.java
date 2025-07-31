package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElementRef;
import java.util.List;

/**
 * JAXB model for the <screen> tag in the menu XML.
 * Represents a container for all 2D UI components and defines properties for its background.
 */
public class ScreenXml {

    /**
     * The background color of the screen, specified as a HEX string (e.g., "#RRGGBB").
     * If not specified, the background will be fully transparent.
     */
    @XmlAttribute
    public String bgColor;

    /**
     * The alpha (transparency) of the background color, from 0.0 (fully transparent)
     * to 1.0 (fully opaque).
     */
    @XmlAttribute
    public Float bgAlpha;

    /**
     * A list of all UI components contained within this screen.
     */
    @XmlElementRef
    public List<ComponentXml> components;
}