package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "button")
public class ButtonXml extends ComponentXml {

    @XmlAttribute
    public String action;

    @XmlAttribute
    public float fontSize = 42;

    /**
     * Constructor to set default values for button dimensions.
     * These will be used if 'width' or 'height' are not specified in the XML tag.
     */
    public ButtonXml() {
        // Устанавливаем значения по умолчанию
        this.width = "400";
        this.height = "55";
    }
}