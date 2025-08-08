package org.foxesworld.cge.tmp.menu.components;

import com.jme3.scene.Node;

/**
 * Абстрактный базовый класс для всех UI-компонентов.
 * Обеспечивает единый API и базовую реализацию.
 */
public abstract class UIComponent {

    protected String id;
    protected final Node node;
    protected float dpiScale = 1.0f;

    protected UIComponent(String id) {
        this.id = id;
        this.node = new Node(id);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Node getNode() {
        return node;
    }

    public float getDpiScale() {
        return dpiScale;
    }

    public void setDpiScale(float dpiScale) {
        this.dpiScale = dpiScale;
    }

    /**
     * Вызывается каждый кадр для обновления компонента.
     * Переопределяется в потомках при необходимости.
     */
    public void update(float tpf) {
        // Ничего по умолчанию
    }

    /**
     * Удаляет компонент из родителя и освобождает ресурсы.
     */
    public void dispose() {
        node.removeFromParent();
        node.detachAllChildren();
    }
}
