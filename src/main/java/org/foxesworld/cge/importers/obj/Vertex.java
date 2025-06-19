package org.foxesworld.cge.importers.obj;

import java.util.Objects;

/**
 * Immutable record representing a single vertex reference in an OBJ model.
 * <p>
 * Stores the indices for the vertex position, texture coordinate, and normal vector.
 * According to the OBJ specification, indices are 1-based or negative for relative addressing; 0 means absent.
 * </p>
 *
 * @param vertexIndex   vertex position index (1-based or negative; 0 is invalid)
 * @param texCoordIndex texture coordinate index (1-based or negative; 0 if none)
 * @param normalIndex   normal vector index (1-based or negative; 0 if none)
 * @author FoxesWorld
 * @since 1.1
 */
public record Vertex(int vertexIndex, int texCoordIndex, int normalIndex) {

    /**
     * Canonical constructor with stricter index validation for improved reliability.
     *
     * @throws IllegalArgumentException if vertexIndex is 0
     */
    public Vertex {
        if (vertexIndex == 0) {
            throw new IllegalArgumentException("vertexIndex must not be 0 (OBJ uses 1-based or negative indices)");
        }
        // texCoordIndex and normalIndex can be 0 (meaning absent) or negative/positive otherwise
    }

    /**
     * Checks if a texture coordinate index is present (non-zero).
     */
    public boolean hasTexture() {
        return texCoordIndex != 0;
    }

    /**
     * Checks if a normal index is present (non-zero).
     */
    public boolean hasNormal() {
        return normalIndex != 0;
    }

    /**
     * Creates a Vertex from an OBJ face definition token (e.g., "1/2/3", "-1/0/-3").
     */
    public static Vertex parse(String token) {
        Objects.requireNonNull(token, "token must not be null");

        String[] parts = token.split("/", -1); // Ensures empty splits are parsed
        try {
            int vi = Integer.parseInt(parts[0]);
            int ti = (parts.length > 1 && !parts[1].isEmpty()) ? Integer.parseInt(parts[1]) : 0;
            int ni = (parts.length > 2 && !parts[2].isEmpty()) ? Integer.parseInt(parts[2]) : 0;
            return new Vertex(vi, ti, ni);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid vertex reference: '" + token + "'", e);
        }
    }

    /**
     * Returns this vertex reference in an OBJ-compliant string form (like "1/2/3", "1//3", etc.).
     */
    @Override
    public String toString() {
        if (hasTexture() && hasNormal()) {
            return vertexIndex + "/" + texCoordIndex + "/" + normalIndex;
        } else if (hasTexture()) {
            return vertexIndex + "/" + texCoordIndex;
        } else if (hasNormal()) {
            return vertexIndex + "//" + normalIndex;
        }
        return Integer.toString(vertexIndex);
    }
}