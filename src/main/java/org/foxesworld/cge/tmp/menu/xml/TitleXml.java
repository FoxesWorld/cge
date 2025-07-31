package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "title")
public class TitleXml extends ComponentXml {
    @XmlAttribute public float fontSize = 96;
}