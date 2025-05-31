package org.foxesworld.cge.ui;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.elements.ImageElement;
import org.foxesworld.cge.ui.elements.PanelElement;
import org.foxesworld.cge.ui.elements.TextElement;
import org.foxesworld.cge.ui.elements.UIElement;
import org.w3c.dom.Element;

/**
 * ElementFactory — создаёт конкретный UIElement (TextElement, ImageElement и т.д.)
 * на основе XML-тега <Element>.
 */
public class ElementFactory {
    private final CalistaGameEngine calistaGameEngine;

    public ElementFactory(CalistaGameEngine engine) {
        this.calistaGameEngine = engine;
    }

    public UIElement create(Element el, PanelElement parent, String defaultFontPath, float defaultFontSize) {
        // id — обязательный атрибут
        String id = el.getAttribute("id");
        if (id == null || id.isEmpty()) {
            throw new RuntimeException("Element missing required 'id' attribute!");
        }

        // type — какой класс UIElement создавать
        String type = el.getAttribute("type");
        if (type == null || type.isEmpty()) {
            throw new RuntimeException("Element[id=" + id + "] missing required 'type' attribute!");
        }

        UIElement result;
        switch (type) {
            case "TextElement": {
                TextElement txt = new TextElement(calistaGameEngine, id, parent, defaultFontPath, defaultFontSize);
                // Установим начальный текст, цвет и т.д. из атрибутов XML:
                if (el.hasAttribute("text")) {
                    txt.setProperty("text", el.getAttribute("text"));
                }
                if (el.hasAttribute("color")) {
                    txt.setProperty("color", el.getAttribute("color"));
                }
                if (el.hasAttribute("fontSize")) {
                    txt.setProperty("fontSize", el.getAttribute("fontSize"));
                }
                if (el.hasAttribute("fontPath")) {
                    txt.setProperty("fontPath", el.getAttribute("fontPath"));
                }
                if (el.hasAttribute("posX")) {
                    txt.setProperty("posX", el.getAttribute("posX"));
                }
                if (el.hasAttribute("posY")) {
                    txt.setProperty("posY", el.getAttribute("posY"));
                }
                if (el.hasAttribute("align")) {
                    txt.setProperty("align", el.getAttribute("align"));
                }
                result = txt;
                break;
            }
            case "ImageElement": {
                ImageElement img = new ImageElement(calistaGameEngine, id, parent);
                if (el.hasAttribute("imagePath")) {
                    img.setProperty("imagePath", el.getAttribute("imagePath"));
                }
                if (el.hasAttribute("posX")) {
                    img.setProperty("posX", el.getAttribute("posX"));
                }
                if (el.hasAttribute("posY")) {
                    img.setProperty("posY", el.getAttribute("posY"));
                }
                if (el.hasAttribute("width")) {
                    img.setProperty("width", el.getAttribute("width"));
                }
                if (el.hasAttribute("height")) {
                    img.setProperty("height", el.getAttribute("height"));
                }
                if (el.hasAttribute("color")) {
                    img.setProperty("color", el.getAttribute("color"));
                }
                if (el.hasAttribute("align")) {
                    img.setProperty("align", el.getAttribute("align"));
                }
                result = img;
                break;
            }
            // При необходимости можно добавить новые типы: ButtonElement, SliderElement и т.д.
            default:
                throw new RuntimeException("Unknown Element type: " + type);
        }

        // Если в XML указан onClick, привяжем его:
        if (el.hasAttribute("onClick")) {
            String methodName = el.getAttribute("onClick");
            result.setOnClickHandler(methodName, parent.getParentPanel() != null ? parent.getParentPanel() : parent);
            // либо передавать eventHandlerTarget из UIPanel
        }

        return result;
    }
}
