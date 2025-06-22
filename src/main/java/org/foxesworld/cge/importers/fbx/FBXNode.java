package org.foxesworld.cge.importers.fbx;

import java.util.*;

public class FBXNode {
    private final String name;
    private final List<String> properties = new ArrayList<>();
    private final List<FBXNode> children = new ArrayList<>();
    private FBXNode parent; // Для поддержки getParent()

    public FBXNode(String name) { this.name = name; }

    public void addProperty(String prop) { properties.add(prop); }

    public void addChild(FBXNode child) {
        children.add(child);
        child.parent = this;
    }

    public String getName() { return name; }

    public List<String> getProperties() { return properties; }

    public List<FBXNode> getChildren() { return children; }

    public FBXNode getParent() { return parent; }

    public FBXNode findChild(String name) {
        for (FBXNode node : children) {
            if (node.name.equals(name)) return node;
        }
        return null;
    }

    public List<FBXNode> findChildren(String name) {
        List<FBXNode> result = new ArrayList<>();
        for (FBXNode node : children) {
            if (node.name.equals(name)) result.add(node);
        }
        return result;
    }
}