package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "slider")
public class SliderXml extends ComponentXml {
    @XmlAttribute public float value = 1.0f;
    @XmlAttribute public float width = 300f;
    @XmlAttribute public String fillColor = "";
    @XmlAttribute public String bind; // Для будущей привязки к настройкам
}