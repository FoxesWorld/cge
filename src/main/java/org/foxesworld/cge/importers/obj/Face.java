package org.foxesworld.cge.importers.obj;

import java.util.*;

/**
 * Represents a polygonal face in an OBJ model. A face may consist of any number of vertices (n-gon)
 * and can be decomposed into triangles for rendering.
 * <p>
 * Each vertex reference may include an index to a position, a texture coordinate, and a normal.
 * Indices follow the OBJ specification (1-based, 0 if absent).
 * </p>
 *
 * @author FoxesWorld
 * @since 1.0
 */
public class Face implements Iterable<Vertex> {

    /** Ordered list of vertex references that make up this face. */
    private final List<Vertex> vertices;

    /** Creates an empty face. Vertices can be added via {@link #add(int, int, int)}. */
    public Face() {
        this.vertices = new ArrayList<>();
    }

    /**
     * Creates a face from an existing list of vertices.
     *
     * @param verts the list of vertex references (will be copied)
     * @throws NullPointerException if verts is null
     */
    public Face(List<Vertex> verts) {
        if (verts == null) {
            throw new NullPointerException("verts must not be null");
        }
        this.vertices = new ArrayList<>(verts);
    }

    /**
     * Adds a vertex reference to this face.
     *
     * @param vertexIndex   the 1-based index of the vertex position (OBJ spec)
     * @param texCoordIndex the 1-based index of the texture coordinate (0 if none)
     * @param normalIndex   the 1-based index of the vertex normal (0 if none)
     */
    public void add(int vertexIndex, int texCoordIndex, int normalIndex) {
        if (vertexIndex <= 0) {
            throw new IllegalArgumentException("vertexIndex must be > 0");
        }
        vertices.add(new Vertex(vertexIndex, texCoordIndex, normalIndex));
    }

    /** @return unmodifiable list of {@link Vertex} objects */
    public List<Vertex> getVertices() {
        return Collections.unmodifiableList(vertices);
    }

    /** @return number of vertices in this face */
    public int size() {
        return vertices.size();
    }

    /** @return true if this face is a triangle (exactly 3 vertices) */
    public boolean isTriangle() {
        return vertices.size() == 3;
    }

    /**
     * Decomposes this n-gon face into a list of triangles using a fan triangulation strategy.
     * @return list of triangle {@link Face} instances
     */
    public List<Face> triangulate() {
        List<Face> triangles = new ArrayList<>();
        for (int i = 1; i + 1 < vertices.size(); i++) {
            Face tri = new Face();
            tri.vertices.add(vertices.get(0));
            tri.vertices.add(vertices.get(i));
            tri.vertices.add(vertices.get(i + 1));
            triangles.add(tri);
        }
        return triangles;
    }

    /**
     * Reverses the winding order of this face (useful for flipping normals).
     */
    public void reverse() {
        Collections.reverse(vertices);
    }

    /**
     * Creates a deep copy of this face.
     * @return a new {@link Face} with the same vertices
     */
    public Face copy() {
        return new Face(new ArrayList<>(this.vertices));
    }

    /**
     * Parses a face definition from an OBJ file line, e.g. "f 1/2/3 4/5/6 7/8/9".
     * Texture or normal indices may be omitted.
     *
     * @param line the OBJ face line
     * @return a new {@link Face} instance
     * @throws IllegalArgumentException if the line is invalid or has fewer than 3 vertices
     */
    public static Face parse(String line) {
        if (line == null) {
            throw new IllegalArgumentException("Face line must not be null");
        }

        String[] tokens = line.trim().split("\\s+");
        if (tokens.length < 4 || !tokens[0].equals("f")) {
            throw new IllegalArgumentException("Invalid face definition: " + line);
        }

        Face face = new Face();
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            String[] parts = token.split("/");
            try {
                int vi = Integer.parseInt(parts[0]);
                int ti = (parts.length > 1 && !parts[1].isEmpty()) ? Integer.parseInt(parts[1]) : 0;
                int ni = (parts.length > 2 && !parts[2].isEmpty()) ? Integer.parseInt(parts[2]) : 0;
                face.add(vi, ti, ni);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid index in face token: " + token, e);
            }
        }

        if (face.size() < 3) {
            throw new IllegalArgumentException("A face must contain at least 3 vertices: " + line);
        }

        return face;
    }


    @Override
    public Iterator<Vertex> iterator() {
        return getVertices().iterator();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Face{");
        for (Vertex v : vertices) {
            sb.append(v).append(", ");
        }
        if (!vertices.isEmpty()) {
            sb.setLength(sb.length() - 2);
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Face)) return false;
        Face other = (Face) obj;
        return vertices.equals(other.vertices);
    }

    @Override
    public int hashCode() {
        return vertices.hashCode();
    }
}