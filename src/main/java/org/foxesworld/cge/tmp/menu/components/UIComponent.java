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
    private float width, height;

    protected UIComponent(String id) {
        super(id);
        this.id = id;
    }

    public abstract void update(float tpf);
    public abstract boolean intersects(Vector2f cursor);
    public abstract void setSize(final float width, final float height);

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public Node getNode() {return this;}
    public float getDpiScale() {return dpiScale;}
    public void setDpiScale(float dpiScale) {this.dpiScale = dpiScale;}

    public void dispose() {
        removeFromParent();
        detachAllChildren();
    }

    public float getHeight() { return height; }
    public float getWidth() { return width; }
    public void setWidth(float width) {this.width = width;}
    public void setHeight(float height) {this.height = height;}

    public UIComponent getParentComponent() {
        if (getParent() instanceof UIComponent) {
            return (UIComponent) getParent();
        }
        return null;
    }
}
