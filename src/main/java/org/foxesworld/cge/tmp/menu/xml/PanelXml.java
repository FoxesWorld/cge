package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.*;

import java.util.List;

/**
 * XML model for a Panel component.
 * A panel is a container that can hold other components, define layout,
 * spacing, background styles, and more.
 */
@XmlRootElement(name = "panel")
@XmlAccessorType(XmlAccessType.FIELD)
public class PanelXml extends ComponentXml {

    // === Identification & Meta ===

    @XmlAttribute
    private String anchor;

    @XmlAttribute(name = "dpiScale")
    private float dpiScale = 1.0f;

    // === Styling ===

    /** Background color (hex or rgba string) */
    @XmlAttribute(name = "bgColor")
    private String bgColor;

    /** Background transparency (0.0 - 1.0) */
    @XmlAttribute(name = "bgAlpha")
    private Float bgAlpha;

    /** Space between edge of panel and inner content */
    @XmlAttribute(name = "padding")
    private Float padding;

    /** Space between components inside the panel */
    @XmlAttribute(name = "spacing")
    private Float spacing;

    /** Radius for rounded corners (in px or units) */
    @XmlAttribute(name = "cornerRadius")
    private Float cornerRadius;

    // === Children Components ===

    /**
     * Contains child components nested in this panel.
     * Each component is polymorphically mapped by its XML tag.
     */
    @XmlElementWrapper(name = "components")
    @XmlElements({
            @XmlElement(name = "button", type = ButtonXml.class),
            @XmlElement(name = "panel", type = PanelXml.class),
            @XmlElement(name = "slider", type = SliderXml.class),
            @XmlElement(name = "checkbox", type = CheckboxXml.class),
            @XmlElement(name = "text", type = TextXml.class),
            @XmlElement(name = "tabs", type = TabsXml.class)
    })
    private List<ComponentXml> components;

    // === Getters ===

    public String getId() {
        return id;
    }

    public String getAnchor() {
        return anchor;
    }

    public float getDpiScale() {
        return dpiScale;
    }

    public String getBgColor() {
        return bgColor;
    }

    public Float getBgAlpha() {
        return bgAlpha;
    }

    public Float getPadding() {
        return padding;
    }

    public Float getSpacing() {
        return spacing;
    }

    public Float getCornerRadius() {
        return cornerRadius;
    }

    public List<ComponentXml> getComponents() {
        return components;
    }

    // === Setters (optional for future runtime UI editing support) ===

    public void setId(String id) {
        this.id = id;
    }

    public void setAnchor(String anchor) {
        this.anchor = anchor;
    }

    public void setDpiScale(float dpiScale) {
        this.dpiScale = dpiScale;
    }

    public void setBgColor(String bgColor) {
        this.bgColor = bgColor;
    }

    public void setBgAlpha(Float bgAlpha) {
        this.bgAlpha = bgAlpha;
    }

    public void setPadding(Float padding) {
        this.padding = padding;
    }

    public void setSpacing(Float spacing) {
        this.spacing = spacing;
    }

    public void setCornerRadius(Float cornerRadius) {
        this.cornerRadius = cornerRadius;
    }

    public void setComponents(List<ComponentXml> components) {
        this.components = components;
    }
}
