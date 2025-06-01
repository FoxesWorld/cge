package org.foxesworld.cge.ui;

import org.foxesworld.cge.ui.elements.PanelElement;
import org.foxesworld.cge.ui.elements.UIElement;
import org.w3c.dom.Element;

@FunctionalInterface
public interface UIElementCreator {
    UIElement create(Element el, String id, PanelElement parent, String defaultFontPath, float defaultFontSize);
}