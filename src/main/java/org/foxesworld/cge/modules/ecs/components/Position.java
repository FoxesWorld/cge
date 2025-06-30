// Файл: src/main/java/org/foxesworld/cge/modules/ecs/components/Position.java
package org.foxesworld.cge.modules.ecs.components;

import com.jme3.math.Vector3f;
import com.simsilica.es.EntityComponent;

/**
 * Хранит позицию сущности в мировых координатах.
 * Это компонент-данные, содержащий только значение Vector3f.
 */
public class Position implements EntityComponent {

    private final Vector3f value;

    /**
     * Конструктор для создания компонента с начальной позицией.
     * @param value Вектор позиции. Рекомендуется передавать новый экземпляр,
     *              чтобы избежать случайного изменения извне.
     */
    public Position(Vector3f value) {
        this.value = value;
    }

    /**
     * Конструктор для создания компонента в начале координат (0, 0, 0).
     */
    public Position() {
        this.value = new Vector3f();
    }

    /**
     * Конструктор для создания компонента по координатам.
     */
    public Position(float x, float y, float z) {
        this.value = new Vector3f(x, y, z);
    }

    /**
     * Возвращает вектор позиции.
     * @return Vector3f, представляющий позицию.
     */
    public Vector3f getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Position[" + value + "]";
    }
}