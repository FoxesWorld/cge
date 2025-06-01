package org.foxesworld.cge.ui.novaUi.elements;

import com.jme3.scene.Node;
import org.foxesworld.cge.ui.novaUi.OnClickHandler;
import org.foxesworld.cge.ui.novaUi.elements.panel.PanelElement;

import java.util.ArrayList;
import java.util.List;

/**
 * AbstractUIElement provides common functionality for all UI elements:
 *   • id: unique identifier
 *   • parentPanel: reference to containing PanelElement (null if root)
 *   • rawPosX, rawPosY: coordinates relative to parent (used by layout)
 *   • ownAlign: optional alignment string (e.g., "center", "top-right", "100,50")
 *   • onClick handler support via reflection
 *   • resize listener support: allows elements to react when parent panel changes size
 * Subclasses (TextElement, ImageElement, ProgressElement, PanelElement, etc.) inherit these fields
 * and override size methods as needed.
 */
@SuppressWarnings("unused")
public abstract class AbstractUIElement implements UIElement {
    /** Scene graph node representing this element. */
    protected final Node node = new Node();
    protected float width, height;

    /** Unique identifier for this UI element. */
    protected String id;

    /** Parent panel (null if this is a root-level element). */
    protected PanelElement parentPanel;

    /**
     * Coordinates relative to the parent panel's top-left. These are used
     * when a panel's layout ("vertical"/"horizontal"/"none") consults rawPosX/rawPosY.
     */
    protected float rawPosX = 0f;
    protected float rawPosY = 0f;

    /**
     * Optional alignment overrides that take precedence over rawPosX/rawPosY.
     * Examples: "top-left", "center", "50,20" (explicit pixel offsets).
     */
    protected String ownAlign;

    /** Click-handler wrapper that invokes a method by reflection on target. */
    protected OnClickHandler clickHandler;

    /**
     * Interface for resize listeners. Invoked when this element's size changes.
     */
    @FunctionalInterface
    public interface ResizeListener {
        void onResize(AbstractUIElement element, float newWidth, float newHeight);
    }

    /** Registered listeners for resize events. */
    private final List<ResizeListener> resizeListeners = new ArrayList<>();

    /** Last known width and height for notifying only on actual change. */
    private float lastKnownWidth = -1f;
    private float lastKnownHeight = -1f;

    public AbstractUIElement() {
        // node initialized inline
    }

    // ==== ID ====
    @Override
    public String getId() {
        return id;
    }

    /** Sets the element's unique identifier. */
    public void setId(String id) {
        this.id = id;
        this.node.setName(id);
    }

    // ==== Node ====
    @Override
    public Node getNode() {
        return node;
    }

    // ==== Parent Panel ====
    @Override
    public PanelElement getParentPanel() {
        return parentPanel;
    }

    /** Assigns a parent PanelElement for this element. */
    public void setParentPanel(PanelElement parent) {
        this.parentPanel = parent;
    }

    /** Detaches this element from its current parentPanel (if any). */
    public void removeFromParent() {
        if (parentPanel != null) {
            parentPanel.removeChild(this);
            parentPanel = null;
        }
    }

    // ==== Raw Position ====
    public float getRawPosX() {
        return rawPosX;
    }

    public float getRawPosY() {
        return rawPosY;
    }

    /** Sets rawPosX (overridden by layout manager if panel.layout!="none"). */
    public void setRawPosX(float x) {
        this.rawPosX = x;
    }

    /** Sets rawPosY (overridden by layout manager if panel.layout!="none"). */
    public void setRawPosY(float y) {
        this.rawPosY = y;
    }

    // ==== Alignment ====
    @Override
    public boolean hasOwnAlign() {
        return ownAlign != null && !ownAlign.trim().isEmpty();
    }

    @Override
    public String getOwnAlign() {
        return ownAlign;
    }

    /** Sets an explicit alignment for this element (overrides rawPos). */
    public void setOwnAlign(String align) {
        this.ownAlign = (align != null ? align.trim() : null);
    }

    // ==== OnClick Handler ====
    @Override
    public void setOnClickHandler(String methodName, Object eventHandlerTarget) {
        if (methodName != null && !methodName.isEmpty() && eventHandlerTarget != null) {
            this.clickHandler = new OnClickHandler(methodName, eventHandlerTarget);
        }
    }

    /**
     * Invokes the registered click handler (if any). Should be called from input listener.
     */
    protected void triggerClick() {
        if (clickHandler != null) {
            clickHandler.invoke();
        }
    }

    // ==== Resize Listener Support ====
    /**
     * Registers a listener to be notified when this element's size changes.
     */
    public void addResizeListener(ResizeListener listener) {
        if (listener != null && !resizeListeners.contains(listener)) {
            resizeListeners.add(listener);
        }
    }

    /**
     * Unregisters a previously registered resize listener.
     */
    public void removeResizeListener(ResizeListener listener) {
        resizeListeners.remove(listener);
    }

    /**
     * Checks if width/height changed and notifies listeners. Should be called
     * after any update that might affect getWidth()/getHeight().
     */
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

    // ==== Visibility and Enabled (optional extensions) ====
    public boolean isVisible() {
        return true;
    }

    public void setEnabled(boolean enabled) {
        // no-op by default
    }

    // ==== Property Support ====
    @Override
    public void setProperty(String key, String value) {
        // By default, unrecognized property → no action.
        // Subclasses that support specific properties must override this.
    }

    // ==== Size (to be overridden) ====
    public float getWidth() {
        return this.width;
    }

    public float getHeight() {
        return this.height;
    }
}