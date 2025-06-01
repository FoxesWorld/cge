package org.foxesworld.cge.ui.novaUi.tmp;

import org.foxesworld.cge.ui.novaUi.elements.AbstractUIElement;

@FunctionalInterface
public interface ResizeListener {
    void onResize(AbstractUIElement element, float newWidth, float newHeight);
}
