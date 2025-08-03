// Поместите этот файл в пакет org.foxesworld.cge.tmp.menu.xml
package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.*;
import java.util.List;

@XmlRootElement(name = "panel")
@XmlAccessorType(XmlAccessType.FIELD)
public class PanelXml extends ComponentXml {

    @XmlAttribute
    public String width;

    @XmlAttribute
    public String height;

    @XmlAttribute
    public Float padding;

    @XmlAttribute
    public Float spacing;

    // JAXB магия: собирает все дочерние XML-элементы (button, title, etc.) в этот список
    @XmlAnyElement(lax = true)
    public List<ComponentXml> components;
}