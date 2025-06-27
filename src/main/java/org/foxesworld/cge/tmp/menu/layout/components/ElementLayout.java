package org.foxesworld.cge.tmp.menu.layout.components;

import com.jme3.math.Vector2f;

import java.util.Map;

public class ElementLayout {
    public final String id;
    public final String type;
    public final Vector2f position;
    public final Vector2f size;
    public final Map<String, String> attributes;

    public ElementLayout(String id, String type, Vector2f position, Vector2f size, Map<String, String> attributes) {
        this.id = id;
        this.type = type;
        this.position = position;
        this.size = size;
        this.attributes = attributes;
    }
}