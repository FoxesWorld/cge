package org.foxesworld.cge.modules.ui.novaUi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable data object that represents a single node (element) parsed from a UI layout file (e.g., XML).
 * It holds the element's type, its attributes, and its child definitions.
 * This class serves as an intermediate representation between the parser and the UILayoutBuilder.
 */
public final class UINodeDefinition { // 'final' - этот класс не предназначен для наследования

    private final String type;
    private final String id;
    private final Map<String, String> attributes;
    private final List<UINodeDefinition> children;

    /**
     * Constructs a UINodeDefinition.
     *
     * @param type The type of the element (e.g., "Panel", "Image"). Cannot be null or blank.
     * @param attributes A map of attribute keys and values. Cannot be null.
     * @param children A list of child node definitions. Cannot be null.
     */
    public UINodeDefinition(String type, Map<String, String> attributes, List<UINodeDefinition> children) {
        // Проверяем входные данные на корректность
        Objects.requireNonNull(type, "Element type cannot be null");
        if (type.isBlank()) {
            throw new IllegalArgumentException("Element type cannot be blank");
        }

        this.type = type;
        this.attributes = Map.copyOf(Objects.requireNonNull(attributes, "Attributes map cannot be null"));
        this.children = List.copyOf(Objects.requireNonNull(children, "Children list cannot be null"));

        // Извлекаем и кэшируем ID для быстрого доступа
        this.id = this.attributes.getOrDefault("id", "");
    }

    /**
     * A convenience constructor for creating a definition with no children.
     */
    public UINodeDefinition(String type, Map<String, String> attributes) {
        this(type, attributes, Collections.emptyList());
    }

    /**
     * @return The type of the UI element, e.g., "Panel", "Image".
     */
    public String getType() {
        return type;
    }

    /**
     * @return The ID of the UI element, as specified by the 'id' attribute. Returns an empty string if not present.
     */
    public String getId() {
        return id;
    }

    /**
     * @return An unmodifiable view of the element's attributes.
     */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * @return An unmodifiable view of the element's child definitions.
     */
    public List<UINodeDefinition> getChildren() {
        return children;
    }

    /**
     * Gets an attribute's value by its key.
     *
     * @param key The name of the attribute.
     * @return The attribute's value, or null if the key is not found.
     */
    public String getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * Gets an attribute's value by its key, returning a default value if not found.
     *
     * @param key The name of the attribute.
     * @param defaultValue The value to return if the attribute is not present.
     * @return The attribute's value, or the default value.
     */
    public String getAttribute(String key, String defaultValue) {
        return attributes.getOrDefault(key, defaultValue);
    }

    @Override
    public String toString() {
        return "UINodeDefinition{" +
                "type='" + type + '\'' +
                ", id='" + id + '\'' +
                ", children=" + children.size() +
                '}';
    }
}