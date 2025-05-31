package org.foxesworld.cge.ui;

import com.jme3.scene.Node;
import org.foxesworld.cge.ui.elements.PanelElement;
import org.foxesworld.cge.ui.elements.UIElement;

/**
 * Абстрактный класс, содержащий базовые поля: id, parentPanel, ownAlign, onClickHandler.
 * Все конкретные элементы (TextElement, ImageElement, PanelElement) могут наследоваться отсюда.
 */
public abstract class AbstractUIElement implements UIElement {
    protected final Node node = new Node();
    protected String id;
    protected PanelElement parentPanel;
    protected String ownAlign;          // например: "center" или "top-right" или "100,50"
    protected OnClickHandler clickHandler;

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public Node getNode() {
        return node;
    }

    @Override
    public PanelElement getParentPanel() {
        return parentPanel;
    }

    public void setParentPanel(PanelElement parent) {
        this.parentPanel = parent;
    }

    @Override
    public boolean hasOwnAlign() {
        return ownAlign != null && !ownAlign.trim().isEmpty();
    }

    @Override
    public String getOwnAlign() {
        return ownAlign;
    }

    @Override
    public void setOnClickHandler(String methodName, Object eventHandlerTarget) {
        if (methodName != null && !methodName.isEmpty() && eventHandlerTarget != null) {
            this.clickHandler = new OnClickHandler(methodName, eventHandlerTarget);
        }
    }

    /**
     * Вызвать clickHandler (если он был установлен).
     * Должен вызываться в слушателе мыши/касания
     */
    protected void triggerClick() {
        if (clickHandler != null) {
            clickHandler.invoke();
        }
    }
}
