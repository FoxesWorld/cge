// Файл: src/main/java/org/foxesworld/cge/modules/ecs/components/PhysicsBody.java
package org.foxesworld.cge.modules.ecs.components;

import com.simsilica.es.EntityComponent;

/**
 * Компонент, указывающий, что сущность должна иметь физическое тело (RigidBody).
 * Он сообщает PhysicsSystem, что для этой сущности нужно создать физический объект.
 *
 * Хранит базовые физические свойства, такие как масса.
 */
public class PhysicsBody implements EntityComponent {

    private final float mass;

    /**
     * Создает компонент с указанной массой.
     * @param mass Масса объекта в условных единицах (например, килограммах).
     *             Масса 0.0f обычно означает статический (неподвижный) объект.
     */
    public PhysicsBody(float mass) {
        this.mass = mass;
    }

    /**
     * Возвращает массу, указанную для этого физического тела.
     * @return масса объекта.
     */
    public float getMass() {
        return mass;
    }

    @Override
    public String toString() {
        return "PhysicsBody[mass=" + mass + "]";
    }
}