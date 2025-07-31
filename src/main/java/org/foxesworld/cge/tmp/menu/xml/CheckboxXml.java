// В файле ...menu.xml/CheckboxXml.java

package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "checkbox")
public class CheckboxXml extends ComponentXml {
    @XmlAttribute public boolean checked = false;
    @XmlAttribute public String bind;

    // НОВЫЙ АТРИБУТ с значением по умолчанию
    @XmlAttribute public String size = "30";
}