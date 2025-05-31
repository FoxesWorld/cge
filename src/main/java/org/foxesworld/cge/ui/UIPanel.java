package org.foxesworld.cge.ui;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.elements.ImageElement;
import org.foxesworld.cge.ui.elements.PanelElement;
import org.foxesworld.cge.ui.elements.TextElement;
import org.foxesworld.cge.ui.elements.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

/**
 * UIPanel: универсальный класс для создания и управления
 * иерархией UI-панелей и элементов на основе XML-конфигурации.
 *
 * Убраты «хардкод»-значения, улучшена отзывчивость и стабильность:
 * - Кэширование последних значений текстовых полей (чтобы не читать BitmapText каждый кадр)
 * - Сбор «панелей на пересчёт» и единоразовый вызов reposition в update()
 * - Вынесены конфигурационные константы
 */
public class UIPanel extends BaseAppState {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIPanel.class);

    // --- Жёстко зашитые значения вынесены в константы или задаются через XML ---
    private static final String    XML_FONT_TAG          = "Font";
    private static final String    XML_PANEL_TAG         = "Panel";
    private static final String    XML_ELEMENT_TAG       = "Element";

    // Значения по умолчанию, пока не переопределены из XML
    private static final String    DEFAULT_FONT_PATH     = "Interface/Fonts/Default.fnt";
    private static final float     DEFAULT_FONT_SIZE     = 20f;
    private static final ColorRGBA DEFAULT_BG_COLOR      = new ColorRGBA(0f, 0f, 0f, 0.5f);

    private final CalistaGameEngine       calistaGameEngine;
    private final Node                    guiNode;
    private final String                  configPath;

    private Camera                         cam;
    private Node                           rootPanelNode = new Node("UIPanelRoot");

    // Текущие «конфигурационные» значения (инициализируются из XML)
    private String                         defaultFontPath = DEFAULT_FONT_PATH;
    private float                          defaultFontSize = DEFAULT_FONT_SIZE;
    private ColorRGBA                      defaultBgColor  = DEFAULT_BG_COLOR.clone();

    /** Все созданные UI-элементы (id → элемент) **/
    private final Map<String, UIElement>   allElements = new LinkedHashMap<>();

    /** Обработчик событий, чьи поля могут биндиться к TextElement **/
    private Object                         eventHandlerTarget = null;

    /**
     * Для каждого TextElement храним:
     *  1) поле Field (методом reflect)
     *  2) последнее известное значение (String), чтобы не читать BitmapText'ы каждый кадр
     *
     * После того, как текст меняется, мы кладём в эту мапу новое значение.
     * В update() сравниваем новое → старое; если отличается, обновляем.
     */
    private final Map<TextElement, Field>        boundTextFields       = new LinkedHashMap<>();
    private final Map<TextElement, String>       lastKnownTextValues   = new HashMap<>();

    /** Панели (PanelElement), которым нужно сделать recomputeSizeAndRepositionChildren() в конце update() **/
    private final Set<PanelElement>               dirtyPanels          = new LinkedHashSet<>();

    public UIPanel(CalistaGameEngine calistaGameEngine, String configPath) {
        this.calistaGameEngine = calistaGameEngine;
        this.guiNode           = calistaGameEngine.getGuiNode();
        this.configPath        = configPath;
        LOGGER.info("UIPanel created (config = {})", configPath);
    }

    /**
     * Регистрируем объект-обработчик (HUDController, MainApp и т.п.).
     * После разбора XML и создания всех элементов UIPanel попытается
     * биндинговать текстовые поля UI к полям в этом объекте-обработчике.
     */
    public void registerEventHandler(Object handler) {
        this.eventHandlerTarget = handler;
        LOGGER.info("Event handler registered: {}", handler.getClass().getSimpleName());
    }

    @Override
    protected void initialize(Application app) {
        this.cam = app.getCamera();
        LOGGER.info("Initializing UIPanel: camera = {}×{}", cam.getWidth(), cam.getHeight());

        try {
            parseXmlAndBuildUI();
            guiNode.attachChild(rootPanelNode);
            // После построения сразу расставим панели по экрану
            repositionAllRootPanels();
            LOGGER.info("UIPanel successfully initialized and attached to guiNode.");
        } catch (Exception e) {
            LOGGER.error("UIPanel initialization failed: ", e);
            throw new RuntimeException("Cannot initialize UIPanel", e);
        }
    }

    @Override
    protected void cleanup(Application app) {
        guiNode.detachChild(rootPanelNode);
        LOGGER.info("UIPanel cleaned up (detached from guiNode).");
    }

    @Override
    protected void onEnable() {
        LOGGER.debug("UIPanel enabled.");
    }

    @Override
    protected void onDisable() {
        LOGGER.debug("UIPanel disabled.");
    }

    /**
     * Вариант ручной установки текста для TextElement по id.
     * Сразу отмечаем родительскую панель «грязной» (dirty) для пересчёта в конце update().
     */
    public void setText(String id, String newText) {
        UIElement e = allElements.get(id);
        if (e instanceof TextElement te) {
            LOGGER.debug("setText: id='{}' newText='{}'", id, newText);
            te.setText(newText);
            // Обновляем локальное кэшированное значение
            lastKnownTextValues.put(te, newText);
            markDirty(te.getParentPanel());
        } else {
            LOGGER.warn("setText: element '{}' not found or not a TextElement", id);
        }
    }

    /**
     * Установка произвольного свойства (цвета, размера, позиции и т.п.) по id.
     * Если это TextElement или ImageElement — помечаем родительскую панель на пересчёт.
     */
    public void setProperty(String id, String propKey, String propValue) {
        UIElement e = allElements.get(id);
        if (e == null) {
            LOGGER.warn("setProperty: element '{}' not found", id);
            return;
        }
        LOGGER.debug("setProperty: id='{}' key='{}' value='{}'", id, propKey, propValue);
        e.setProperty(propKey, propValue);

        if (e instanceof TextElement || e instanceof ImageElement) {
            markDirty(e.getParentPanel());
        }
    }

    /**
     * Получить ссылку на UI-элемент по его id. Может вернуть PanelElement, TextElement или ImageElement.
     */
    public UIElement getElement(String id) {
        return allElements.get(id);
    }

    //-------------------------------------------------------
    // Основной «парсинг» и построение UI из XML-конфигурации
    //-------------------------------------------------------
    private void parseXmlAndBuildUI() throws Exception {
        LOGGER.info("Parsing UI XML: '{}'", configPath);
        Document doc = loadXmlDocument(configPath);

        Element root = doc.getDocumentElement();
        parseGlobalFontSettings(root);

        // Ищем корневую панель (<Panel> внутри <UI>)
        NodeList panelNodes = root.getElementsByTagName(XML_PANEL_TAG);
        if (panelNodes.getLength() == 0) {
            throw new RuntimeException("UI XML must contain at least one <Panel> element");
        }
        Element topPanelEl = (Element) panelNodes.item(0);

        LOGGER.info("Creating top-level panel with id='{}'", topPanelEl.getAttribute("id"));
        PanelElement topPanel = createPanelFromXml(topPanelEl, null);
        rootPanelNode.attachChild(topPanel.getNode());

        // Биндим текстовые поля к объекту-обработчику (если он задан)
        bindAllTextFields();
        LOGGER.info("UI construction completed (elements counted = {}).", allElements.size());
    }

    /**
     * Чтение глобальных настроек шрифта (<Font defaultPath="..." defaultSize="..."/>).
     * Если тега нет или атрибуты отсутствуют, остаются прежние константы.
     */
    private void parseGlobalFontSettings(Element root) {
        NodeList fonts = root.getElementsByTagName(XML_FONT_TAG);
        if (fonts.getLength() == 0) {
            LOGGER.debug("No <Font> tag found; using defaults (path='{}', size={}).", defaultFontPath, defaultFontSize);
            return;
        }
        Element fontEl = (Element) fonts.item(0);
        if (fontEl.hasAttribute("defaultPath")) {
            defaultFontPath = fontEl.getAttribute("defaultPath");
            LOGGER.debug("Default font path overridden = '{}'", defaultFontPath);
        }
        if (fontEl.hasAttribute("defaultSize")) {
            try {
                defaultFontSize = Float.parseFloat(fontEl.getAttribute("defaultSize"));
                LOGGER.debug("Default font size overridden = {}", defaultFontSize);
            } catch (NumberFormatException nfe) {
                LOGGER.warn("Invalid defaultSize in <Font>: '{}'; keeping {}", fontEl.getAttribute("defaultSize"), defaultFontSize);
            }
        }
    }

    /**
     * Рекурсивное создание PanelElement из XML-узла <Panel> и вложенных <Element> / <Panel>.
     * Собираем атрибуты, создаём фон, обрабатываем детей, сразу пересчитываем размеры для данного узла.
     */
    private PanelElement createPanelFromXml(Element panelEl, PanelElement parentPanel) {
        String id = panelEl.getAttribute("id");
        PanelElement panel = new PanelElement(calistaGameEngine, id, parentPanel);
        allElements.put(id, panel);

        // margin и padding
        panel.setMargin(parseFloatOrDefault(panelEl.getAttribute("margin"), 0f));
        panel.setPadding(parseFloatOrDefault(panelEl.getAttribute("padding"), 0f));

        // bgColor
        if (panelEl.hasAttribute("bgColor")) {
            panel.setBgColor(parseColorOrDefault(panelEl.getAttribute("bgColor"), defaultBgColor));
        } else {
            panel.setBgColor(defaultBgColor.clone());
        }

        // width/height
        String wAttr = panelEl.getAttribute("width");
        if (isNonEmptyAndNotAuto(wAttr)) {
            panel.setFixedWidth(Float.parseFloat(wAttr));
        } else {
            panel.setAutoWidth(true);
        }

        String hAttr = panelEl.getAttribute("height");
        if (isNonEmptyAndNotAuto(hAttr)) {
            panel.setFixedHeight(Float.parseFloat(hAttr));
        } else {
            panel.setAutoHeight(true);
        }

        // align
        if (panelEl.hasAttribute("align")) {
            panel.setAlign(panelEl.getAttribute("align"));
        } else {
            panel.setAlign("top-left");
        }

        // Создаём фон (Quad 1×1, материал и т. д.)
        panel.buildBackgroundGeom();

        // Кэшируем ElementFactory, чтобы не создавать каждый раз новый
        ElementFactory elementFactory = new ElementFactory(calistaGameEngine);

        // Проходим по всем дочерним узлам (<Element> и вложенные <Panel>)
        NodeList children = panelEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) child;
            switch (el.getTagName()) {
                case XML_ELEMENT_TAG -> {
                    UIElement uiEl = elementFactory.create(el, panel, defaultFontPath, defaultFontSize);
                    if (uiEl != null) {
                        panel.addChild(uiEl);
                        allElements.put(uiEl.getId(), uiEl);

                        // Если указан onClick, сразу вешаем обработчик
                        if (el.hasAttribute("onClick") && eventHandlerTarget != null) {
                            uiEl.setOnClickHandler(el.getAttribute("onClick"), eventHandlerTarget);
                        }
                    } else {
                        LOGGER.warn("Failed to create UIElement for <Element> in panel '{}'", id);
                    }
                }
                case XML_PANEL_TAG -> {
                    PanelElement childPanel = createPanelFromXml(el, panel);
                    panel.addChild(childPanel);
                }
                default -> LOGGER.warn("Unknown tag '{}' inside <Panel id='{}'>", el.getTagName(), id);
            }
        }

        // Однократный пересчёт размеров и позиционирования детей для данной панели
        panel.recomputeSizeAndRepositionChildren();
        return panel;
    }

    /**
     * Вся логика привязки TextElement → Field в eventHandlerTarget.
     * Результат: в boundTextFields хранится Field, а в lastKnownTextValues — начальное значение.
     */
    private void bindAllTextFields() {
        boundTextFields.clear();
        lastKnownTextValues.clear();

        if (eventHandlerTarget == null) {
            LOGGER.debug("bindAllTextFields: eventHandlerTarget == null, skip binding.");
            return;
        }

        // Кэшируем все поля обработчика: имена → Field
        Map<String, Field> fieldMap = new HashMap<>();
        for (Field f : eventHandlerTarget.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            fieldMap.put(f.getName(), f);
        }
        LOGGER.debug("bindAllTextFields: handler fields = {}", fieldMap.keySet());

        // Пробегаемся по всем UIElement, находим TextElement
        for (UIElement ue : allElements.values()) {
            if (!(ue instanceof TextElement te)) continue;
            String elementId = te.getId();
            if (!fieldMap.containsKey(elementId)) continue;

            Field f = fieldMap.get(elementId);
            boundTextFields.put(te, f);

            // Устанавливаем начальное значение: берём текущее поле обработчика и сразу кладём в текст
            try {
                Object val = f.get(eventHandlerTarget);
                String text = (val != null) ? String.valueOf(val) : "";
                te.setText(text);
                lastKnownTextValues.put(te, text);
            } catch (Exception ex) {
                LOGGER.warn("Cannot bind initial value for '{}'", elementId, ex);
                lastKnownTextValues.put(te, "");
            }
        }
    }

    /**
     * Ежекадровый апдейт (вызывается JME).
     * Проходим по всем связанным TextElement → Field, проверяем, изменилось ли значение.
     * Если изменилось — обновляем текст и помечаем parentPanel «грязным» (dirty).
     * В конце, если есть «грязные» панели, пересчитываем их и пересмещаем все корневые.
     */
    @Override
    public void update(float tpf) {
        if (boundTextFields.isEmpty()) {
            return;
        }

        // 1) Обработка всех TextElement → Field
        for (Map.Entry<TextElement, Field> entry : boundTextFields.entrySet()) {
            TextElement te = entry.getKey();
            Field       f  = entry.getValue();

            try {
                Object newValObj = f.get(eventHandlerTarget);
                String newValStr = (newValObj != null) ? String.valueOf(newValObj) : "";

                String oldValStr = lastKnownTextValues.getOrDefault(te, "");
                if (!newValStr.equals(oldValStr)) {
                    te.setText(newValStr);
                    lastKnownTextValues.put(te, newValStr);
                    markDirty(te.getParentPanel());
                }
            } catch (IllegalAccessException iae) {
                LOGGER.warn("Cannot access field '{}' for TextElement '{}'", f.getName(), te.getId());
            }
        }

        // 2) Если есть «грязные» панели, пересчитываем их
        if (!dirtyPanels.isEmpty()) {
            // Пересчитаем размер и позиции «грязных» панелей (и вверх по иерархии)
            for (PanelElement dirty : dirtyPanels) {
                PanelElement current = dirty;
                while (current != null) {
                    current.recomputeSizeAndRepositionChildren();
                    current = current.getParentPanel();
                }
            }
            dirtyPanels.clear();
            // После этого обновляем позицию всех «верхнеуровневых» панелей
            repositionAllRootPanels();
        }
    }

    /**
     * Помечаем данную панель (PanelElement) как «грязную» (требует пересчёта).
     * Если panel == null, ничего не делаем.
     */
    private void markDirty(PanelElement panel) {
        if (panel != null) {
            dirtyPanels.add(panel);
        }
    }

    /**
     * Перемещаем все панели верхнего уровня (parentPanel == null), учитывая align и margin.
     * Вызывается при инициализации и после обновлений, если были изменения.
     */
    private void repositionAllRootPanels() {
        int width  = cam.getWidth();
        int height = cam.getHeight();
        for (UIElement e : allElements.values()) {
            if (e instanceof PanelElement pe && pe.getParentPanel() == null) {
                pe.repositionRecursively(width, height);
            }
        }
    }

    // ===========================
    // Вспомогательные утилиты
    // ===========================

    /**
     * Загружает XML из внутреннего classpath (resources) по заданному пути.
     * @throws Exception, если не удалось открыть/распарсить XML.
     */
    private Document loadXmlDocument(String path) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) {
            String msg = "Cannot load UI XML resource: " + path;
            LOGGER.error(msg);
            throw new RuntimeException(msg);
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder        db  = dbf.newDocumentBuilder();
        Document               doc = db.parse(is);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private boolean isNonEmptyAndNotAuto(String s) {
        return (s != null && !s.isEmpty() && !"auto".equals(s));
    }

    private float parseFloatOrDefault(String s, float def) {
        if (s == null || s.isEmpty()) return def;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            LOGGER.warn("parseFloatOrDefault: '{}' is not a float, default = {}", s, def);
            return def;
        }
    }

    /**
     * Парсинг цвета формата "r,g,b,a". Если некорректно — возвращаем переданный defaultColor.clone().
     */
    private ColorRGBA parseColorOrDefault(String s, ColorRGBA defaultColor) {
        if (s == null || s.isEmpty()) {
            return defaultColor.clone();
        }
        String[] parts = s.split(",");
        if (parts.length != 4) {
            LOGGER.warn("parseColor: '{}' invalid, expected 4 components", s);
            return defaultColor.clone();
        }
        try {
            float r = Float.parseFloat(parts[0]);
            float g = Float.parseFloat(parts[1]);
            float b = Float.parseFloat(parts[2]);
            float a = Float.parseFloat(parts[3]);
            return new ColorRGBA(r, g, b, a);
        } catch (NumberFormatException nfe) {
            LOGGER.warn("parseColor: cannot parse '{}', using default color", s);
            return defaultColor.clone();
        }
    }
}