package org.foxesworld.cge.ui.novaUi.elements.panel;

import org.foxesworld.cge.ui.novaUi.elements.AbstractUIElement;
import org.foxesworld.cge.ui.novaUi.elements.ChildMetrics;
import org.foxesworld.cge.ui.novaUi.elements.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * PanelLayout — вычисляет «контентную» область (contentMaxX, contentMaxY),
 * пересчитывает размеры панели (autoWidth/autoHeight или fixedWidth/fixedHeight),
 * а затем позиционирует всех детей, гарантируя, что они не выйдут за пределы панели (clamping).
 */
public class PanelLayout {
    private static final Logger logger = LoggerFactory.getLogger(PanelLayout.class);

    private final PanelElement panel;
    private final MetricsRegistry metricsRegistry;

    public PanelLayout(PanelElement panel, MetricsRegistry metricsRegistry) {
        this.panel = panel;
        this.metricsRegistry = metricsRegistry;
    }

    /**
     * Пересчитывает текущую ширину/высоту панели (с учётом padding + контента),
     * а затем позиционирует каждого ребёнка с «clamp» — чтобы child не вышел за пределы panel.
     */
    public void recomputeAndLayOut(float width, float height) {
        if (!"none".equals(panel.getLayout())) {
            applyLayoutMode(panel.getLayout());
        }

        // 1) Вычисляем требуемый контент
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

        // 2) Определяем новую ширину/высоту панели
        float newW = panel.isAutoWidth() ? (contentMaxX + panel.getPadding()) : panel.getFixedWidth();
        float newH = panel.isAutoHeight() ? (contentMaxY + panel.getPadding()) : panel.getFixedHeight();

        // 3) Позиционируем каждого ребёнка, «клинапя» по границам
        for (UIElement ue : children) {
            positionChildClamped(ue, newW, newH);
        }

        panel.getRenderer().setSize(width, height); //0, 0
        panel.getRenderer().createBackground(width, height);
    }

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
    }

    /**
     * Позиционирует child по обычной формуле (rawX+padding, panelH - padding - rawY - h),
     * затем «клинапит» координаты внутри интервала [padding .. panelW - padding - childWidth] по X
     * и [childHeight+padding .. panelH - padding] по Y.
     */
    private void positionChildClamped(UIElement ue, float panelW, float panelH) {
        ChildMetrics metrics = metricsRegistry.getMetricsFor(ue);
        if (metrics == null) return;

        float rawX = metrics.getRawX(ue);
        float rawY = metrics.getRawY(ue);
        float cw   = metrics.getWidth(ue);
        float ch   = metrics.getHeight(ue);
        float pad  = panel.getPadding();

        // нормальная позиция
        float px = rawX + pad;
        float py = panelH - pad - rawY - ch;

        // clamp X
        px = Math.max(pad, Math.min(px, panelW - pad - cw));
        // clamp Y
        float topNom = py + ch;
        float topClamped = Math.max(ch + pad, Math.min(topNom, panelH - pad));
        py = topClamped - ch;

        ue.getNode().setLocalTranslation(px, py, 0f);
    }
}
