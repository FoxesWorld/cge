package org.foxesworld.cge.modules.ui.novaUi;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.ElementFactory;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.*;

/**
 * UIXmlParser is responsible for:
 *  • loading and normalizing XML
 *  • reading global parameters (font)
 *  • recursive construction of panels and nested elements
 *  • filling Map<id, UIElement> for UIPanel
 *
 * All panel/element attributes are passed through setProperty(...),
 * except for service ones: "id", "type", "onClick".
 */
public class UIXmlParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIXmlParser.class);

    // XML tags and service attributes
    private static final String TAG_FONT    = "Font";
    private static final String TAG_PANEL   = "Panel";
    private static final String TAG_ELEMENT = "Element";

    private static final String ATTR_ID       = "id";
    private static final String ATTR_TYPE     = "type";
    private static final String ATTR_ONCLICK  = "onClick";

    private final CalistaGameEngine engine;
    private final String configPath;

    // Global values set from <Font> or use defaults
    private String defaultFontPath = "Interface/Fonts/Default.fnt";
    private float  defaultFontSize = 20f;

    // Optimization: cache factory instance
    private final ElementFactory elementFactory;

    public UIXmlParser(CalistaGameEngine engine, String configPath) {
        this.engine = Objects.requireNonNull(engine);
        this.configPath = Objects.requireNonNull(configPath);
        this.elementFactory = new ElementFactory(engine);
    }

    /**
     * Parses the XML configuration and returns the root panel along with
     * the map of all elements (id → UIElement) and font settings.
     */
    public ParseResult parse() throws Exception {
        LOGGER.info("UIXmlParser: loading XML '{}'", configPath);
        Document doc = loadXmlDocument(configPath);
        Element root = doc.getDocumentElement();

        parseFontSettings(root);

        NodeList panelTags = root.getElementsByTagName(TAG_PANEL);
        if (panelTags.getLength() == 0) {
            throw new RuntimeException("UI XML must contain at least one <Panel> element");
        }
        Element rootPanelEl = (Element) panelTags.item(0);

        Map<String, UIElement> allElements = new LinkedHashMap<>();
        PanelElement rootPanel = buildPanelRecursive(rootPanelEl, null, allElements);

        return new ParseResult(rootPanel, allElements, defaultFontPath, defaultFontSize);
    }

    /** Reads global <Font defaultPath="..." defaultSize="..."/> block */
    private void parseFontSettings(Element root) {
        NodeList fonts = root.getElementsByTagName(TAG_FONT);
        if (fonts.getLength() == 0) {
            LOGGER.debug("No <Font> tag; using defaults ({} / {})", defaultFontPath, defaultFontSize);
            return;
        }
        Element fontEl = (Element) fonts.item(0);
        if (fontEl.hasAttribute("defaultPath")) {
            defaultFontPath = fontEl.getAttribute("defaultPath");
            LOGGER.debug("Default fontPath -> {}", defaultFontPath);
        }
        if (fontEl.hasAttribute("defaultSize")) {
            try {
                defaultFontSize = Float.parseFloat(fontEl.getAttribute("defaultSize"));
                LOGGER.debug("Default fontSize -> {}", defaultFontSize);
            } catch (NumberFormatException nfe) {
                LOGGER.warn("Invalid defaultSize='{}', keeping {}",
                        fontEl.getAttribute("defaultSize"), defaultFontSize);
            }
        }
    }

    /**
     * Recursively builds a panel and all its nested elements/panels.
     * Fills the allElements map, calling panel.setProperty(...) and element.setProperty(...).
     */
    private PanelElement buildPanelRecursive(Element panelEl,
                                             PanelElement parent,
                                             Map<String, UIElement> allElements) {
        // 1) Create PanelElement with id
        String panelId = panelEl.getAttribute(ATTR_ID);
        if (panelId == null || panelId.isEmpty()) {
            throw new IllegalArgumentException("<Panel> missing required 'id' attribute");
        }
        PanelElement panel = new PanelElement(engine, panelId, parent);
        allElements.put(panelId, panel);

        // 2) Assign all panel attributes except service ones
        NamedNodeMap attrs = panelEl.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String name = attr.getName();
            if (!ATTR_ID.equals(name)) {
                panel.setProperty(name, attr.getValue());
            }
        }

        // 3) Traverse child <Element> and <Panel> nodes
        NodeList children = panelEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element childEl = (Element) node;
            switch (childEl.getTagName()) {
                case TAG_ELEMENT -> handleElementTag(childEl, panel, allElements);
                case TAG_PANEL -> {
                    PanelElement subPanel = buildPanelRecursive(childEl, panel, allElements);
                    panel.addChild(subPanel);
                }
                default -> LOGGER.warn("Unknown tag '{}' inside <Panel id='{}'>", childEl.getTagName(), panelId);
            }
        }

        // 4) First layout/positioning of children
        panel.recomputeSizeAndRepositionChildren();
        return panel;
    }

    /**
     * Handles <Element> tag: creates element, assigns properties, and attaches to parent panel.
     */
    private void handleElementTag(Element childEl, PanelElement parentPanel, Map<String, UIElement> allElements) {
        String childId   = childEl.getAttribute(ATTR_ID);
        String childType = childEl.getAttribute(ATTR_TYPE);

        if (childId == null || childId.isEmpty()) {
            LOGGER.warn("<Element> missing required 'id' attribute; skipping");
            return;
        }
        if (childType == null || childType.isEmpty()) {
            LOGGER.warn("<Element id='{}'> missing required 'type' attribute; skipping", childId);
            return;
        }

        UIElement uiEl = elementFactory.create(childEl, parentPanel, defaultFontPath, defaultFontSize);
        if (uiEl != null) {
            // Assign all attributes (except id/type/onClick) via setProperty
            NamedNodeMap elAttrs = childEl.getAttributes();
            for (int j = 0; j < elAttrs.getLength(); j++) {
                Attr a = (Attr) elAttrs.item(j);
                String n = a.getName();
                if (ATTR_ID.equals(n) || ATTR_TYPE.equals(n) || ATTR_ONCLICK.equals(n)) continue;
                uiEl.setProperty(n, a.getValue());
            }
            parentPanel.addChild(uiEl);
            allElements.put(childId, uiEl);
        } else {
            LOGGER.warn("Failed to create UIElement for <Element id='{}' type='{}'>", childId, childType);
        }
    }

    private Document loadXmlDocument(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Cannot load UI XML: " + path);
            }
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(is);
            doc.getDocumentElement().normalize();
            return doc;
        }
    }

    /** Parse result: root panel, all elements map, font settings. */
    public static class ParseResult {
        public final PanelElement rootPanel;
        public final Map<String, UIElement> allElements;
        public final String defaultFontPath;
        public final float defaultFontSize;

        public ParseResult(PanelElement rootPanel,
                           Map<String, UIElement> allElements,
                           String defaultFontPath,
                           float defaultFontSize) {
            this.rootPanel      = rootPanel;
            this.allElements    = allElements;
            this.defaultFontPath = defaultFontPath;
            this.defaultFontSize = defaultFontSize;
        }
    }
}