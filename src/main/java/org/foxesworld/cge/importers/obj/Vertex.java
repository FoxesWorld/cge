package org.foxesworld.cge.importers.obj;

import java.util.Objects;

/**
 * Immutable record representing a single vertex reference in an OBJ model.
 * <p>
 * Stores the indices for the vertex position, texture coordinate, and normal vector.
 * According to the OBJ specification, indices are 1-based; a value of 0 indicates the attribute is absent.
 * </p>
 *
 * @param vertexIndex   1-based index of the vertex position (must be >= 1)
 * @param texCoordIndex 1-based index of the texture coordinate (0 if none)
 * @param normalIndex   1-based index of the vertex normal (0 if none)
 *
 * @author FoxesWorld
 * @since 1.0
 */
public record Vertex(int vertexIndex, int texCoordIndex, int normalIndex) {

    /**
     * Canonical constructor with index validation.
     *
     * @throws IllegalArgumentException if vertexIndex < 1 or texCoordIndex/normalIndex < 0
     */
    public Vertex {
        if (vertexIndex < 1) {
            throw new IllegalArgumentException(
                    "vertexIndex must be >= 1, but was " + vertexIndex);
        }
        if (texCoordIndex < 0) {
            throw new IllegalArgumentException(
                    "texCoordIndex must be >= 0, but was " + texCoordIndex);
        }
        if (normalIndex < 0) {
            throw new IllegalArgumentException(
                    "normalIndex must be >= 0, but was " + normalIndex);
        }
    }

    /**
     * Returns true if a texture coordinate index is present.
     *
     * @return {@code true} if texCoordIndex > 0
     */
    public boolean hasTexture() {
        return texCoordIndex > 0;
    }

    /**
     * Returns true if a normal index is present.
     *
     * @return {@code true} if normalIndex > 0
     */
    public boolean hasNormal() {
        return normalIndex > 0;
    }

    /**
     * Parses a vertex reference token from an OBJ face definition, e.g. "1/2/3", "4//5", or "6/7".
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

    @Override
    public int vertexIndex() {
        return vertexIndex;
    }

    /**
     * Returns the OBJ-formatted string "vertex/texCoord/normal".
     *
     * @return formatted token string
     */
    @Override
    public String toString() {
        return vertexIndex + "/" + texCoordIndex + "/" + normalIndex;
    }
}