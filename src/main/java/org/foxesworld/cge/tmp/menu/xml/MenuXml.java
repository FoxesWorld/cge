package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "menu")
public class MenuXml {
    @XmlElement public SceneXml scene;
    @XmlElement public ScreenXml screen;
}