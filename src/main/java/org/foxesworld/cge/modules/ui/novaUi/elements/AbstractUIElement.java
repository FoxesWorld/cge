package org.foxesworld.cge.modules.ui.novaUi.elements;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * An abstract base class that provides a default implementation for most of the
 * {@link UIElement} interface. It handles common properties like ID, parent, node,
 * margins, padding, alignment, and onClick event handling.
 *
 * Concrete elements should extend this class and override methods as needed.
 */
public abstract class AbstractUIElement implements UIElement {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractUIElement.class);

    protected String id;
    protected Node node;
    protected PanelElement parentPanel;
    protected CalistaGameEngine engine;
    protected AssetManager assetManager;

    // Common layout properties with sane defaults
    protected float marginH = 0f;
    protected float marginV = 0f;
    protected float paddingH = 0f;
    protected float paddingV = 0f;
    protected float posX = 0f;
    protected float posY = 0f;
    protected String align = "center"; // Default alignment

    // onClick event handling fields
    private String onClickMethodName;
    private Object eventHandlerTarget;

    public AbstractUIElement(CalistaGameEngine engine, String id, PanelElement parent) {
        this.id = Objects.requireNonNull(id, "Element ID cannot be null");
        this.engine = Objects.requireNonNull(engine, "Engine cannot be null");
        this.assetManager = engine.getAssetManager();
        this.parentPanel = parent;
        this.node = new Node("UIElement-" + id);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Node getNode() {
        return node;
    }

    @Override
    public PanelElement getParentPanel() {
        return parentPanel;
    }

    @Override
    public void setParentPanel(PanelElement parent) {
        this.parentPanel = parent;
    }

    @Override
    public void update(float tpf) {
        // Default implementation does nothing. Override for animated elements.
    }

    @Override
    public void setProperty(String key, String value) {
        // This acts as a router for common properties.
        // Concrete classes can call super.setProperty() for unrecognized keys.
        switch (key.toLowerCase()) {
            case "posx":
                this.posX = Float.parseFloat(value);
                break;
            case "posy":
                this.posY = Float.parseFloat(value);
                break;
            case "align":
                this.align = value;
                break;
            case "margin":
                parseAndSetMargin(value);
                break;
            case "padding":
                parseAndSetPadding(value);
                break;
            case "onclick":
                this.onClickMethodName = value;
                break;
            default:
                LOGGER.trace("Property '{}' not handled by AbstractUIElement, must be handled by subclass.", key);
                break;
        }
    }

    protected void parseAndSetMargin(String value) {
        float[] values = PropertyParser.parseEdgeValues(value);
        if (values.length == 1) {
            this.marginH = values[0];
            this.marginV = values[0];
        } else if (values.length >= 2) {
            this.marginH = values[0];
            this.marginV = values[1];
        }
    }

    protected void parseAndSetPadding(String value) {
        float[] values = PropertyParser.parseEdgeValues(value);
        if (values.length == 1) {
            this.paddingH = values[0];
            this.paddingV = values[0];
        } else if (values.length >= 2) {
            this.paddingH = values[0];
            this.paddingV = values[1];
        }
    }

    @Override
    public void setEventHandler(Object target) {
        this.eventHandlerTarget = target;
    }

    @Override
    public void triggerClick() {
        if (onClickMethodName == null || eventHandlerTarget == null) {
            return;
        }
        try {
            // Find a method with no parameters
            Method method = eventHandlerTarget.getClass().getMethod(onClickMethodName);
            method.invoke(eventHandlerTarget);
        } catch (NoSuchMethodException e) {
            // Or a method that takes this UIElement as a parameter
            try {
                Method method = eventHandlerTarget.getClass().getMethod(onClickMethodName, UIElement.class);
                method.invoke(eventHandlerTarget, this);
            } catch (Exception e2) {
                LOGGER.error("Could not find or invoke onClick method '{}' on target '{}'", onClickMethodName, eventHandlerTarget.getClass().getSimpleName(), e2);
            }
        } catch (Exception e) {
            LOGGER.error("Error invoking onClick method '{}' on target '{}'", onClickMethodName, eventHandlerTarget.getClass().getSimpleName(), e);
        }
    }

    // --- Default Getters for Layout Properties ---
    @Override public float getMarginH() { return marginH; }
    @Override public float getMarginV() { return marginV; }
    @Override public float getPaddingH() { return paddingH; }
    @Override public float getPaddingV() { return paddingV; }
    @Override public String getAlign() { return align; }
    @Override public float getPosX() { return posX; }
    @Override public float getPosY() { return posY; }
}