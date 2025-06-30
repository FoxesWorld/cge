// Файл: src/main/java/org/foxesworld/cge/modules/ecs/systems/PhysicsSystem.java
package org.foxesworld.cge.modules.ecs.systems;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import org.foxesworld.cge.modules.ecs.EntitySystem;
import org.foxesworld.cge.modules.ecs.components.PhysicsBody;
import org.foxesworld.cge.modules.ecs.components.View;
import org.foxesworld.cge.modules.physics.PhysicsModule;

import java.util.Objects;

/**
 * Добавляет физические тела сущностям.
 * Находит сущности с компонентами View и PhysicsBody и создает для них
 * RigidBody с помощью PhysicsModule.
 */
public class PhysicsSystem implements EntitySystem {
    private final PhysicsModule physicsModule;
    private EntitySet entities;

    public PhysicsSystem(PhysicsModule physicsModule) {
        this.physicsModule = Objects.requireNonNull(physicsModule, "PhysicsModule cannot be null");
    }

    @Override
    public void initialize(EntityData ed) {
        // Следим за сущностями, которые должны иметь физику и у которых есть геометрия
        entities = ed.getEntities(View.class, PhysicsBody.class);
    }

    @Override
    public void start() {}

    @Override
    public void update(float tpf) {
        if (entities.applyChanges()) {
            for (Entity e : entities.getAddedEntities()) {
                addPhysicsToEntity(e);
            }
            // Логику удаления физики можно добавить в getRemovedEntities(),
            // если ваш PhysicsModule это поддерживает.
        }
    }

    private void addPhysicsToEntity(Entity e) {
        Spatial spatial = e.get(View.class).getSpatial();
        float mass = e.get(PhysicsBody.class).getMass();

        // Рекурсивно ищем геометрию внутри Spatial и добавляем для нее физику
        if (spatial instanceof Node) {
            processNodePhysics((Node) spatial, mass);
        } else if (spatial instanceof Geometry) {
            physicsModule.addRigidBody(spatial, mass);
        }
    }

    private void processNodePhysics(Node node, float mass) {
        for (Spatial child : node.getChildren()) {
            if (child instanceof Geometry) {
                // Предполагаем, что addRigidBody корректно обрабатывает позицию и форму
                physicsModule.addRigidBody(child, mass);
            } else if (child instanceof Node) {
                // Рекурсивный вызов для вложенных нод
                processNodePhysics((Node) child, mass);
            }
        }
    }

    @Override
    public void stop() {
        entities.release();
        entities = null;
    }
}