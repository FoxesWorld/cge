package org.foxesworld.cge.ui;

import com.jme3.scene.Node;
import org.foxesworld.cge.ui.elements.PanelElement;
import org.foxesworld.cge.ui.elements.UIElement;

/**
 * AbstractUIElement provides common functionality for all UI elements:
 *   • id: unique identifier
 *   • parentPanel: reference to containing PanelElement (null if root)
 *   • rawPosX, rawPosY: coordinates relative to parent (used by layout)
 *   • ownAlign: optional alignment string (e.g., "center", "top-right", "100,50")
 *   • onClick handler support via reflection
 *
 * Subclasses (TextElement, ImageElement, ProgressElement, PanelElement, etc.) inherit these fields
 * and override size methods as needed.
 */
public abstract class AbstractUIElement implements UIElement {
    /** Scene graph node representing this element. */
    protected final Node node = new Node();

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

    public AbstractUIElement() {
        // node is already initialized inline
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

    /**
     * Returns the raw X offset (in pixels) from the parent panel's top-left.
     * Used by PanelElement when computing layout or positioning.
     */
    public float getRawPosX() {
        return rawPosX;
    }

    /**
     * Returns the raw Y offset (in pixels) from the parent panel's top-left.
     * Used by PanelElement when computing layout or positioning.
     */
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

    /**
     * If element has an explicit ownAlign (like "center", "10,20", "top-right"), PanelElement
     * or parent layout will position based on this instead of rawPosX/rawPosY.
     */
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

    /**
     * Registers a click handler: methodName on eventHandlerTarget will be invoked via reflection
     * whenever triggerClick() is called. If methodName or target is null/empty, no handler is set.
     */
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

    // ==== Visibility and Enabled (optional extensions) ====

    /**
     * Returns whether this element is currently visible. Subclasses may override.
     * By default, true.
     */
    public boolean isVisible() {
        return true;
    }

    /**
     * Enables or disables this element. Subclasses may override for custom behavior.
     * By default, does nothing.
     */
    public void setEnabled(boolean enabled) {
        // no-op by default
    }

    // ==== Property Support ====

    /**
     * Default implementation: if an unknown property key is passed, do nothing.
     * Subclasses should override setProperty(key, value) if they support extra attributes.
     */
    @Override
    public void setProperty(String key, String value) {
        // By default, unrecognized property → no action.
        // Subclasses that support specific properties must override this.
    }

    // ==== Size (to be overridden) ====

    /**
     * Returns this element's width for layout calculations.
     * Subclasses (TextElement, ImageElement, PanelElement, etc.) should override.
     * Default: 0.
     */
    public float getWidth() {
        return 0f;
    }

    /**
     * Returns this element's height for layout calculations.
     * Subclasses should override. Default: 0.
     */
    public float getHeight() {
        return 0f;
    }
}
