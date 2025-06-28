package org.foxesworld.cge.modules.ui.novaUi;

import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;

import java.util.Map;

/**
 * An interface for loading a UI layout from a source.
 * This allows NovaUI to be independent of the layout format (XML, JSON, programmatic, etc.).
 */
public interface UILayoutLoader {
    /**
     * Parses the source and builds the UI element tree.
     * @return A ParseResult containing the root panel and a map of all elements by ID.
     * @throws Exception if loading or parsing fails.
     */
    ParseResult load() throws Exception;

    /**
     * A record to hold the results of a UI parsing operation.
     * @param rootPanel The root element of the parsed UI.
     * @param allElements A map of all parsed elements, keyed by their ID.
     */
    record ParseResult(PanelElement rootPanel, Map<String, UIElement> allElements) {}
}