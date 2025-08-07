package org.foxesworld.cge.tmp.menu.components.utils;

import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Прямоугольник с закруглёнными углами.
 */
public class RoundedQuad extends Mesh {

    /**
     * @param width          Ширина прямоугольника
     * @param height         Высота прямоугольника
     * @param radius         Радиус закруглений
     * @param cornerSegments Количество сегментов на угол (больше = плавнее)
     */
    public RoundedQuad(float width, float height, float radius, int cornerSegments) {
        radius = FastMath.clamp(radius, 0, Math.min(width, height) / 2f);

        List<Vector3f> vertices = new ArrayList<>();
        List<Vector2f> texCoords = new ArrayList<>();
        List<Short> indices = new ArrayList<>();

        float halfW = width / 2f;
        float halfH = height / 2f;

        // Центральная часть (прямоугольник без закруглений)
        addRect(vertices, texCoords, indices,
                -halfW + radius, -halfH + radius,
                halfW - radius, halfH - radius,
                width, height);

        // Горизонтальные прямоугольники
        addRect(vertices, texCoords, indices,
                -halfW + radius, halfH - radius,
                halfW - radius, halfH,
                width, height); // верх
        addRect(vertices, texCoords, indices,
                -halfW + radius, -halfH,
                halfW - radius, -halfH + radius,
                width, height); // низ

        // Вертикальные прямоугольники
        addRect(vertices, texCoords, indices,
                -halfW, -halfH + radius,
                -halfW + radius, halfH - radius,
                width, height); // левый
        addRect(vertices, texCoords, indices,
                halfW - radius, -halfH + radius,
                halfW, halfH - radius,
                width, height); // правый

        // Четыре закруглённых угла
        generateCorner(vertices, texCoords, indices,  halfW - radius,  halfH - radius, 0f, FastMath.HALF_PI, radius, cornerSegments, width, height); // Верх-право
        generateCorner(vertices, texCoords, indices, -halfW + radius,  halfH - radius, FastMath.HALF_PI, FastMath.PI, radius, cornerSegments, width, height); // Верх-лево
        generateCorner(vertices, texCoords, indices, -halfW + radius, -halfH + radius, FastMath.PI, FastMath.PI*1.5f, radius, cornerSegments, width, height); // Низ-лево
        generateCorner(vertices, texCoords, indices,  halfW - radius, -halfH + radius, FastMath.PI*1.5f, FastMath.TWO_PI, radius, cornerSegments, width, height); // Низ-право

        // Преобразуем списки в буферы
        FloatBuffer pb = BufferUtils.createFloatBuffer(vertices.toArray(new Vector3f[0]));
        FloatBuffer tb = BufferUtils.createFloatBuffer(texCoords.toArray(new Vector2f[0]));
        ShortBuffer ib = BufferUtils.createShortBuffer(indices.size());
        for (short s : indices) ib.put(s);

        setBuffer(Type.Position, 3, pb);
        setBuffer(Type.TexCoord, 2, tb);
        setBuffer(Type.Index, 3, ib);

        updateBound();
        setStatic();
    }

    /** Добавление прямоугольной области */
    private void addRect(List<Vector3f> verts, List<Vector2f> uvs, List<Short> indices,
                         float x1, float y1, float x2, float y2,
                         float width, float height) {
        short startIndex = (short) verts.size();

        verts.add(new Vector3f(x1, y1, 0));
        verts.add(new Vector3f(x2, y1, 0));
        verts.add(new Vector3f(x2, y2, 0));
        verts.add(new Vector3f(x1, y2, 0));

        uvs.add(new Vector2f((x1 + width / 2) / width, (y1 + height / 2) / height));
        uvs.add(new Vector2f((x2 + width / 2) / width, (y1 + height / 2) / height));
        uvs.add(new Vector2f((x2 + width / 2) / width, (y2 + height / 2) / height));
        uvs.add(new Vector2f((x1 + width / 2) / width, (y2 + height / 2) / height));

        indices.add((short) (startIndex));
        indices.add((short) (startIndex + 1));
        indices.add((short) (startIndex + 2));

        indices.add((short) (startIndex));
        indices.add((short) (startIndex + 2));
        indices.add((short) (startIndex + 3));
    }

    /** Генерация одного угла */
    private void generateCorner(List<Vector3f> verts, List<Vector2f> uvs, List<Short> indices,
                                float cx, float cy, float startAngle, float endAngle, float radius, int segments,
                                float width, float height) {
        short centerIndex = (short) verts.size();
        verts.add(new Vector3f(cx, cy, 0));
        uvs.add(new Vector2f((cx + width / 2) / width, (cy + height / 2) / height));

        float step = (endAngle - startAngle) / segments;
        for (int i = 0; i <= segments; i++) {
            float angle = startAngle + step * i;
            float x = cx + radius * FastMath.cos(angle);
            float y = cy + radius * FastMath.sin(angle);

            verts.add(new Vector3f(x, y, 0));
            uvs.add(new Vector2f((x + width / 2) / width, (y + height / 2) / height));
        }

        for (int i = 0; i < segments; i++) {
            indices.add(centerIndex);
            indices.add((short) (centerIndex + i + 1));
            indices.add((short) (centerIndex + i + 2));
        }
    }
}
