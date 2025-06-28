package org.foxesworld.cge.modules.ui.novaUi.xml;

import org.foxesworld.cge.modules.ui.novaUi.UINodeDefinition;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class UIXmlParser {

    public UINodeDefinition parse(InputStream inputStream) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
        doc.getDocumentElement().normalize();
        Element rootXmlNode = doc.getDocumentElement();
        return parseRecursive(rootXmlNode);
    }

    private UINodeDefinition parseRecursive(Element element) {
        String type = element.getTagName();
        Map<String, String> attributes = new HashMap<>();
        NamedNodeMap attributeNodes = element.getAttributes();

        for (int i = 0; i < attributeNodes.getLength(); i++) {
            Node attr = attributeNodes.item(i);
            attributes.put(attr.getNodeName(), attr.getNodeValue());
        }

        UINodeDefinition definition = new UINodeDefinition(type, attributes);

        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                definition.addChild(parseRecursive((Element) node));
            }
        }
        return definition;
    }
}