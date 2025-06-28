package org.foxesworld.cge.modules.ui.novaUi.elements.progress;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.PropertyParser;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A configurable progress bar element. It delegates rendering and animation
 * to helper classes and integrates cleanly into the UI layout system.
 */
public class ProgressElement extends AbstractUIElement {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressElement.class);

    private final ProgressRenderer renderer;
    private final ProgressAnimator animator;

    private float width = 100f;
    private float height = 10f;
    private float border = 1f;
    private float padding = 1f;

    public ProgressElement(CalistaGameEngine engine, String id, PanelElement parent) {
        super(engine, id, parent);
        this.node.setName("Progress_" + id);

        this.renderer = new ProgressRenderer(engine.getAssetManager());
        this.animator = new ProgressAnimator(0.5f, 5f); // Start at 50%, speed 5

        this.node.attachChild(renderer.getNode());
        this.updateSize(); // Initial size setup
    }

    @Override
    public void setProperty(String key, String value) {
        boolean needsSizeUpdate = false;
        switch (key.toLowerCase()) {
            case "width":
                this.width = Float.parseFloat(value);
                needsSizeUpdate = true;
                break;
            case "height":
                this.height = Float.parseFloat(value);
                needsSizeUpdate = true;
                break;
            case "borderthickness":
                this.border = Float.parseFloat(value);
                needsSizeUpdate = true;
                break;
            case "padding": // Padding now belongs to the element itself
                this.padding = Float.parseFloat(value);
                needsSizeUpdate = true;
                break;
            case "progress":
                float p = Math.max(0f, Math.min(1f, Float.parseFloat(value)));
                animator.setTarget(p);
                break;
            case "animationspeed":
                animator.setSpeed(Float.parseFloat(value));
                break;
            case "bordercolor":
                renderer.setBorderColor(PropertyParser.parseColorRGBA(value));
                break;
            case "backgroundcolor":
                renderer.setBackgroundColor(PropertyParser.parseColorRGBA(value));
                break;
            case "fillcolor":
                renderer.setFillColor(PropertyParser.parseColorRGBA(value));
                break;
            default:
                // For margin, align, onClick, etc.
                super.setProperty(key, value);
                break;
        }
        if (needsSizeUpdate) {
            updateSize();
        }
    }

    private void updateSize() {
        renderer.updateSize(this.width, this.height, this.border, this.padding);
        renderer.updateFill(animator.getCurrentValue()); // Ensure fill is correct after resize
        if (getParentPanel() != null) {
            getParentPanel().markLayoutDirty();
        }
    }

    @Override
    public void update(float tpf) {
        if (animator.update(tpf)) {
            renderer.updateFill(animator.getCurrentValue());
        }
    }

    @Override
    public float getWidth() {
        return this.width;
    }

    @Override
    public float getHeight() {
        return this.height;
    }
}