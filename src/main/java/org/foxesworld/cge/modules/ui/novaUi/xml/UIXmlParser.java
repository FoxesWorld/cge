package org.foxesworld.cge.modules.ui.novaUi.xml;

import org.foxesworld.cge.modules.ui.novaUi.UINodeDefinition;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses an XML UI layout file into a tree of UINodeDefinition objects.
 * Supports a flexible layout syntax where element properties can be defined
 * either as attributes on the element itself or as child <property> tags.
 */
public class UIXmlParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIXmlParser.class);

    /**
     * Parses an XML input stream into a root UINodeDefinition.
     * @param inputStream The XML file stream.
     * @return The root node of the UI definition tree.
     * @throws Exception If parsing fails.
     */
    public UINodeDefinition parse(InputStream inputStream) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
        doc.getDocumentElement().normalize();
        Element rootXmlNode = doc.getDocumentElement();
        return parseRecursive(rootXmlNode);
    }

    /**
     * Recursively parses an XML Element into a UINodeDefinition.
     * This method distinguishes between child elements that are UI components
     * (like <Panel>, <Text>) and those that are <property> declarations.
     *
     * @param element The XML element to parse.
     * @return A UINodeDefinition representing the element and its children.
     */
    private UINodeDefinition parseRecursive(Element element) {
        String type = element.getTagName();
        Map<String, String> properties = new HashMap<>();
        List<Element> childUiElements = new ArrayList<>();

        // --- Stage 1: GATHER ALL PROPERTIES & SEPARATE UI CHILDREN ---

        // First, read attributes directly from the element tag (for hybrid style, e.g., <Text id="...">)
        // This makes 'id' a standard attribute, which is common practice.
        if (element.hasAttribute("id")) {
            properties.put("id", element.getAttribute("id"));
        }

        // Now, iterate through all child nodes to find <property> tags and other UI elements
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;

                // Check if the child is a <property> tag
                if ("property".equalsIgnoreCase(childElement.getTagName())) {
                    String name = childElement.getAttribute("name");
                    String value = childElement.getAttribute("value");

                    // Allow property value to be defined as text content for multiline values
                    if ((value == null || value.isEmpty()) && childElement.hasChildNodes()) {
                        value = childElement.getTextContent().trim();
                    }

                    if (name != null && !name.isEmpty() && value != null) {
                        properties.put(name, value);
                    } else {
                        LOGGER.warn("Skipping invalid <property> tag in element <{}>. Missing 'name' or 'value'.", type);
                    }
                } else {
                    // If it's not a <property> tag, it's a nested UI element.
                    // Store it for later recursive parsing.
                    childUiElements.add(childElement);
                }
            }
        }

        // --- Stage 2: CREATE THE DEFINITION AND PROCESS UI CHILDREN ---

        // Create the definition object with all collected properties
        UINodeDefinition definition = new UINodeDefinition(type, properties);

        // Recursively parse the stored child UI elements and add them to the definition
        for (Element uiChild : childUiElements) {
            definition.addChild(parseRecursive(uiChild));
        }

        return definition;
    }
}