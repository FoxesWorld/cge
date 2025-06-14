package org.foxesworld.cge.ui.novaUi.elements.panel;

import org.foxesworld.cge.ui.novaUi.elements.ChildMetrics;
import org.foxesworld.cge.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.ui.novaUi.elements.image.ImageElement;
import org.foxesworld.cge.ui.novaUi.elements.progress.ProgressElement;
import org.foxesworld.cge.ui.novaUi.elements.text.TextElement;

import java.util.*;

/**
 * MetricsRegistry — хранит список пар (Class<? extends UIElement>, ChildMetrics),
 * по которым PanelLayout может узнать реальные размеры/сырое положение для данного элемента.
 *
 * Если появятся новые типы UIElement, просто добавьте в этот регистр ещё одну пару.
 */
public class MetricsRegistry {
    private final List<Map.Entry<Class<? extends UIElement>, ChildMetrics>> registry = new ArrayList<>();

    public MetricsRegistry() {
        registry.add(new AbstractMap.SimpleEntry<>(TextElement.class, new ChildMetrics() {
            @Override public float getRawX(UIElement ue)    { return ((TextElement) ue).getRawPosX(); }
            @Override public float getRawY(UIElement ue)    { return ((TextElement) ue).getRawPosY(); }
            @Override public float getWidth(UIElement ue)   { return ((TextElement) ue).getWidth(); }
            @Override public float getHeight(UIElement ue)  { return ((TextElement) ue).getHeight(); }
        }));
        registry.add(new AbstractMap.SimpleEntry<>(ImageElement.class, new ChildMetrics() {
            @Override public float getRawX(UIElement ue)    { return ((ImageElement) ue).getRawPosX(); }
            @Override public float getRawY(UIElement ue)    { return ((ImageElement) ue).getRawPosY(); }
            @Override public float getWidth(UIElement ue)   { return ((ImageElement) ue).getWidth(); }
            @Override public float getHeight(UIElement ue)  { return ((ImageElement) ue).getHeight(); }
        }));
        registry.add(new AbstractMap.SimpleEntry<>(ProgressElement.class, new ChildMetrics() {
            @Override public float getRawX(UIElement ue)    { return ((ProgressElement) ue).getRawPosX(); }
            @Override public float getRawY(UIElement ue)    { return ((ProgressElement) ue).getRawPosY(); }
            @Override public float getWidth(UIElement ue)   { return ((ProgressElement) ue).getWidth(); }
            @Override public float getHeight(UIElement ue)  { return ((ProgressElement) ue).getHeight(); }
        }));
        registry.add(new AbstractMap.SimpleEntry<>(PanelElement.class, new ChildMetrics() {
            @Override public float getRawX(UIElement ue)    { return ((PanelElement) ue).getRawPosX(); }
            @Override public float getRawY(UIElement ue)    { return ((PanelElement) ue).getRawPosY(); }
            @Override public float getWidth(UIElement ue)   { return ((PanelElement) ue).getCurrentWidth(); }
            @Override public float getHeight(UIElement ue)  { return ((PanelElement) ue).getCurrentHeight(); }
        }));
    }

    /**
     * Ищет первый ChildMetrics, чей класс является родителем (isInstance) данного ue.
     */
    public ChildMetrics getMetricsFor(UIElement ue) {
        for (var entry : registry) {
            if (entry.getKey().isInstance(ue)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
