package org.foxesworld.cge.modules.ui.novaUi.elements.panel;

import org.foxesworld.cge.modules.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.ChildMetrics;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * PanelLayout is responsible for laying out and sizing the content of a {@link PanelElement}.
 * <p>
 * It computes the content area (contentMaxX, contentMaxY), recalculates the panel's dimensions
 * (autoWidth/autoHeight or fixedWidth/fixedHeight), and positions all children, guaranteeing
 * that they remain within the panel bounds (clamping).
 * <p>
 * Supports vertical and horizontal layouts, as well as a free ("none") layout.
 */
public class PanelLayout {
    private static final Logger logger = LoggerFactory.getLogger(PanelLayout.class);

    private final PanelElement panel;
    private final MetricsRegistry metricsRegistry;

    /**
     * Constructs a new PanelLayout.
     * @param panel The panel whose layout this manager controls.
     * @param metricsRegistry A registry to provide size/position metrics for UI elements.
     */
    public PanelLayout(PanelElement panel, MetricsRegistry metricsRegistry) {
        this.panel = panel;
        this.metricsRegistry = metricsRegistry;
    }

    /**
     * Recomputes the panel's width and height based on its children's metrics (padding + content),
     * then positions every child with "clamping" so that no child can overflow the panel bounds.
     *
     * @param width  The current width constraint (may be ignored if autoWidth).
     * @param height The current height constraint (may be ignored if autoHeight).
     */
    public void recomputeAndLayOut(float width, float height) {
        if (!"none".equals(panel.getLayout())) {
            applyLayoutMode(panel.getLayout());
        }

        // 1) Compute content bounds
        float contentMaxX = 0f;
        float contentMaxY = 0f;
        List<UIElement> children = panel.getChildren();
        for (UIElement ue : children) {
            ChildMetrics metrics = metricsRegistry.getMetricsFor(ue);
            if (metrics == null) continue;

            float rawX = metrics.getRawX(ue);
            float rawY = metrics.getRawY(ue);
            float w    = metrics.getWidth(ue);
            float h    = metrics.getHeight(ue);

            contentMaxX = Math.max(contentMaxX, rawX + w);
            contentMaxY = Math.max(contentMaxY, rawY + h);
        }

        // 2) Determine panel dimensions
        float newW = panel.isAutoWidth() ? (contentMaxX + panel.getPadding()) : panel.getFixedWidth();
        float newH = panel.isAutoHeight() ? (contentMaxY + panel.getPadding()) : panel.getFixedHeight();

        // 3) Clamp and position children
        for (UIElement ue : children) {
            positionChildClamped(ue, newW, newH);
        }

        // 4) Update renderer with new size and background
        panel.getRenderer().setSize(newW, newH);
        panel.getRenderer().createBackground(newW, newH);
    }

    /**
     * Applies the given layout mode to all children.
     * Supports "vertical" and "horizontal" layouts.
     * @param mode The layout mode ("vertical", "horizontal", or "none").
     */
    private void applyLayoutMode(String mode) {
        List<UIElement> children = panel.getChildren();
        float pad = panel.getPadding();
        float spacing = panel.getSpacing();

        if ("vertical".equals(mode)) {
            float yCursor = pad;
            for (UIElement ue : children) {
                ChildMetrics m = metricsRegistry.getMetricsFor(ue);
                if (m == null) continue;
                float h = m.getHeight(ue);
                if (ue instanceof AbstractUIElement) {
                    ((AbstractUIElement) ue).setRawPosX(pad);
                    ((AbstractUIElement) ue).setRawPosY(yCursor);
                }
                yCursor += h + spacing;
            }
        } else if ("horizontal".equals(mode)) {
            float xCursor = pad;
            for (UIElement ue : children) {
                ChildMetrics m = metricsRegistry.getMetricsFor(ue);
                if (m == null) continue;
                float w = m.getWidth(ue);
                if (ue instanceof AbstractUIElement) {
                    ((AbstractUIElement) ue).setRawPosX(xCursor);
                    ((AbstractUIElement) ue).setRawPosY(pad);
                }
                xCursor += w + spacing;
            }
        }
        // "none": children are positioned by their own rawPosX/rawPosY
    }

    /**
     * Positions a child element within the panel, clamping its position to ensure it does not overflow.
     *
     * X: [padding ... panelW - padding - childWidth]
     * Y: [childHeight + padding ... panelH - padding]
     *
     * @param ue The child UI element to position.
     * @param panelW The width of the panel.
     * @param panelH The height of the panel.
     */
    private void positionChildClamped(UIElement ue, float panelW, float panelH) {
        ChildMetrics metrics = metricsRegistry.getMetricsFor(ue);
        if (metrics == null) return;

        float rawX = metrics.getRawX(ue);
        float rawY = metrics.getRawY(ue);
        float cw   = metrics.getWidth(ue);
        float ch   = metrics.getHeight(ue);
        float pad  = panel.getPadding();

        // Default position: top-left origin, y grows downward
        float px = rawX + pad;
        float py = panelH - pad - rawY - ch;

        // Clamp X: don't let child overflow panel horizontally
        px = Math.max(pad, Math.min(px, panelW - pad - cw));
        // Clamp Y: don't let child overflow panel vertically
        float topNom = py + ch;
        float topClamped = Math.max(ch + pad, Math.min(topNom, panelH - pad));
        py = topClamped - ch;

        ue.getNode().setLocalTranslation(px, py, 0f);
    }
}