package org.foxesworld.cge.tmp.menu.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.components.ViceImage;
import org.foxesworld.cge.tmp.menu.xml.ImageXml;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;

/**
 * Строитель компонента изображения из ImageXml.
 */
public class ImageBuilder implements ComponentBuilder<ImageXml> {

    @Override
    public ViceImage build(ImageXml model, Node parent, BuildContext context) {
        // Парсим размеры: использует ту же утилиту, что и ButtonBuilder
        float width = MenuUtils.parseSize(model.width, context.mainMenuAppState().getGameEngine().getCamera().getWidth());
        float height = MenuUtils.parseSize(model.height, context.mainMenuAppState().getGameEngine().getCamera().getHeight());

        // Создаём компонент (используем конструктор с простыми параметрами)
        ViceImage image = new ViceImage(model.id, context.mainMenuAppState().getGameEngine().getAssetManager(), model.path, width, height);

        // Tint
        if (model.tint != null && !model.tint.isBlank()) {
            image.setTintColor(model.tint);
        }

        if(model.scaleMode != null && !model.scaleMode.isBlank()) {
            image.setScaleMode(ViceImage.ScaleMode.valueOf(model.scaleMode.toUpperCase()));
        }

        // Background visibility
        boolean showBg = Boolean.parseBoolean(model.showBackground);
        image.setShowBackground(showBg);

        // Corner radius: парсим как размер (поддерживает %, vw/vh по MenuUtils.parseSize)
        try {
            float corner = MenuUtils.parseSize(model.cornerRadius, Math.min(context.mainMenuAppState().getGameEngine().getCamera().getWidth(), context.mainMenuAppState().getGameEngine().getCamera().getHeight()));
            image.setCornerRadius(corner);
        } catch (Exception ignored) {}

        // Позиция: рассчитываем по x/y/alignX/alignY (как у ButtonBuilder)
        Vector2f pos = MenuUtils.calculatePosition(model.x, model.y, model.alignX, context.mainMenuAppState().getGameEngine().getCamera());
        // Больше не делаем ручного сдвига по ширине — ViceImage учитывает areaOrigin.

        // Прикрепляем в сцену
        parent.attachChild(image.getNode());

        return image;
    }
}
