package org.foxesworld.cge.modules.ui.novaUi.elements;

import com.jme3.scene.Node;
import org.foxesworld.cge.modules.ui.novaUi.OnClickHandler;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractUIElement implements UIElement {
    protected final Node node = new Node();
    protected float width, height;
    protected String id;
    protected PanelElement parentPanel;
    protected float rawPosX = 0f;
    protected float rawPosY = 0f;
    protected String ownAlign;
    protected OnClickHandler clickHandler;

    @FunctionalInterface
    public interface ResizeListener {
        void onResize(AbstractUIElement element, float newWidth, float newHeight);
    }

    private final List<ResizeListener> resizeListeners = new ArrayList<>();
    private float lastKnownWidth = -1f;
    private float lastKnownHeight = -1f;

    public AbstractUIElement() { }

    public void updateSelf(float tpf) { }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        this.node.setName(id);
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

    public void removeFromParent() {
        if (parentPanel != null) {
            parentPanel.removeChild(this);
            parentPanel = null;
        }
    }

    public float getRawPosX() {
        return rawPosX;
    }

    public float getRawPosY() {
        return rawPosY;
    }

    public void setRawPosX(float x) {
        this.rawPosX = x;
    }

    public void setRawPosY(float y) {
        this.rawPosY = y;
    }

    @Override
    public boolean hasOwnAlign() {
        return ownAlign != null && !ownAlign.trim().isEmpty();
    }

    @Override
    public String getOwnAlign() {
        return ownAlign;
    }

    public void setOwnAlign(String align) {
        this.ownAlign = (align != null ? align.trim() : null);
    }

    @Override
    public void setOnClickHandler(String methodName, Object eventHandlerTarget) {
        if (methodName != null && !methodName.isEmpty() && eventHandlerTarget != null) {
            this.clickHandler = new OnClickHandler(methodName, eventHandlerTarget);
        }
    }

    protected void triggerClick() {
        if (clickHandler != null) {
            clickHandler.invoke();
        }
    }

    public void addResizeListener(ResizeListener listener) {
        if (listener != null && !resizeListeners.contains(listener)) {
            resizeListeners.add(listener);
        }
    }

    public void removeResizeListener(ResizeListener listener) {
        resizeListeners.remove(listener);
    }

    protected void checkAndNotifyResize() {
        float currentWidth = getWidth();
        float currentHeight = getHeight();
        if (currentWidth != lastKnownWidth || currentHeight != lastKnownHeight) {
            for (ResizeListener listener : resizeListeners) {
                listener.onResize(this, currentWidth, currentHeight);
            }
            lastKnownWidth = currentWidth;
            lastKnownHeight = currentHeight;
        }
    }

    public boolean isVisible() {
        return true;
    }

    public void setEnabled(boolean enabled) { }

    @Override
    public void setProperty(String key, String value) { }

    @Override
    public float getWidth() {
        return this.width;
    }

    @Override
    public float getHeight() {
        return this.height;
    }
}
