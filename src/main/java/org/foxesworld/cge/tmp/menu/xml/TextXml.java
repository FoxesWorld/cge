package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "text")
public class TextXml extends ComponentXml {
    @XmlAttribute public String fontSize = "20%";
    @XmlAttribute public String fontPath = "";
}