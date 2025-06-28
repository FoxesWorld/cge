package org.foxesworld.cge.modules.ui.novaUi.elements.panel;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.PropertyParser;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.layout.AbsoluteLayout;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.layout.HorizontalLayout;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.layout.UILayout;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.layout.VerticalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A container element that delegates layout logic to a configurable UILayout strategy.
 * This version is simplified for correctness and clarity.
 */
public class PanelElement extends AbstractUIElement {

    private static final Logger LOGGER = LoggerFactory.getLogger(PanelElement.class);

    private final List<UIElement> children = new ArrayList<>();
    private UILayout currentLayout = new AbsoluteLayout();
    private float spacing = 0f;

    // Sizing
    private boolean isAutoWidth = true;
    private boolean isAutoHeight = true;
    private float width = 0f;
    private float height = 0f;

    // Rendering
    private final Geometry backgroundGeom;
    private final Material backgroundMat;

    private boolean isLayoutDirty = true; // Start dirty to force initial layout

    public PanelElement(CalistaGameEngine engine, String id, PanelElement parent) {
        super(engine, id, parent);
        this.node.setName("Panel_" + id);

        // Setup rendering directly here for simplicity
        backgroundMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        backgroundMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        backgroundMat.setColor("Color", new ColorRGBA(0, 0, 0, 0)); // Default to transparent
        backgroundGeom = new Geometry("PanelBackground", new Quad(1, 1));
        backgroundGeom.setMaterial(backgroundMat);
        this.node.attachChild(backgroundGeom);
    }

    public void addChild(UIElement child) {
        if (child != null) {
            children.add(child);
            child.setParentPanel(this);
            node.attachChild(child.getNode());
            markLayoutDirty();
        }
    }

    public void removeChild(UIElement child) {
        if (child != null) {
            children.remove(child);
            node.detachChild(child.getNode());
            child.setParentPanel(null);
            markLayoutDirty();
        }
    }

    @Override
    public void setProperty(String key, String value) {
        boolean affectsLayout = true; // Assume most properties affect layout

        switch (key.toLowerCase()) {
            case "width":
                this.isAutoWidth = "auto".equalsIgnoreCase(value);
                if (!isAutoWidth) this.width = Float.parseFloat(value);
                break;
            case "height":
                this.isAutoHeight = "auto".equalsIgnoreCase(value);
                if (!isAutoHeight) this.height = Float.parseFloat(value);
                break;
            case "layout":
                setLayout(value);
                break;
            case "spacing":
                this.spacing = Float.parseFloat(value);
                break;
            case "bgcolor":
                backgroundMat.setColor("Color", PropertyParser.parseColorRGBA(value));
                affectsLayout = false; // Changing color doesn't affect size or position
                break;
            default:
                // Delegate to super for common properties like margin, padding, align
                super.setProperty(key, value);
                break;
        }

        if (affectsLayout) {
            markLayoutDirty();
        }
    }

    private void setLayout(String layoutName) {
        switch (layoutName.toLowerCase()) {
            case "horizontal" -> currentLayout = new HorizontalLayout();
            case "vertical" -> currentLayout = new VerticalLayout();
            case "none", "absolute" -> currentLayout = new AbsoluteLayout();
            default -> LOGGER.warn("Unknown layout '{}' for panel '{}'. Using absolute.", layoutName, getId());
        }
    }

    public void markLayoutDirty() {
        if (this.isLayoutDirty) return; // Already marked
        this.isLayoutDirty = true;
        if (getParentPanel() != null) {
            getParentPanel().markLayoutDirty();
        }
    }

    /**
     * Updates the panel's layout. This should be called by the central UI manager.
     * It computes the size and arranges children.
     */
    public void updateLayout() {
        // Step 1: Determine the panel's size.
        if (isAutoWidth || isAutoHeight) {
            Vector2f neededSize = currentLayout.calculateNeededSize(this);
            if (isAutoWidth) {
                this.width = neededSize.x + getPaddingH() * 2;
            }
            if (isAutoHeight) {
                this.height = neededSize.y + getPaddingV() * 2;
            }
        }

        // Step 2: Update the background geometry to match the new size.
        ((Quad) backgroundGeom.getMesh()).updateGeometry(this.width, this.height);
        //backgroundGeom.setLocalBound(null); // Force bound recalculation

        // Step 3: Arrange children within the final bounds.
        currentLayout.arrangeChildren(this);

        // Step 4: Mark as clean.
        this.isLayoutDirty = false;
        LOGGER.trace("Updated layout for panel '{}'. Final size: {}x{}", getId(), this.width, this.height);
    }

    @Override
    public void update(float tpf) {
        for (UIElement child : children) {
            child.update(tpf);
        }
    }

    @Override
    public float getWidth() { return this.width; }
    @Override
    public float getHeight() { return this.height; }

    public float getSpacing() { return spacing; }
    public List<UIElement> getChildren() { return Collections.unmodifiableList(children); }
    public boolean isLayoutDirty() { return isLayoutDirty; }
}