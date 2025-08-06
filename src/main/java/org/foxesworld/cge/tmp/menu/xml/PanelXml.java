package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.*;

import java.util.List;

/**
 * XML model for a Panel component. A panel acts as a container for other components
 * and can have its own background color.
 */
@XmlRootElement(name = "panel")
public class PanelXml extends ComponentXml {// Важно: наследуемся от ComponentXml

    @XmlAttribute
    private float dpiScale;

    @XmlAttribute
    private String anchor;
    @XmlAttribute
    private String id;

    @XmlAttribute
    public String bgColor;

    @XmlAttribute
    public Float bgAlpha;

    @XmlAttribute
    public Float padding;

    @XmlAttribute
    public Float spacing;

    @XmlAttribute
    public Float cornerRadius;

    /**
     * This annotation specifies that the list of components is wrapped
     * within a single <components> XML element.
     */
    @XmlElementWrapper(name = "components")
    /**
     * This annotation tells JAXB how to map the inner XML tags (like <button>, <panel>)
     * to concrete Java classes. This is crucial for handling polymorphism.
     */
    @XmlElements({
            @XmlElement(name = "button", type = ButtonXml.class),
            @XmlElement(name = "panel", type = PanelXml.class),
            @XmlElement(name = "slider", type = SliderXml.class),
            @XmlElement(name = "checkbox", type = CheckboxXml.class),
            @XmlElement(name = "title", type = TitleXml.class),
            @XmlElement(name = "tabs", type = TabsXml.class)
    })
    public List<ComponentXml> components;

    public String getId() {
        return id;
    }

    public String getAnchor() {
        return anchor;
    }

    public float getDpiScale() {
        return dpiScale;
    }
}