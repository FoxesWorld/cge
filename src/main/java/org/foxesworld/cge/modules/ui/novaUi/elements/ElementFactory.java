package org.foxesworld.cge.modules.ui.novaUi.elements;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.button.ButtonElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.image.ImageElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.progress.ProgressElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.text.TextElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ElementFactory — создаёт UIElement по XML-тегу <Element>.
 * Позволяет регистрировать новые типы без изменения исходного кода.
 * Устойчивее к ошибкам, потокобезопасен, расширяем.
 */
public class ElementFactory {

    private static final Logger logger = LoggerFactory.getLogger(ElementFactory.class);
    private final CalistaGameEngine calistaGameEngine;
    // Используем потокобезопасную карту для регистрации типов элементов
    private final Map<String, UIElementCreator> registry = Collections.synchronizedMap(new HashMap<>());

    public ElementFactory(CalistaGameEngine engine) {
        this.calistaGameEngine = Objects.requireNonNull(engine, "CalistaGameEngine must not be null");

        // Регистрация базовых типов
        registerType("TextElement", (el, id, parent, fontPath, fontSize) -> {
            var txt = new TextElement(calistaGameEngine, id, parent, fontPath, fontSize);
            applyAttributes(txt, el);
            return txt;
        });

        registerType("ImageElement", (el, id, parent, fontPath, fontSize) -> {
            var img = new ImageElement(calistaGameEngine, id, parent);
            applyAttributes(img, el);
            return img;
        });

        registerType("Progress", (el, id, parent, fontPath, fontSize) -> {
            var prog = new ProgressElement(calistaGameEngine, id, parent);
            applyAttributes(prog, el);
            return prog;
        });

        registerType("Button", (el, id, parent, fontPath, fontSize) -> {
            var btn = new ButtonElement(calistaGameEngine, id, parent, fontPath, fontSize);
            applyAttributes(btn, el);
            return btn;
        });
    }

    /**
     * Регистрирует новый тип UI-элемента.
     */
    public void registerType(String type, UIElementCreator creator) {
        if (type == null || creator == null) {
            throw new IllegalArgumentException("Type and creator must not be null");
        }
        registry.put(type, creator);
        logger.debug("Registered '{}' UI element", type);
    }

    /**
     * Создаёт элемент по XML-описанию.
     */
    public UIElement create(Element el, PanelElement parent, String defaultFontPath, float defaultFontSize) {
        Objects.requireNonNull(el, "XML Element must not be null");
        Objects.requireNonNull(parent, "Parent panel must not be null");
        String id = el.getAttribute("id");
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Element missing required 'id' attribute!");
        }

        String type = el.getAttribute("type");
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Element[id=" + id + "] missing required 'type' attribute!");
        }

        UIElementCreator creator = registry.get(type);
        if (creator == null) {
            throw new IllegalArgumentException("Unknown Element type: " + type);
        }

        UIElement element = creator.create(el, id, parent, defaultFontPath, defaultFontSize);

        // Обработчик onClick, если есть
        if (el.hasAttribute("onClick")) {
            String methodName = el.getAttribute("onClick");
            element.setOnClickHandler(methodName, parent.getParentPanel() != null ? parent.getParentPanel() : parent);
        }

        return element;
    }

    /**
     * Применяет все XML-атрибуты к UI-элементу как свойства, кроме служебных.
     */
    private void applyAttributes(UIElement element, Element xmlElement) {
        NamedNodeMap attrs = xmlElement.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String name = attr.getNodeName();
            if (!"type".equals(name) && !"id".equals(name) && !"onClick".equals(name)) {
                element.setProperty(name, attr.getNodeValue());
            }
        }
    }
}