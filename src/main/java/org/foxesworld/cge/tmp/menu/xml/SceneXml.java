package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAttribute;

public class SceneXml {
    @XmlAttribute public String modelPath;
    @XmlAttribute public String skyboxPath;
    @XmlAttribute public Float modelScale;
    @XmlAttribute public Float modelOffsetX;
    @XmlAttribute public Float modelOffsetY;
    @XmlAttribute public Float modelOffsetZ;
    @XmlAttribute public Float cameraDistance;
    @XmlAttribute public Float cameraHeight;
    @XmlAttribute public Float lookAtY;
}