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
 * UIXmlParser — отвечает за:
 *  • загрузку и нормализацию XML
 *  • чтение глобальных параметров (шрифт)
 *  • рекурсивное построение панели и вложенных элементов
 *  • наполнение Map<id, UIElement> для UIPanel
 *
 * Теперь без «жёстко захардкоженных» названий атрибутов:
 * все атрибуты панели/элемента пробрасываются через setProperty(...),
 * кроме служебных: "id", "type", "onClick".
 */
public class UIXmlParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIXmlParser.class);

    // XML-теги и служебные атрибуты
    private static final String TAG_FONT    = "Font";
    private static final String TAG_PANEL   = "Panel";
    private static final String TAG_ELEMENT = "Element";

    private static final String ATTR_ID       = "id";
    private static final String ATTR_TYPE     = "type";
    private static final String ATTR_ONCLICK  = "onClick";

    private final CalistaGameEngine engine;
    private final String configPath;

    // Глобальные значения, задаются из <Font> или остаются по умолчанию
    private String defaultFontPath = "Interface/Fonts/Default.fnt";
    private float  defaultFontSize = 20f;

    public UIXmlParser(CalistaGameEngine engine, String configPath) {
        this.engine = engine;
        this.configPath = configPath;
    }

    /**
     * Выполняет разбор XML-конфигурации и возвращает корневую панель вместе
     * со всей картой элементов (id → UIElement) и настройками шрифта.
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

    /** Считывает глобальный блок <Font defaultPath="..." defaultSize="..."/> */
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
     * Рекурсивное построение одной панели и всех вложенных в неё элементов/панелей.
     * Заполняет карту allElements, вызывая panel.setProperty(...) и element.setProperty(...).
     */
    private PanelElement buildPanelRecursive(Element panelEl,
                                             PanelElement parent,
                                             Map<String, UIElement> allElements) {
        // 1) Создаем PanelElement с id
        String panelId = panelEl.getAttribute(ATTR_ID);
        PanelElement panel = new PanelElement(engine, panelId, parent);
        allElements.put(panelId, panel);

        // 2) Назначаем все атрибуты панели
        NamedNodeMap attrs = panelEl.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String name = attr.getName();
            String value = attr.getValue();

            // Пропускаем служебные
            if (ATTR_ID.equals(name)) {
                continue;
            }
            // В PanelElement есть обработчики для margin, padding, bgColor, width, height, align, layout, spacing
            panel.setProperty(name, value);
        }

        // 3) Построить фон (1×1 quad)
        //panel.();

        // 4) Обойти дочерние узлы <Element> и <Panel>
        ElementFactory factory = new ElementFactory(engine);
        NodeList children = panelEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childEl = (Element) node;
            switch (childEl.getTagName()) {
                case TAG_ELEMENT -> {
                    // id + type обязательны для <Element>
                    String childId   = childEl.getAttribute(ATTR_ID);
                    String childType = childEl.getAttribute(ATTR_TYPE);

                    UIElement uiEl = factory.create(childEl, panel, defaultFontPath, defaultFontSize);
                    if (uiEl != null) {
                        // Назначаем все атрибуты (кроме id/type/onClick) через setProperty
                        NamedNodeMap elAttrs = childEl.getAttributes();
                        for (int j = 0; j < elAttrs.getLength(); j++) {
                            Attr a = (Attr) elAttrs.item(j);
                            String n = a.getName();
                            String v = a.getValue();
                            if (ATTR_ID.equals(n) || ATTR_TYPE.equals(n)) {
                                continue;
                            }
                            if (ATTR_ONCLICK.equals(n)) {
                                // Отдельно обрабатываем onClick
                                //uiEl.setOnClickHandler(v, factory.getEventHandlerTarget());
                            } else {
                                uiEl.setProperty(n, v);
                            }
                        }
                        panel.addChild(uiEl);
                        allElements.put(childId, uiEl);
                    } else {
                        LOGGER.warn("Failed to create UIElement for <Element id='{}' type='{}'>",
                                childId, childType);
                    }
                }
                case TAG_PANEL -> {
                    // Вложенная панель
                    PanelElement subPanel = buildPanelRecursive(childEl, panel, allElements);
                    panel.addChild(subPanel);
                }
                default -> {
                    LOGGER.warn("Unknown tag '{}' inside <Panel id='{}'>", childEl.getTagName(), panelId);
                }
            }
        }

        // 5) Первый layout/позиционирование детей
        panel.recomputeSizeAndRepositionChildren();
        return panel;
    }

    private Document loadXmlDocument(String path) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new RuntimeException("Cannot load UI XML: " + path);
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(is);
        doc.getDocumentElement().normalize();
        return doc;
    }

    /** Результат парсинга: корневая панель, карта всех элементов, настройки шрифта. */
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
