package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElementRef;
import java.util.List;

public class TabXml extends ComponentXml {

    @XmlAttribute public String iconPath;
    @XmlAttribute public float iconSize = 50f;
    @XmlAttribute public String title;
    @XmlElementRef
    public List<ComponentXml> components; // Вкладка может содержать другие компоненты
}