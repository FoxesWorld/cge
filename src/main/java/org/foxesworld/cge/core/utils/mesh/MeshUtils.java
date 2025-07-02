package org.foxesworld.cge.core.utils.mesh;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Утилитарный класс для продвинутой обработки мешей.
 * Включает сшивку UV-швов и вычисление TBN-пространства (Tangent, Binormal, Normal).
 */
public final class MeshUtils {

    /**
     * Сшивает UV-швы, усредняя UV-координаты для вершин, имеющих одинаковую 3D-позицию.
     * Этот метод корректно обрабатывает дублированные позиции, что критично для качественного
     * текстурирования и мипмаппинга.
     *
     * @param faces     Список граней, использующих данные вершины и UV.
     * @param positions Список 3D-позиций вершин.
     * @param uvs       Список исходных UV-координат.
     * @return Новый список UV-координат, где швы сшиты. Размер списка равен positions.size().
     */
    public static List<Vector2f> stitchUVSeams(
            List<Face> faces,
            List<Vector3f> positions,
            List<Vector2f> uvs) {

        if (positions.isEmpty() || uvs.isEmpty()) {
            return new ArrayList<>(uvs);
        }

        // Ключ - 3D позиция, Значение - список всех UV-координат, найденных в этой позиции.
        // Это КОРРЕКТНЫЙ способ найти вершины для сшивки.
        Map<Vector3f, List<Vector2f>> posToUvsMap = new HashMap<>();

        for (Face face : faces) {
            for (Vertex v : face.getVertices()) {
                // Пропускаем вершины без текстурных координат
                if (!v.hasTexture()) continue;

                int posIdx = resolveIndex(v.vertexIndex(), positions.size());
                int uvIdx = resolveIndex(v.texCoordIndex(), uvs.size());

                if (posIdx < 0 || uvIdx < 0) continue;

                Vector3f position = positions.get(posIdx);
                Vector2f uv = uvs.get(uvIdx);
                posToUvsMap.computeIfAbsent(position, k -> new ArrayList<>()).add(uv);
            }
        }

        // Теперь усредняем UV для каждой уникальной 3D-позиции.
        Map<Vector3f, Vector2f> averagedUvs = new HashMap<>();
        for (Map.Entry<Vector3f, List<Vector2f>> entry : posToUvsMap.entrySet()) {
            List<Vector2f> uvGroup = entry.getValue();
            Vector2f average = new Vector2f();
            for (Vector2f uv : uvGroup) {
                average.addLocal(uv);
            }
            average.divideLocal(uvGroup.size());
            averagedUvs.put(entry.getKey(), average);
        }

        // Создаем финальный список UV, который будет соответствовать списку positions.
        List<Vector2f> stitchedUvs = new ArrayList<>(positions.size());
        for (Vector3f position : positions) {
            // Для каждой вершины в исходном списке находим ее усредненную UV-координату.
            // Если для какой-то вершины не нашлось UV (маловероятно), добавляем (0,0).
            stitchedUvs.add(averagedUvs.getOrDefault(position, Vector2f.ZERO));
        }

        return stitchedUvs;
    }

    /**
     * Вычисляет и устанавливает 4-компонентный буфер тангентов для меша.
     * Этот метод использует ортогонализацию Грама-Шмидта для корректности
     * и сохраняет направление бинормали в W-компоненте тангента,
     * что является современным и эффективным подходом.
     *
     * @param mesh Меш для обработки. Должен иметь буферы Position, Normal, TexCoord и Index.
     * @throws IllegalArgumentException если необходимые буферы отсутствуют.
     */
    public static void computeTangentBinormal(Mesh mesh) {
        VertexBuffer posVb = mesh.getBuffer(VertexBuffer.Type.Position);
        VertexBuffer normVb = mesh.getBuffer(VertexBuffer.Type.Normal);
        VertexBuffer uvVb = mesh.getBuffer(VertexBuffer.Type.TexCoord);
        IndexBuffer idxB = mesh.getIndexBuffer();

        if (posVb == null || normVb == null || uvVb == null || idxB == null) {
            throw new IllegalArgumentException("Mesh must contain Position, Normal, TexCoord, and Index data to compute tangents.");
        }

        FloatBuffer posBuf = (FloatBuffer) posVb.getData();
        FloatBuffer normBuf = (FloatBuffer) normVb.getData();
        FloatBuffer uvBuf = (FloatBuffer) uvVb.getData();

        int vertexCount = mesh.getVertexCount();
        int triCount = mesh.getTriangleCount();

        // Промежуточные массивы для накопления векторов
        Vector3f[] tanAccum = new Vector3f[vertexCount];
        Vector3f[] binAccum = new Vector3f[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            tanAccum[i] = new Vector3f();
            binAccum[i] = new Vector3f();
        }

        // Временные векторы для вычислений, чтобы избежать аллокаций в цикле
        Vector3f v1 = new Vector3f(), v2 = new Vector3f(), v3 = new Vector3f();
        Vector2f t1 = new Vector2f(), t2 = new Vector2f(), t3 = new Vector2f();
        Vector3f e1 = new Vector3f(), e2 = new Vector3f();

        // 1. Проходим по всем треугольникам и накапливаем тангенты/бинормали
        for (int i = 0; i < triCount; i++) {
            int i1 = idxB.get(i * 3);
            int i2 = idxB.get(i * 3 + 1);
            int i3 = idxB.get(i * 3 + 2);

            BufferUtils.populateFromBuffer(v1, posBuf, i1);
            BufferUtils.populateFromBuffer(v2, posBuf, i2);
            BufferUtils.populateFromBuffer(v3, posBuf, i3);

            BufferUtils.populateFromBuffer(t1, uvBuf, i1);
            BufferUtils.populateFromBuffer(t2, uvBuf, i2);
            BufferUtils.populateFromBuffer(t3, uvBuf, i3);

            v2.subtract(v1, e1); // Edge 1
            v3.subtract(v1, e2); // Edge 2

            float du1 = t2.x - t1.x, dv1 = t2.y - t1.y;
            float du2 = t3.x - t1.x, dv2 = t3.y - t1.y;

            float det = du1 * dv2 - du2 * dv1;
            if (Math.abs(det) < 1e-8f) { // Пропускаем треугольники с нулевой площадью в UV-пространстве
                continue;
            }
            float invDet = 1.0f / det;

            // T = (1/det) * ( dv2 * e1 - dv1 * e2 )
            // B = (1/det) * ( -du2 * e1 + du1 * e2 )
            Vector3f T = e1.mult(dv2).subtractLocal(e2.mult(dv1)).multLocal(invDet);
            Vector3f B = e2.mult(du1).subtractLocal(e1.mult(du2)).multLocal(invDet);

            tanAccum[i1].addLocal(T);
            tanAccum[i2].addLocal(T);
            tanAccum[i3].addLocal(T);

            binAccum[i1].addLocal(B);
            binAccum[i2].addLocal(B);
            binAccum[i3].addLocal(B);
        }

        // 2. Ортогонализация и создание 4-компонентного буфера
        FloatBuffer tangentBuf = BufferUtils.createFloatBuffer(vertexCount * 4);

        Vector3f n = new Vector3f(), t = new Vector3f(), b = new Vector3f();
        for (int i = 0; i < vertexCount; i++) {
            BufferUtils.populateFromBuffer(n, normBuf, i);
            t.set(tanAccum[i]);
            b.set(binAccum[i]);

            // Процесс ортогонализации Грама-Шмидта:
            // t' = normalize(t - (t . n) * n)
            Vector3f projected = n.mult(t.dot(n));
            Vector3f tangent = t.subtract(projected).normalizeLocal();

            // Вычисляем W-компонент (handedness)
            // w = sign( (n x t) . b )
            float w = (n.cross(t).dot(b) < 0.0f) ? -1.0f : 1.0f;

            tangentBuf.put(tangent.x).put(tangent.y).put(tangent.z).put(w);
        }
        tangentBuf.flip();

        // 3. Устанавливаем новый буфер и удаляем старый бинормальный (если был)
        mesh.setBuffer(VertexBuffer.Type.Tangent, 4, tangentBuf);
        if (mesh.getBuffer(VertexBuffer.Type.Binormal) != null) {
            mesh.clearBuffer(VertexBuffer.Type.Binormal);
        }
    }

    /**
     * Вспомогательный метод для преобразования 1-основанного или отрицательного индекса OBJ
     * в 0-основанный индекс Java.
     * @return Индекс (0-based) или -1, если OBJ-индекс равен 0.
     */
    private static int resolveIndex(int objIndex, int listSize) {
        if (objIndex > 0) return objIndex - 1;
        if (objIndex < 0) return listSize + objIndex;
        return -1; // Невалидный индекс 0
    }
}