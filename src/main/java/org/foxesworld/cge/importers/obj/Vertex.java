package org.foxesworld.cge.importers.obj;

import java.util.Objects;

/**
 * Immutable record representing a single vertex reference in an OBJ model.
 * <p>
 * Stores the indices for the vertex position, texture coordinate, and normal vector.
 * According to the OBJ specification, indices are 1-based or negative for relative addressing; 0 means absent.
 * </p>
 *
 * @param vertexIndex   OBJ index of the vertex position (must not be 0)
 * @param texCoordIndex OBJ index of the texture coordinate (0 if none, otherwise 1-based or negative)
 * @param normalIndex   OBJ index of the vertex normal (0 if none, otherwise 1-based or negative)
 *
 * @author FoxesWorld
 * @since 1.1
 */
public record Vertex(int vertexIndex, int texCoordIndex, int normalIndex) {

    /**
     * Canonical constructor with index validation.
     *
     * @throws IllegalArgumentException if vertexIndex == 0 or texCoordIndex/normalIndex == 0 for present attributes
     */
    public Vertex {
        if (vertexIndex == 0) {
            throw new IllegalArgumentException(
                    "vertexIndex must not be 0 (OBJ uses 1-based or negative indices)");
        }
        // texCoordIndex and normalIndex can be 0 (meaning absent), or any other integer (OBJ allows negatives)
    }

    /**
     * Returns true if a texture coordinate index is present.
     *
     * @return {@code true} if texCoordIndex != 0
     */
    public boolean hasTexture() {
        return texCoordIndex != 0;
    }

    /**
     * Returns true if a normal index is present.
     *
     * @return {@code true} if normalIndex != 0
     */
    public boolean hasNormal() {
        return normalIndex != 0;
    }

    /**
     * Parses a vertex reference token from an OBJ face definition, e.g. "1/2/3", "4//5", or "6/7", including negative indices.
     *
     * @param token the string token from an OBJ face line
     * @return a new {@link Vertex} instance
     * @throws IllegalArgumentException if the token format is invalid or indices are out of range
     */
    public static Vertex parse(String token) {
        Objects.requireNonNull(token, "token must not be null");
        String[] parts = token.split("/");
        try {
            int vi = Integer.parseInt(parts[0]);
            int ti = (parts.length > 1 && !parts[1].isEmpty()) ? Integer.parseInt(parts[1]) : 0;
            int ni = (parts.length > 2 && !parts[2].isEmpty()) ? Integer.parseInt(parts[2]) : 0;
            return new Vertex(vi, ti, ni);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid vertex reference token: '" + token + "'", e);
        }
    }

    /**
     * Returns the OBJ-formatted string, omitting absent fields (e.g., "1/2", "1//3", or "1").
     *
     * @return formatted token string
     */
    @Override
    public String toString() {
        if (hasTexture() && hasNormal())
            return vertexIndex + "/" + texCoordIndex + "/" + normalIndex;
        if (hasTexture())
            return vertexIndex + "/" + texCoordIndex;
        if (hasNormal())
            return vertexIndex + "//" + normalIndex;
        return Integer.toString(vertexIndex);
    }
}