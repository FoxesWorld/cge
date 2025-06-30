// Файл: src/main/java/org/foxesworld/cge/modules/ecs/components/View.java
package org.foxesworld.cge.modules.ecs.components;

import com.jme3.scene.Spatial;
import com.simsilica.es.EntityComponent;

/**
 * Связывает сущность с ее визуальным представлением (Spatial) в графе сцены.
 * Хранит ссылку на 3D-модель, которая будет отображаться в мире.
 */
public class View implements EntityComponent {

    private final Spatial spatial;

    /**
     * Конструктор для создания компонента.
     * @param spatial 3D-модель (Node, Geometry и т.д.), которая представляет эту сущность.
     *                Не должна быть null.
     */
    public View(Spatial spatial) {
        if (spatial == null) {
            throw new IllegalArgumentException("Spatial cannot be null for View component.");
        }
        this.spatial = spatial;
    }

    /**
     * Возвращает визуальное представление сущности.
     * @return Spatial, привязанный к этой сущности.
     */
    public Spatial getSpatial() {
        return spatial;
    }

    @Override
    public String toString() {
        return "View[" + (spatial.getName() != null ? spatial.getName() : spatial.getClass().getSimpleName()) + "]";
    }
}