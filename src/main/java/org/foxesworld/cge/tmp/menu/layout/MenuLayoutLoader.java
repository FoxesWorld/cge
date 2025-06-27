package org.foxesworld.cge.tmp.menu.layout;

import com.jme3.app.Application;
import com.jme3.math.Vector2f;
import org.foxesworld.cge.tmp.menu.layout.components.ElementLayout;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

public class MenuLayoutLoader {

    private static final Logger logger = Logger.getLogger(MenuLayoutLoader.class.getName());

    /**
     * Loads menu element layouts from an XML file in assets.
     * XML format:
     * <menu>
     *   <element id="btnStart" type="button" x="100" y="200" width="300" height="60" text="START"/>
     *   <element id="infoBlock" type="panel" x="800" y="80" width="500" height="400"/>
     *   ...
     * </menu>
     *
     * @param app       the JME application
     * @param assetPath path to the XML in resources
     * @return list of parsed ElementLayout
     */
    public static List<ElementLayout> load(Application app, String assetPath) {
        List<ElementLayout> layouts = new ArrayList<>();
        try (InputStream is = MenuLayoutLoader.class.getClassLoader().getResourceAsStream(assetPath)) {
            if (is == null) {
                logger.severe("Menu layout XML not found: " + assetPath);
                return layouts;
            }

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setIgnoringComments(true); // ignore XML comments
            dbFactory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(is);
            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName("element");
            for (int i = 0; i < nList.getLength(); i++) {
                Node node = nList.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Element el = (Element) node;

                String id = el.getAttribute("id");
                String type = el.getAttribute("type");

                if (id == null || id.isEmpty()) {
                    logger.warning("Skipping element with missing id");
                    continue;
                }
                if (type == null || type.isEmpty()) {
                    logger.warning("Skipping element with missing type: id=" + id);
                    continue;
                }

                float x = parseFloatSafe(el.getAttribute("x"), 0);
                float y = parseFloatSafe(el.getAttribute("y"), 0);
                float w = parseFloatSafe(el.getAttribute("width"), 0);
                float h = parseFloatSafe(el.getAttribute("height"), 0);

                // Collect all attributes into a map
                Map<String, String> attrs = new HashMap<>();
                NamedNodeMap nnm = el.getAttributes();
                for (int j = 0; j < nnm.getLength(); j++) {
                    Node attr = nnm.item(j);
                    attrs.put(attr.getNodeName(), attr.getNodeValue());
                }

                layouts.add(new ElementLayout(id, type, new Vector2f(x, y), new Vector2f(w, h), attrs));
            }
        } catch (Exception e) {
            logger.severe("Error parsing menu layout: " + e.getMessage());
            e.printStackTrace();
        }
        return layouts;
    }

    /**
     * Helper to parse a float with fallback.
     */
    private static float parseFloatSafe(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
