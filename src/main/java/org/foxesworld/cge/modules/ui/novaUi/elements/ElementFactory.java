package org.foxesworld.cge.modules.ui.novaUi.elements;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.image.ImageElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.progress.ProgressElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.text.TextElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * ElementFactory — создаёт UIElement по XML-тегу <Element>.
 * Позволяет регистрировать новые типы без изменения исходного кода.
 */
public class ElementFactory {

    private static final Logger logger = LoggerFactory.getLogger(ElementFactory.class);
    private final CalistaGameEngine calistaGameEngine;
    private final Map<String, UIElementCreator> registry = new HashMap<>();

    public ElementFactory(CalistaGameEngine engine) {
        this.calistaGameEngine = engine;

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

        // Пример: registerType("Button", (el, id, parent, fontPath, fontSize) -> new ButtonElement(...));
    }

    public void registerType(String type, UIElementCreator creator) {
        registry.put(type, creator);
        logger.debug("Registered '{}' UI element", type);
    }

    public UIElement create(Element el, PanelElement parent, String defaultFontPath, float defaultFontSize) {
        String id = el.getAttribute("id");
        if (id.isEmpty()) {
            throw new RuntimeException("Element missing required 'id' attribute!");
        }

        String type = el.getAttribute("type");
        if (type.isEmpty()) {
            throw new RuntimeException("Element[id=" + id + "] missing required 'type' attribute!");
        }

        UIElementCreator creator = registry.get(type);
        if (creator == null) {
            throw new RuntimeException("Unknown Element type: " + type);
        }

        UIElement element = creator.create(el, id, parent, defaultFontPath, defaultFontSize);

        // Обработчик onClick, если есть
        if (el.hasAttribute("onClick")) {
            String methodName = el.getAttribute("onClick");
            element.setOnClickHandler(methodName, parent.getParentPanel() != null ? parent.getParentPanel() : parent);
        }

        return element;
    }

    private void applyAttributes(UIElement element, Element xmlElement) {
        NamedNodeMap attrs = xmlElement.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            if (!"type".equals(attr.getNodeName()) && !"id".equals(attr.getNodeName())) {
                element.setProperty(attr.getNodeName(), attr.getNodeValue());
            }
        }
    }
}
