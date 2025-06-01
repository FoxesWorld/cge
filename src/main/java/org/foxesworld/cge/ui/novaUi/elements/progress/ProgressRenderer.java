package org.foxesworld.cge.ui.novaUi.elements.progress;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProgressRenderer – отвечает за построение и обновление трёх геометрий:
 *  • borderGeom – «рамка» (Quad width×height)
 *  • bgGeom     – «фон» (Quad innerW×innerH)
 *  • fgGeom     – «заливка» (Quad fillW×innerH)
 *
 * При изменении любых размеров/цветов/толщин «ProgressElement» вызывает методы
 * этой фабрики/рендерера для перестройки геометрий.
 */
public class ProgressRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ProgressRenderer.class);

    private final CalistaGameEngine engine;

    // Геометрии (могут быть null до первого build)
    private Geometry borderGeom;
    private Geometry bgGeom;
    private Geometry fgGeom;

    public ProgressRenderer(CalistaGameEngine engine) {
        this.engine = engine;
    }

    /** Возвращает текущую геометрию рамки (или null, если ещё не создана). */
    public Geometry getBorderGeom() { return borderGeom; }
    /** Возвращает текущую геометрию фона (или null). */
    public Geometry getBgGeom() { return bgGeom; }
    /** Возвращает текущую геометрию заливки (или null). */
    public Geometry getFgGeom() { return fgGeom; }

    /**
     * Строит (или пересоздаёт) рамку: Quad size=(w×h) с цветом borderColor.
     * Если ранее borderGeom уже создавался, удаляет старую геометрию из сцены и создаёт новую.
     */
    public Geometry buildBorder(float w, float h, ColorRGBA borderColor) {
        if (borderGeom != null) {
            borderGeom.removeFromParent();
        }
        Quad quad = new Quad(w, h);
        borderGeom = new Geometry("Border", quad);
        Material mat = new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        mat.setColor("Color", borderColor);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        borderGeom.setMaterial(mat);
        borderGeom.setLocalTranslation(0f, 0f, 0f);
        return borderGeom;
    }

    /**
     * Строит (или пересоздаёт) «фон»: Quad size=(innerW×innerH) с цветом backgroundColor,
     * смещённый на (borderThickness+padding, borderThickness+padding, zIndex).
     */
    public Geometry buildBackground(float innerW, float innerH,
                                    ColorRGBA backgroundColor,
                                    float borderThickness, float padding, float zIndex) {
        if (bgGeom != null) {
            bgGeom.removeFromParent();
        }
        Quad quad = new Quad(innerW, innerH);
        bgGeom = new Geometry("Background", quad);
        Material mat = new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        mat.setColor("Color", backgroundColor);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        bgGeom.setMaterial(mat);
        bgGeom.setLocalTranslation(borderThickness + padding, borderThickness + padding, zIndex);
        return bgGeom;
    }

    /**
     * Строит (или пересоздаёт) «заливку»: Quad size=(fillW×innerH) с цветом fillColor,
     * смещённый на (borderThickness+padding, borderThickness+padding, zIndex).
     */
    public Geometry buildFill(float fillW, float innerH,
                              ColorRGBA fillColor,
                              float borderThickness, float padding, float zIndex) {
        if (fgGeom != null) {
            fgGeom.removeFromParent();
        }
        Quad quad = new Quad(fillW, innerH);
        fgGeom = new Geometry("Fill", quad);
        Material mat = new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        mat.setColor("Color", fillColor);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        fgGeom.setMaterial(mat);
        fgGeom.setLocalTranslation(borderThickness + padding, borderThickness + padding, zIndex);
        return fgGeom;
    }

    /**
     * Обновляет только геометрию заливки (fgGeom), пересоздавая Quad нужной ширины fillW × innerH.
     * Если fgGeom ещё не было создано, автоматически вызывает buildFill().
     */
    public void updateFill(float fillW, float innerH,
                           ColorRGBA fillColor,
                           float borderThickness, float padding) {
        if (fgGeom == null || bgGeom == null) {
            // ещё не построены или удалены: пересоздадим всё сразу
            buildFill(fillW, innerH, fillColor, borderThickness, padding, 2f);
            return;
        }
        // Меняем mesh на Quad(new dimensions)
        fgGeom.setMesh(new Quad(fillW, innerH));
        fgGeom.getMaterial().setColor("Color", fillColor);
        fgGeom.setLocalTranslation(borderThickness + padding, borderThickness + padding, 2f);
    }
}
