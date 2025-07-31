package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name = "tabs")
public class TabsXml extends ComponentXml {
    @XmlAttribute public String orientation = "HORIZONTAL"; // По умолчанию
    @XmlAttribute public float contentWidth = 600;
    @XmlAttribute public float contentHeight = 400;

    @XmlElement(name = "tab")
    public List<TabXml> tabs;
}