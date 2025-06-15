package org.foxesworld.cge.modules.ui.novaUi.elements.progress;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProgressRenderer оптимизирован для минимизации пересоздания объектов и
 * добавлена полноценная поддержка alpha-канала для всех геометрий, включая фон.
 */
public class ProgressRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ProgressRenderer.class);

    private final CalistaGameEngine engine;

    private Geometry borderGeom;
    private Geometry bgGeom;
    private Geometry fgGeom;

    private Material borderMat;
    private Material bgMat;
    private Material fgMat;

    public ProgressRenderer(CalistaGameEngine engine) {
        this.engine = engine;
    }

    public Geometry getBorderGeom() {
        return borderGeom;
    }

    public Geometry getBgGeom() {
        return bgGeom;
    }

    public Geometry getFgGeom() {
        return fgGeom;
    }

    /**
     * Обновляет или создаёт геометрию рамки (borderGeom) с заданными параметрами.
     * @param w ширина рамки
     * @param h высота рамки
     * @param borderColor цвет рамки (ColorRGBA включает alpha)
     * @param zIndex Z-положение в сцене (чем больше, тем ближе к камере)
     * @return обновлённая или новая Geometry для рамки
     */
    public Geometry buildOrUpdateBorder(float w, float h, ColorRGBA borderColor, float zIndex) {
        if (borderGeom == null) {
            borderGeom = new Geometry("ProgressBorder", new Quad(w, h));
            borderMat = new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
            borderMat.setColor("Color", borderColor);
            borderMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            borderGeom.setMaterial(borderMat);
            borderGeom.setQueueBucket(RenderQueue.Bucket.Gui);
            borderGeom.setLocalTranslation(0f, 0f, zIndex);
        } else {
            borderGeom.setMesh(new Quad(w, h));
            borderMat.setColor("Color", borderColor);
            borderGeom.setLocalTranslation(0f, 0f, zIndex);
        }
        return borderGeom;
    }

    /**
     * Обновляет или создаёт фон (bgGeom) с заданными параметрами.
     * @param innerW ширина фона (без рамки)
     * @param innerH высота фона (без рамки)
     * @param backgroundColor цвет фона (включая alpha)
     * @param borderThickness толщина рамки
     * @param padding внутренний отступ от рамки
     * @param zIndex Z-положение в сцене
     * @return обновлённая или новая Geometry для фона
     */
    public Geometry buildOrUpdateBackground(float innerW, float innerH,
                                            ColorRGBA backgroundColor,
                                            float borderThickness, float padding, float zIndex) {
        float tx = borderThickness + padding;
        float ty = borderThickness + padding;
        if (bgGeom == null) {
            bgGeom = new Geometry("ProgressBackground", new Quad(innerW, innerH));
            bgMat = new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
            bgMat.setColor("Color", backgroundColor);
            bgMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            bgGeom.setMaterial(bgMat);
            bgGeom.setQueueBucket(RenderQueue.Bucket.Gui);
            bgGeom.setLocalTranslation(tx, ty, zIndex);
        } else {
            bgGeom.setMesh(new Quad(innerW, innerH));
            bgMat.setColor("Color", backgroundColor);
            bgGeom.setLocalTranslation(tx, ty, zIndex);
        }
        return bgGeom;
    }

    /**
     * Обновляет или создаёт заполнение (fgGeom) с заданными параметрами.
     * @param fillW ширина заполнения (пропорциональна прогрессу)
     * @param innerH высота фона (и заливки)
     * @param fillColor цвет заливки (включая alpha)
     * @param borderThickness толщина рамки
     * @param padding внутренний отступ от рамки
     * @param zIndex Z-положение в сцене
     * @return обновлённая или новая Geometry для заливки
     */
    public Geometry buildOrUpdateFill(float fillW, float innerH,
                                      ColorRGBA fillColor,
                                      float borderThickness, float padding, float zIndex) {
        float tx = borderThickness + padding;
        float ty = borderThickness + padding;
        if (fgGeom == null) {
            fgGeom = new Geometry("ProgressFill", new Quad(fillW, innerH));
            fgMat = new Material(engine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
            fgMat.setColor("Color", fillColor);
            fgMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            fgGeom.setMaterial(fgMat);
            fgGeom.setQueueBucket(RenderQueue.Bucket.Gui);
            fgGeom.setLocalTranslation(tx, ty, zIndex);
        } else {
            fgGeom.setMesh(new Quad(fillW, innerH));
            fgMat.setColor("Color", fillColor);
            fgGeom.setLocalTranslation(tx, ty, zIndex);
        }
        return fgGeom;
    }

    /**
     * Обновляет только заливку (fgGeom). Если fgGeom ещё не создана, создаёт её вместе с материалом.
     * @param fillW ширина заполнения
     * @param innerH высота фона (и заливки)
     * @param fillColor цвет заливки (включая alpha)
     * @param borderThickness толщина рамки
     * @param padding внутренний отступ
     * @param zIndex Z-положение
     */
    public void updateFill(float fillW, float innerH,
                           ColorRGBA fillColor,
                           float borderThickness, float padding, float zIndex) {
        if (fgGeom == null) {
            buildOrUpdateFill(fillW, innerH, fillColor, borderThickness, padding, zIndex);
        } else {
            fgGeom.setMesh(new Quad(fillW, innerH));
            fgMat.setColor("Color", fillColor);
            fgGeom.setLocalTranslation(borderThickness + padding, borderThickness + padding, zIndex);
        }
    }

    /**
     * Полное обновление прогресса: рамка → фон → заливка в нужном порядке.
     * Обычно вызывается при первом создании компонента или при глобальном ресайзе.
     */
    public void rebuildAll(float totalW, float totalH,
                           ColorRGBA borderColor, ColorRGBA backgroundColor, ColorRGBA fillColor,
                           float borderThickness, float padding) {
        float zBorder = 0f;
        buildOrUpdateBorder(totalW, totalH, borderColor, zBorder);

        float innerW = totalW - 2f * borderThickness;
        float innerH = totalH - 2f * borderThickness;
        float zBg = 1f;
        buildOrUpdateBackground(innerW, innerH, backgroundColor, borderThickness, padding, zBg);

        float innerContentW = innerW - 2f * padding;
        float zFill = 2f;
        buildOrUpdateFill(innerContentW, innerH - 2f * padding, fillColor, borderThickness, padding, zFill);
    }

    /**
     * Применяет alpha ко всем частям прогресса (рамка, фон, заливка).
     * @param alpha значение прозрачности [0..1]
     */
    public void setAlpha(float alpha) {
        if (borderMat != null) {
            ColorRGBA c = ((ColorRGBA) borderMat.getParam("Color").getValue()).clone();
            c.a = alpha;
            borderMat.setColor("Color", c);
        }
        if (bgMat != null) {
            ColorRGBA c = ((ColorRGBA) bgMat.getParam("Color").getValue()).clone();
            c.a = alpha;
            bgMat.setColor("Color", c);
        }
        if (fgMat != null) {
            ColorRGBA c = ((ColorRGBA) fgMat.getParam("Color").getValue()).clone();
            c.a = alpha;
            fgMat.setColor("Color", c);
        }
    }
}
