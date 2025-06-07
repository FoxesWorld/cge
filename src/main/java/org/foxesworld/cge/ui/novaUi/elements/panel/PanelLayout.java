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

        // Оптимизация: получаем метрики для всех детей только один раз
        for (UIElement ue : children) {
            ChildMetrics metrics = metricsRegistry.getMetricsFor(ue);
            if (metrics == null) continue;

            contentMaxX = Math.max(contentMaxX, metrics.getRawX(ue) + metrics.getWidth(ue));
            contentMaxY = Math.max(contentMaxY, metrics.getRawY(ue) + metrics.getHeight(ue));
        }

        // 2) Определяем новую ширину/высоту панели
        float padding = panel.getPadding();
        float newW = panel.isAutoWidth() ? (contentMaxX + padding) : panel.getFixedWidth();
        float newH = panel.isAutoHeight() ? (contentMaxY + padding) : panel.getFixedHeight();

        // 3) Позиционируем каждого ребёнка, «клинапя» по границам
        for (UIElement ue : children) {
            positionChildClamped(ue, newW, newH);
        }

        // Обновляем фон панели с учётом новых размеров
        panel.getRenderer().setSize(width, height);
        panel.getRenderer().createBackground(width, height);
    }

    private void applyLayoutMode(String mode) {
        List<UIElement> children = panel.getChildren();
        float pad = panel.getPadding();
        float spacing = panel.getSpacing();

        // Универсальная обработка вертикального и горизонтального выравнивания
        switch (mode.toLowerCase()) {
            case "vertical":
                positionVertical(children, pad, spacing);
                break;
            case "horizontal":
                positionHorizontal(children, pad, spacing);
                break;
            case "grid":
                positionGrid(children, pad, spacing);
                break;
            case "stack":
                positionStack(children, pad, spacing);
                break;
            default:
                logger.warn("Unknown layout mode: {}", mode);
                break;
        }
    }

    private void positionVertical(List<UIElement> children, float pad, float spacing) {
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
    }

    private void positionHorizontal(List<UIElement> children, float pad, float spacing) {
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

    private void positionGrid(List<UIElement> children, float pad, float spacing) {
        // Простая сетка: предполагаем, что количество столбцов известно
        int columns = 3;
        int row = 0, col = 0;
        for (UIElement ue : children) {
            ChildMetrics m = metricsRegistry.getMetricsFor(ue);
            if (m == null) continue;

            float w = m.getWidth(ue);
            float h = m.getHeight(ue);
            float x = pad + (col * (w + spacing));
            float y = pad + (row * (h + spacing));

            if (ue instanceof AbstractUIElement) {
                ((AbstractUIElement) ue).setRawPosX(x);
                ((AbstractUIElement) ue).setRawPosY(y);
            }

            // Следующий элемент в сетке
            col++;
            if (col >= columns) {
                col = 0;
                row++;
            }
        }
    }

    private void positionStack(List<UIElement> children, float pad, float spacing) {
        // Ставим все элементы друг на друга
        float yCursor = pad;
        for (UIElement ue : children) {
            ChildMetrics m = metricsRegistry.getMetricsFor(ue);
            if (m == null) continue;

            float w = m.getWidth(ue);
            if (ue instanceof AbstractUIElement) {
                ((AbstractUIElement) ue).setRawPosX(pad);
                ((AbstractUIElement) ue).setRawPosY(yCursor);
            }
            yCursor += m.getHeight(ue) + spacing;
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