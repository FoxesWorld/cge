package org.foxesworld.cge.ui.novaUi;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.novaUi.elements.PanelElement;
import org.foxesworld.cge.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.ui.novaUi.elements.image.ImageElement;
import org.foxesworld.cge.ui.novaUi.elements.progress.ProgressElement;
import org.foxesworld.cge.ui.novaUi.elements.text.TextElement;
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
 * Добавлена принудительная проверка коллизий внутри каждой панели: после
 * первоначального reposition все дочерние элементы проверяются, и при
 * наложениях смещаются вниз, чтобы исключить перекрытия.
 */
public class UIPanel extends BaseAppState {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIPanel.class);

    // --- Теги XML ---
    private static final String XML_FONT_TAG    = "Font";
    private static final String XML_PANEL_TAG   = "Panel";
    private static final String XML_ELEMENT_TAG = "Element";

    // Значения по умолчанию
    private static final String    DEFAULT_FONT_PATH = "Interface/Fonts/Default.fnt";
    private static final float     DEFAULT_FONT_SIZE = 20f;
    private static final ColorRGBA DEFAULT_BG_COLOR  = new ColorRGBA(0f, 0f, 0f, 0.5f);

    private final CalistaGameEngine calistaGameEngine;
    private final Node              guiNode;
    private final String            configPath;

    private Camera                cam;
    private Node                  rootPanelNode = new Node("UIPanelRoot");

    // Глобальные настройки, читаются из XML
    private String    defaultFontPath = DEFAULT_FONT_PATH;
    private float     defaultFontSize = DEFAULT_FONT_SIZE;
    private ColorRGBA defaultBgColor  = DEFAULT_BG_COLOR.clone();

    /** Все созданные UI-элементы (id → элемент) **/
    private final Map<String, UIElement> allElements = new LinkedHashMap<>();

    /** Обработчик событий, чьи поля биндим к TextElement/ProgressElement **/
    private Object eventHandlerTarget = null;

    /**
     * Храним единый мапинг UIElement (TextElement или ProgressElement) → Field
     * из eventHandlerTarget. В update() будем пробегаться по boundFields.
     */
    private final Map<UIElement, Field> boundFields = new LinkedHashMap<>();

    /**
     * Для каждого UIElement храним последнее «известное» значение:
     * - для TextElement → String
     * - для ProgressElement → Float
     */
    private final Map<UIElement, Object> lastKnownValues = new HashMap<>();

    /** Панели, которым нужно вызвать recomputeSizeAndRepositionChildren() в конце update() **/
    private final Set<PanelElement> dirtyPanels = new LinkedHashSet<>();

    public UIPanel(CalistaGameEngine calistaGameEngine, String configPath) {
        this.calistaGameEngine = calistaGameEngine;
        this.guiNode = calistaGameEngine.getGuiNode();
        this.configPath = configPath;
        LOGGER.info("UIPanel created (config = {})", configPath);
    }

    /**
     * Регистрируем объект-обработчик (HUDController, MainApp и т.п.).
     * После разбора XML UIPanel попытается привязать текст и прогресс к полям в этом объекте.
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
            repositionAllRootPanels(); // первоначальная расстановка с проверкой коллизий
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
     * Установка произвольного свойства (цвета, размера, позиции и т.п.) по id.
     * Если TextElement или ImageElement — помечаем родительскую панель «грязной».
     */
    public void setProperty(String id, String propKey, String propValue) {
        UIElement e = allElements.get(id);
        if (e == null) {
            LOGGER.warn("setProperty: element '{}' not found", id);
            return;
        }
        LOGGER.debug("setProperty: id='{}' key='{}' value='{}'", id, propKey, propValue);
        e.setProperty(propKey, propValue);

        if (e instanceof TextElement || e instanceof ImageElement || e instanceof ProgressElement) {
            markDirty(e.getParentPanel());
        }
    }

    /**
     * Получить UI-элемент по его id.
     */
    public UIElement getElement(String id) {
        return allElements.get(id);
    }

    // ----------------------------------
    // Парсинг XML и построение UI
    // ----------------------------------

    private void parseXmlAndBuildUI() throws Exception {
        LOGGER.info("Parsing UI XML: '{}'", configPath);
        Document doc = loadXmlDocument(configPath);

        Element root = doc.getDocumentElement();
        parseGlobalFontSettings(root);

        // Находим корневую панель (<Panel> внутри <UI>)
        NodeList panelNodes = root.getElementsByTagName(XML_PANEL_TAG);
        if (panelNodes.getLength() == 0) {
            throw new RuntimeException("UI XML must contain at least one <Panel> element");
        }
        Element topPanelEl = (Element) panelNodes.item(0);

        LOGGER.info("Creating top-level panel with id='{}'", topPanelEl.getAttribute("id"));
        PanelElement topPanel = createPanelFromXml(topPanelEl, null);
        rootPanelNode.attachChild(topPanel.getNode());

        // После создания всех элементов — биндим поля сразу
        bindAllFields();
        LOGGER.info("UI construction completed (elements counted = {}).", allElements.size());
    }

    /**
     * Чтение глобальных настроек шрифта (<Font defaultPath="..." defaultSize="..."/>).
     */
    private void parseGlobalFontSettings(Element root) {
        NodeList fonts = root.getElementsByTagName(XML_FONT_TAG);
        if (fonts.getLength() == 0) {
            LOGGER.debug("No <Font> tag found; using defaults (path='{}', size={}).",
                    defaultFontPath, defaultFontSize);
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
                LOGGER.warn("Invalid defaultSize in <Font>: '{}'; keeping {}",
                        fontEl.getAttribute("defaultSize"), defaultFontSize);
            }
        }
    }

    /**
     * Рекурсивное создание PanelElement из XML-узла <Panel> и вложенных <Element> / <Panel>.
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

        // Строим фон (Quad 1×1, материал и т. д.)
        panel.buildBackgroundGeom();

        // Фабрика для создания TextElement, ImageElement, ProgressElement
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

        // Однократный пересчёт размеров и позиционирования детей
        panel.recomputeSizeAndRepositionChildren();
        return panel;
    }

    /**
     * Привязывает сразу все TextElement и ProgressElement к полям eventHandlerTarget.
     * Заполняет boundFields и сразу инициализирует lastKnownValues.
     */
    private void bindAllFields() {
        boundFields.clear();
        lastKnownValues.clear();

        if (eventHandlerTarget == null) {
            LOGGER.debug("bindAllFields: eventHandlerTarget == null, skip binding.");
            return;
        }

        // 1) Кэшируем все поля обработчика: имя → Field
        Map<String, Field> fieldMap = new HashMap<>();
        for (Field f : eventHandlerTarget.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            fieldMap.put(f.getName(), f);
        }
        LOGGER.debug("bindAllFields: handler fields = {}", fieldMap.keySet());

        // 2) Обходим все UIElement, ищем TextElement и ProgressElement
        for (UIElement ue : allElements.values()) {
            String elementId = ue.getId();
            if (!fieldMap.containsKey(elementId)) {
                continue;
            }
            Field f = fieldMap.get(elementId);

            if (ue instanceof TextElement te) {
                boundFields.put(te, f);
                try {
                    Object raw = f.get(eventHandlerTarget);
                    String text = (raw != null) ? raw.toString() : "";
                    te.setText(text);
                    lastKnownValues.put(te, text);
                } catch (Exception ex) {
                    LOGGER.warn("Cannot bind initial text for '{}'", elementId, ex);
                    lastKnownValues.put(te, "");
                }

            } else if (ue instanceof ProgressElement pe) {
                boundFields.put(pe, f);
                try {
                    Object raw = f.get(eventHandlerTarget);
                    float progressValue = 0f;
                    if (raw instanceof Number) {
                        progressValue = ((Number) raw).floatValue();
                    } else if (raw instanceof String) {
                        try {
                            progressValue = Float.parseFloat((String) raw);
                        } catch (NumberFormatException ignored) {
                            progressValue = 0f;
                        }
                    }
                    progressValue = Math.max(0f, Math.min(progressValue, 1f));
                    pe.setProgress(progressValue);
                    lastKnownValues.put(pe, progressValue);
                } catch (Exception ex) {
                    LOGGER.warn("Cannot bind initial progress for '{}'", elementId, ex);
                    lastKnownValues.put(pe, 0f);
                }
            }
        }
    }

    /**
     * Ежекадровый апдейт (вызывается JME).
     * Обходит boundFields и обновляет либо текст, либо прогресс.
     * В конце пересчитывает «грязные» панели.
     */
    @Override
    public void update(float tpf) {
        if (boundFields.isEmpty()) {
            return;
        }

        for (Map.Entry<UIElement, Field> entry : boundFields.entrySet()) {
            UIElement ue = entry.getKey();
            Field field = entry.getValue();

            try {
                Object raw = field.get(eventHandlerTarget);

                if (ue instanceof TextElement te) {
                    String newText = (raw != null) ? raw.toString() : "";
                    String oldText = (String) lastKnownValues.getOrDefault(te, "");
                    if (!newText.equals(oldText)) {
                        te.setText(newText);
                        lastKnownValues.put(te, newText);
                        markDirty(te.getParentPanel());
                    }
                    te.update(tpf);

                } else if (ue instanceof ProgressElement pe) {
                    float newProg = 0f;
                    if (raw instanceof Number) {
                        newProg = ((Number) raw).floatValue();
                    } else if (raw instanceof String) {
                        try {
                            newProg = Float.parseFloat((String) raw);
                        } catch (NumberFormatException ignored) {
                            newProg = 0f;
                        }
                    }
                    newProg = Math.max(0f, Math.min(newProg, 1f)) * 10;
                    float oldProg = (Float) lastKnownValues.getOrDefault(pe, -1f);
                    if (Math.abs(newProg - oldProg) > 1e-5f) {
                        pe.setProgress(newProg);
                        lastKnownValues.put(pe, newProg);
                        markDirty(pe.getParentPanel());
                    }
                    pe.update(tpf);
                }

            } catch (IllegalAccessException iae) {
                LOGGER.warn("Cannot access field '{}' for element '{}'",
                        field.getName(), ue.getId());
            }
        }

        if (!dirtyPanels.isEmpty()) {
            for (PanelElement dirty : dirtyPanels) {
                PanelElement current = dirty;
                while (current != null) {
                    current.recomputeSizeAndRepositionChildren();
                    current = current.getParentPanel();
                }
            }
            dirtyPanels.clear();
            repositionAllRootPanels();
        }
    }

    /**
     * Помечаем панель (PanelElement) «грязной», чтобы пересчитать её в конце кадра.
     */
    private void markDirty(PanelElement panel) {
        if (panel != null) {
            dirtyPanels.add(panel);
        }
    }

    /**
     * Перемещаем все панели верхнего уровня (parentPanel == null) с учётом align, margin и без перекрытий.
     */
    private void repositionAllRootPanels() {
        int width  = cam.getWidth();
        int height = cam.getHeight();

        for (UIElement e : allElements.values()) {
            if (e instanceof PanelElement pe && pe.getParentPanel() == null) {
                pe.repositionRecursively(width, height);
                fixOverlaps(pe);
            }
        }
    }

    /**
     * Для заданной панели проверяем всех её прямых потомков, и если AABB двух элементов пересекаются,
     * смещаем нижний из них дальше вниз (уменьшаем Y) по высоте предыдущего + margin, пока пересечение не исчезнет.
     */
    private void fixOverlaps(PanelElement panel) {
        List<UIElement> children = new ArrayList<>(panel.getChildren());
        // Сортируем по Y-координате: сверху вниз (большее localY → находится «выше» на экране)
        children.sort((a, b) -> {
            float ay = a.getNode().getLocalTranslation().y;
            float by = b.getNode().getLocalTranslation().y;
            return Float.compare(by, ay);
        });

        // Для каждой пары «раньше» и «позже» проверим пересечение
        for (int i = 0; i < children.size(); i++) {
            UIElement upper = children.get(i);
            float ux = upper.getNode().getLocalTranslation().x;
            float uy = upper.getNode().getLocalTranslation().y;
            float uw = upper.getParentPanel().getWidth();
            float uh = upper.getParentPanel().getHeight();

            for (int j = i + 1; j < children.size(); j++) {
                UIElement lower = children.get(j);
                float lx = lower.getNode().getLocalTranslation().x;
                float ly = lower.getNode().getLocalTranslation().y;
                float lw = lower.getParentPanel().getWidth();
                float lh = lower.getParentPanel().getHeight();

                // Проверка AABB: пересекаются ли области [ux, ux+uw]×[uy-uh, uy] и [lx, lx+lw]×[ly-lh, ly]
                boolean overlapX = (lx < ux + uw) && (lx + lw > ux);
                boolean overlapY = (ly > uy - uh) && (ly - lh < uy);

                if (overlapX && overlapY) {
                    // Смещаем «lower» вниз под «upper»
                    float newLy = uy - uh - panel.getPadding() - panel.getMargin();
                    lower.getNode().setLocalTranslation(lx, newLy, 0f);
                    // Обновляем ly, lh, чтобы при следующей проверке было актуально
                    ly = newLy;
                    lh = lower.getParentPanel().getHeight();
                }
            }
        }

        // Рекурсивно обрабатываем вложенные панели
        for (UIElement child : children) {
            if (child instanceof PanelElement childPanel) {
                fixOverlaps(childPanel);
            }
        }
    }

    // ===========================
    // Вспомогательные утилиты
    // ===========================

    /**
     * Загружает XML из classpath (resources) по заданному пути.
     */
    private Document loadXmlDocument(String path) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) {
            String msg = "Cannot load UI XML resource: " + path;
            LOGGER.error(msg);
            throw new RuntimeException(msg);
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db       = dbf.newDocumentBuilder();
        Document doc             = db.parse(is);
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
     * Парсит цвет формата "r,g,b,a". Если некорректно — возвращаем defaultColor.clone().
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
