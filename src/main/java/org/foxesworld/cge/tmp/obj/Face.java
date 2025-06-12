package org.foxesworld.cge.tmp.obj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Представляет одну грань (Face) OBJ-модели.
 * Поддерживает произвольное число вершин (n-gon) и может триангулировать их.
 */
public class Face {
    /** Список вершин (Vertex) грани в порядке обхода. */
    private final List<Vertex> vertices;

    /**
     * Создаёт пустую грань. Вершины можно добавить через {@link #add(int, int, int)}.
     */
    public Face() {
        this.vertices = new ArrayList<>();
    }

    /**
     * Создаёт грань из списка вершин.
     * @param verts список объектов Vertex
     */
    public Face(List<Vertex> verts) {
        this.vertices = new ArrayList<>(verts);
    }

    /**
     * Добавляет вершину к грани.
     * @param vertexIndex  индекс вершины (1-based по спецификации OBJ)
     * @param texCoordIndex индекс текстурной координаты (1-based, 0 если отсутствует)
     * @param normalIndex индекс нормали (1-based, 0 если отсутствует)
     */
    public void add(int vertexIndex, int texCoordIndex, int normalIndex) {
        this.vertices.add(new Vertex(vertexIndex, texCoordIndex, normalIndex));
    }

    /**
     * Возвращает незменяемый список всех вершин грани.
     */
    public List<Vertex> getVertices() {
        return Collections.unmodifiableList(vertices);
    }

    /**
     * Возвращает количество вершин (граней может быть треугольник, квад, полигоны и т.д.).
     */
    public int size() {
        return vertices.size();
    }

    /**
     * Триангулирует n-gon на список треугольников (Face).
     * Алгоритм: фиксируем первую вершину и создаём треугольники (0,i,i+1).
     * @return список Face-треугольников
     */
    public List<Face> triangulate() {
        List<Face> tris = new ArrayList<>();
        for (int i = 1; i + 1 < vertices.size(); i++) {
            Face tri = new Face();
            tri.vertices.add(vertices.get(0));
            tri.vertices.add(vertices.get(i));
            tri.vertices.add(vertices.get(i + 1));
            tris.add(tri);
        }
        return tris;
    }

    /**
     * Парсит строку OBJ-грани типа "f 1/2/3 4/5/6 7/8/9" и возвращает Face.
     * @param line строка из OBJ-файла
     * @throws IllegalArgumentException если формат неверный
     */
    public static Face parse(String line) {
        Face face = new Face();
        StringTokenizer tok = new StringTokenizer(line, " \t");
        if (!tok.hasMoreTokens() || !tok.nextToken().equals("f")) {
            throw new IllegalArgumentException("Invalid face line: " + line);
        }
        while (tok.hasMoreTokens()) {
            String part = tok.nextToken();
            String[] refs = part.split("/");
            try {
                int vi = Integer.parseInt(refs[0]);
                int ti = refs.length > 1 && !refs[1].isEmpty() ? Integer.parseInt(refs[1]) : 0;
                int ni = refs.length > 2 && !refs[2].isEmpty() ? Integer.parseInt(refs[2]) : 0;
                face.add(vi, ti, ni);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid index in face: " + part, e);
            }
        }
        if (face.vertices.size() < 3) {
            throw new IllegalArgumentException("Face must have at least 3 vertices: " + line);
        }
        return face;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Face{");
        vertices.forEach(v -> sb.append(v).append(", "));
        if (!vertices.isEmpty()) sb.setLength(sb.length() - 2);
        return sb.append('}').toString();
    }
}
