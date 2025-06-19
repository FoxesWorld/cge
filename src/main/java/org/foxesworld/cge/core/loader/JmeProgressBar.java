package org.foxesworld.cge.core.loader;

import com.jme3.app.Application;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.material.Material;
import org.foxesworld.cge.CalistaGameEngine;

/**
 * Показывает компактный прогресс загрузки ассетов в правом нижнем углу JME3.
 * Текст — желтый, границы полосы — синие, заполнение — зеленое.
 * Сзади — затемнённый прямоугольник (фон).
 */
@Deprecated
public class JmeProgressBar implements AssetProgressListener {
    private final Application app;
    private final Node guiNode;
    private BitmapText progressText;
    private Geometry background;
    private int barLen = 12; // короткая полоса
    private int lastPercent = -1;
    private int padX = 12;
    private int padY = 8;

    public JmeProgressBar(CalistaGameEngine app) {
        this.app = app;
        this.guiNode = app.getGuiNode();

        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        progressText = new BitmapText(font, false);
        progressText.setSize(18);
        progressText.setColor(ColorRGBA.Yellow);
        progressText.setText("");
        guiNode.attachChild(progressText);

        // Фон — полупрозрачный черный прямоугольник, чуть больше текста
        Quad quad = new Quad(220, 32); // размер обновится динамически
        background = new Geometry("progressBg", quad);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0, 0, 0, 0.65f)); // полупрозрачный черный
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        background.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);
        background.setMaterial(mat);
        guiNode.attachChild(background);

        //placeBottomRight();
        //background.move(0, 0, -1); // всегда позади текста
    }

    private void placeBottomRight() {
        int screenWidth = app.getCamera().getWidth();
        int screenHeight = app.getCamera().getHeight();

        float textWidth = progressText.getLineWidth();
        float textHeight = progressText.getLineHeight();

        // Размер фона — чуть шире текста + паддинги
        float bgWidth = textWidth + padX * 2;
        float bgHeight = textHeight + padY * 2;
        ((Quad)background.getMesh()).updateGeometry(bgWidth, bgHeight);

        // Позиция текста — внутри фона c отступами
        progressText.setLocalTranslation(
                screenWidth - bgWidth + padX - 20,
                textHeight + padY + 20,
                0
        );
        // Позиция фона — совпадает с текстом, но левее и ниже на паддинги
        background.setLocalTranslation(
                screenWidth - bgWidth - 20,
                padY + 20,
                0
        );
    }

    @Override
    public void onProgress(String assetType, int loaded, int total) {
        /*
        int percent = total > 0 ? (int) ((loaded * 100.0) / total) : 100;
        if (percent == lastPercent && loaded != total) return;
        lastPercent = percent;
        int complete = percent * barLen / 100;

        // Генерируем короткую полосу: синие скобки, зеленые #, дефисы
        String blue = "[#00aaff]";
        String green = "[#44ff44]";
        String reset = "[#ffff00]";
        StringBuilder barColored = new StringBuilder();
        barColored.append(blue).append("[");
        for (int i = 0; i < barLen; i++) {
            if (i < complete) {
                barColored.append(green).append("#");
            } else {
                barColored.append(reset).append("-");
            }
        }
        barColored.append(blue).append("]").append(reset);

        String text = String.format("%s %s %3d%%", assetType, barColored, percent);

        app.enqueue(() -> {
            progressText.setText(text);
            progressText.setColor(ColorRGBA.Yellow);
            placeBottomRight();
            background.setCullHint(loaded == total ? Geometry.CullHint.Always : Geometry.CullHint.Inherit);
            if (loaded == total) {
                progressText.setText("");
            }
        });
         */
    }
}