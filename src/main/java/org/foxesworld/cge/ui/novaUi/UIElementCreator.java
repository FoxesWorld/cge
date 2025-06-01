package org.foxesworld.cge.ui.novaUi;

import org.foxesworld.cge.ui.novaUi.elements.PanelElement;
import org.foxesworld.cge.ui.novaUi.elements.UIElement;
import org.w3c.dom.Element;

@FunctionalInterface
public interface UIElementCreator {
    UIElement create(Element el, String id, PanelElement parent, String defaultFontPath, float defaultFontSize);
}