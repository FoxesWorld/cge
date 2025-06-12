package org.foxesworld.cge.tmp.obj;

/**
 * Record для хранения индексов вершины, текстурных координат и нормали в одной вершине.
 * Все индексы должны быть неотрицательными (0 означает отсутствие соответствующего атрибута).
 *
 * @param vertexIndex    индекс позиции вершины (1-based по спецификации OBJ)
 * @param texCoordIndex  индекс текстурной координаты (1-based, 0 если отсутствует)
 * @param normalIndex    индекс нормали (1-based, 0 если отсутствует)
 */
public record Vertex(int vertexIndex, int texCoordIndex, int normalIndex) {
    /**
     * Конструктор с проверкой корректности индексов.
     */
    public Vertex {
        if (vertexIndex < 1) {
            throw new IllegalArgumentException("vertexIndex must be >=1, but was " + vertexIndex);
        }
        if (texCoordIndex < 0) {
            throw new IllegalArgumentException("texCoordIndex must be >=0, but was " + texCoordIndex);
        }
        if (normalIndex < 0) {
            throw new IllegalArgumentException("normalIndex must be >=0, but was " + normalIndex);
        }
    }

    /** Альяс для совместимости старого кода: */
    public int getVertexIndex() {
        return vertexIndex;
    }

    /** Альяс для совместимости старого кода: */
    public int getTexCoordIndex() {
        return texCoordIndex;
    }

    /** Альяс для совместимости старого кода: */
    public int getNormalIndex() {
        return normalIndex;
    }

    /**
     * Возвращает строковое представление в формате "v/t/n".
     */
    @Override
    public String toString() {
        return String.format("%d/%d/%d", vertexIndex, texCoordIndex, normalIndex);
    }
}
