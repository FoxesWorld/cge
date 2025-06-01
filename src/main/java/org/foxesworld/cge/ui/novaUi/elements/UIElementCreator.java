package org.foxesworld.cge.ui.novaUi.elements;

import org.foxesworld.cge.ui.novaUi.elements.panel.PanelElement;
import org.w3c.dom.Element;

@FunctionalInterface
public interface UIElementCreator {
    UIElement create(Element el, String id, PanelElement parent, String defaultFontPath, float defaultFontSize);
}