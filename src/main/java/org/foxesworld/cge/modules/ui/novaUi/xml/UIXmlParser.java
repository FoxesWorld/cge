package org.foxesworld.cge.modules.ui.novaUi.xml;

import org.foxesworld.cge.modules.ui.novaUi.UINodeDefinition;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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
 * Parses an XML UI layout file into an immutable tree of UINodeDefinition objects.
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
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // It's good practice to disable DTDs to prevent XXE vulnerabilities
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        Document doc = factory.newDocumentBuilder().parse(inputStream);
        doc.getDocumentElement().normalize();
        Element rootXmlNode = doc.getDocumentElement();
        return parseRecursive(rootXmlNode);
    }

    /**
     * Recursively parses an XML Element into an immutable UINodeDefinition.
     * This method gathers all properties and then recursively builds a list of
     * child definitions before creating the final UINodeDefinition object.
     *
     * @param element The XML element to parse.
     * @return A new, immutable UINodeDefinition representing the element and its children.
     */
    private UINodeDefinition parseRecursive(Element element) {
        String type = element.getTagName();
        Map<String, String> attributes = new HashMap<>();
        List<UINodeDefinition> childDefinitions = new ArrayList<>();

        // --- Stage 1: GATHER ALL PROPERTIES ---

        // First, read all attributes directly from the element tag (e.g., <Panel id="..." width="...">)
        NamedNodeMap attributeNodes = element.getAttributes();
        for (int i = 0; i < attributeNodes.getLength(); i++) {
            Node attr = attributeNodes.item(i);
            attributes.put(attr.getNodeName(), attr.getNodeValue());
        }

        // --- Stage 2: PROCESS CHILD NODES ---
        // Iterate through all child nodes to find <property> tags and other UI elements.
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
                        if (attributes.containsKey(name)) {
                            LOGGER.warn("Property '{}' for element <{}> was defined as both an attribute and a <property> tag. The <property> tag will override the attribute.", name, type);
                        }
                        attributes.put(name, value);
                    } else {
                        LOGGER.warn("Skipping invalid <property> tag in element <{}>. Missing 'name' or 'value'.", type);
                    }
                } else {
                    // If it's not a <property> tag, it's a nested UI element.
                    // Recursively parse it and add the resulting definition to our list.
                    childDefinitions.add(parseRecursive(childElement));
                }
            }
        }

        // --- Stage 3: CREATE THE FINAL IMMUTABLE DEFINITION ---
        // Create the definition object, passing the collected attributes and the
        // fully constructed list of child definitions to the constructor.
        return new UINodeDefinition(type, attributes, childDefinitions);
    }
}