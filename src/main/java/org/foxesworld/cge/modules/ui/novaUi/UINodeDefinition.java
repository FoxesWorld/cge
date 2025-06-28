package org.foxesworld.cge.modules.ui.novaUi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UINodeDefinition {
    private final String type;
    private final Map<String, String> attributes;
    private final List<UINodeDefinition> children = new ArrayList<>();

    public UINodeDefinition(String type, Map<String, String> attributes) {
        this.type = type;
        this.attributes = attributes;
    }

    public void addChild(UINodeDefinition child) { children.add(child); }
    public String getType() { return type; }
    public Map<String, String> getAttributes() { return Collections.unmodifiableMap(attributes); }
    public List<UINodeDefinition> getChildren() { return Collections.unmodifiableList(children); }
    public String getAttribute(String key) { return attributes.get(key); }
}