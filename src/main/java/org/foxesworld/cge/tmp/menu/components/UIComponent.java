package org.foxesworld.cge.tmp.menu.components;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;

/**
 * Абстрактный базовый класс для всех UI-компонентов.
 * Обеспечивает единый API и базовую реализацию.
 */
public abstract class UIComponent extends Node {

    protected String id;
    protected float dpiScale = 1.0f;

    protected UIComponent(String id) {
        super(id);
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Node getNode() {
        return this;
    }

    public float getDpiScale() {
        return dpiScale;
    }

    public void setDpiScale(float dpiScale) {
        this.dpiScale = dpiScale;
    }

    public abstract void update(float tpf);
    public abstract float getWidth();
    public abstract float getHeight();
    public abstract boolean intersects(Vector2f cursor);
    public abstract void setSize(final float width, final float height);

    public void dispose() {
        removeFromParent();
        detachAllChildren();
    }
}
