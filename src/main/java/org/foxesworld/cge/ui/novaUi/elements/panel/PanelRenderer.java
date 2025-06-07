package org.foxesworld.cge.ui.novaUi.elements.panel;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;

public class PanelRenderer {
    private final CalistaGameEngine engine;
    private final PanelElement panel;
    private Geometry bgGeom;
    private float currentWidth;
    private float currentHeight;

    public PanelRenderer(CalistaGameEngine engine, PanelElement panel) {
        this.engine = engine;
        this.panel = panel;
    }

    /**
     * Обновляет размеры фона панели с учётом margin и padding.
     * Если геометрия уже создана, обновляется только размер.
     */
    public void updateGeometry(float width, float height) {
        if (bgGeom == null) {
            createBackground(width, height);
        } else {
            // Обновляем только размер геометрии, если фон уже существует
            Quad quad = (Quad) bgGeom.getMesh();
            quad.updateGeometry(width, height);
        }

        // Учитываем margin для фона
        bgGeom.setLocalTranslation(panel.getMargin(), panel.getMargin(), 0f);

        currentWidth = width;
        currentHeight = height;
    }

    /**
     * Устанавливает размеры фона панели с учётом margin и padding.
     * Фон будет обновлён, если геометрия ещё не создана.
     */
    public void setSize(float width, float height) {
        currentWidth = width;
        currentHeight = height;

        if (bgGeom == null) {
            createBackground(width, height);
        } else {
            // Обновляем размер фона
            Quad quad = (Quad) bgGeom.getMesh();
            quad.updateGeometry(width, height);
        }

        bgGeom.setLocalTranslation(panel.getMargin(), panel.getMargin(), 0f); // Учитываем margin
    }

    /**
     * Возвращает ширину фона.
     */
    public float getWidth() {
        return currentWidth;
    }

    /**
     * Возвращает высоту фона.
     */
    public float getHeight() {
        return currentHeight;
    }

    /**
     * Устанавливает цвет фона.
     */
    public void setBgColor(ColorRGBA color) {
        if (bgGeom != null) {
            bgGeom.getMaterial().setColor("Color", color);
        }
    }

    /**
     * Создаёт фоновую геометрию для панели с учётом margin и padding.
     */
    void createBackground(float width, float height) {
        // Учитываем margin для фона
        float bgWidth = width + 2f * panel.getMargin();
        float bgHeight = height + 2f * panel.getMargin();

        // Создаём объект Quad для фона
        Quad quad = new Quad(bgWidth, bgHeight);
        bgGeom = new Geometry("BG_" + panel.getId(), quad);

        // Создаём и настраиваем материал для фона
        Material mat = new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        mat.setColor("Color", panel.getBgColor());
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);  // Устанавливаем альфа-режим для прозрачности
        bgGeom.setMaterial(mat);

        // Добавляем фоновую геометрию в панель
        panel.getNode().attachChild(bgGeom);

        // Устанавливаем позицию фона с учётом margin
        bgGeom.setLocalTranslation(panel.getMargin(), panel.getMargin(), 0f);
    }

    /**
     * Обновляет размер фона при изменении геометрии.
     */
    private void updateGeometry(Quad quad, float width, float height) {
        quad.updateGeometry(width, height);
    }

    /**
     * Устанавливает прозрачность фона.
     */
    public void setAlpha(float alpha) {
        if (bgGeom != null) {
            Material material = bgGeom.getMaterial();
            material.setFloat("AlphaDiscardThreshold", alpha);
        }
    }

    /**
     * Обновляет цвет и прозрачность фона
     */
    public void setColorAndAlpha(ColorRGBA color, float alpha) {
        setBgColor(color);
        setAlpha(alpha);
    }

    /**
     * Пересчитывает и обновляет размеры фона на основе панели, включая все отступы.
     */
    public void updatePanelSize() {
        float contentWidth = panel.getWidth(); // Получаем ширину контента панели
        float contentHeight = panel.getHeight(); // Получаем высоту контента панели

        // Пересчитываем размеры фона с учётом отступов
        float width = contentWidth + 2 * panel.getPadding();
        float height = contentHeight + 2 * panel.getPadding();

        // Устанавливаем новый размер фона
        setSize(width, height);
    }
}
