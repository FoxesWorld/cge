// Файл: src/main/java/org/foxesworld/cge/modules/ecs/systems/SceneGraphSystem.java
package org.foxesworld.cge.modules.ecs.systems;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import org.foxesworld.cge.modules.ecs.EntitySystem;
import org.foxesworld.cge.modules.ecs.components.Position;
import org.foxesworld.cge.modules.ecs.components.View;

/**
 * Управляет жизненным циклом Spatial в графе сцены.
 * - Добавляет Spatial новых сущностей в rootNode.
 * - Удаляет Spatial из rootNode при удалении сущностей.
 * - (Опционально) Обновляет позицию Spatial, если компонент Position изменился.
 */
public class SceneGraphSystem implements EntitySystem {

    private final Node rootNode;
    private EntitySet entities;

    public SceneGraphSystem(Node rootNode) {
        this.rootNode = rootNode;
    }

    @Override
    public void initialize(EntityData ed) {
        // Следим за сущностями, у которых есть и визуальное представление, и позиция
        entities = ed.getEntities(View.class, Position.class);
    }

    @Override
    public void start() {
        // Можно сразу обработать сущности, которые уже существуют на момент старта
        entities.applyChanges();
        for (Entity e : entities) {
            attachSpatial(e);
        }
    }

    @Override
    public void update(float tpf) {
        // Проверяем, не появились ли новые сущности или не удалились ли старые
        if (entities.applyChanges()) {
            for (Entity e : entities.getAddedEntities()) {
                attachSpatial(e);
            }
            for (Entity e : entities.getRemovedEntities()) {
                // Важно! Получаем компонент до того, как сущность будет полностью удалена
                View view = e.get(View.class);
                if (view != null) {
                    view.getSpatial().removeFromParent();
                }
            }
            for (Entity e : entities.getChangedEntities()) {
                // Если позиция изменилась, обновляем ее в сцене
                updateSpatialPosition(e);
            }
        }
    }

    private void attachSpatial(Entity e) {
        Spatial spatial = e.get(View.class).getSpatial();
        Vector3f pos = e.get(Position.class).getValue();
        spatial.setLocalTranslation(pos);
        rootNode.attachChild(spatial);
    }

    private void updateSpatialPosition(Entity e) {
        Spatial spatial = e.get(View.class).getSpatial();
        Vector3f pos = e.get(Position.class).getValue();
        spatial.setLocalTranslation(pos);
    }

    @Override
    public void stop() {
        // Очищаем сцену от всех наших Spatial
        for (Entity e : entities) {
            e.get(View.class).getSpatial().removeFromParent();
        }
        // Освобождаем ресурсы EntitySet
        entities.release();
        entities = null;
    }
}