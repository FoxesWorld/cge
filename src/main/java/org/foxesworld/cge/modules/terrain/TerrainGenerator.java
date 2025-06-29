package org.foxesworld.cge.modules.terrain;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

/**
 * A utility class for procedural terrain mesh generation.
 * It creates a grid-based mesh with heights determined by the custom SimplexNoise class.
 * This class is designed to be a static utility and should not be instantiated.
 */
public final class TerrainGenerator {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private TerrainGenerator() {}

    /**
     * Generates a terrain mesh based on Simplex noise.
     *
     * @param size         The number of vertices along one edge (e.g., 65, 129 for 2^n + 1).
     * @param patchSize    The physical size of each grid cell. Affects the overall terrain dimensions.
     * @param heightScale  A multiplier for the noise to control the maximum height of hills.
     * @param noiseScale   The frequency of the noise. Smaller values create broader, smoother hills.
     * @param seed         The seed for the noise generator, allowing for reproducible terrains.
     * @return A new, ready-to-use Mesh object representing the generated terrain.
     */
    public static Mesh generateTerrainMesh(int size, float patchSize, float heightScale, float noiseScale, long seed) {
        if (size <= 1) {
            throw new IllegalArgumentException("Size must be greater than 1.");
        }

        // 1. Initialize the noise generator with the provided seed
        SimplexNoise simplex = new SimplexNoise(seed);

        // 2. Prepare buffers for mesh data
        int vertexCount = size * size;
        int triangleCount = (size - 1) * (size - 1) * 2;

        float[] vertices = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] texCoords = new float[vertexCount * 2];
        int[] indices = new int[triangleCount * 3];

        // --- Step A: Generate Vertices and Texture Coordinates ---
        int vertIndex = 0;
        int texIndex = 0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                // Calculate vertex position
                float px = x * patchSize;
                float pz = z * patchSize;

                // Get noise value from your SimplexNoise class
                float noiseValue = simplex.noise(x * noiseScale, z * noiseScale);

                // Convert noise from [-1, 1] to [0, 1] and then scale by height
                float py = ((noiseValue + 1f) * 0.5f) * heightScale;

                vertices[vertIndex++] = px;
                vertices[vertIndex++] = py;
                vertices[vertIndex++] = pz;

                // Calculate texture coordinates
                texCoords[texIndex++] = (float) x / (size - 1);
                texCoords[texIndex++] = (float) z / (size - 1);
            }
        }

        // --- Step B: Generate Indices to form triangles ---
        int indicesIndex = 0;
        for (int z = 0; z < size - 1; z++) {
            for (int x = 0; x < size - 1; x++) {
                int topLeft = (z * size) + x;
                int topRight = topLeft + 1;
                int bottomLeft = ((z + 1) * size) + x;
                int bottomRight = bottomLeft + 1;

                // First triangle of the quad
                indices[indicesIndex++] = topLeft;
                indices[indicesIndex++] = bottomLeft;
                indices[indicesIndex++] = topRight;

                // Second triangle of the quad
                indices[indicesIndex++] = topRight;
                indices[indicesIndex++] = bottomLeft;
                indices[indicesIndex++] = bottomRight;
            }
        }

        // --- Step C: Calculate Normals for correct lighting ---
        int normIndex = 0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                // Get heights of neighboring vertices to determine the surface's slope
                float hL = getHeight(vertices, x - 1, z, size); // Height Left
                float hR = getHeight(vertices, x + 1, z, size); // Height Right
                float hD = getHeight(vertices, x, z - 1, size); // Height Down
                float hU = getHeight(vertices, x, z + 1, size); // Height Up

                // Create a normal vector from the slopes and normalize it
                Vector3f normal = new Vector3f(hL - hR, 2f * patchSize, hD - hU).normalizeLocal();

                normals[normIndex++] = normal.x;
                normals[normIndex++] = normal.y;
                normals[normIndex++] = normal.z;
            }
        }

        // --- Step D: Assemble the Mesh ---
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(texCoords));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));

        // Finalize the mesh
        mesh.updateBound();
        mesh.setStatic(); // Mark as static for performance optimizations by the engine
        return mesh;
    }

    /**
     * A helper method to safely get the height (Y-component) of a vertex from the array.
     * It handles out-of-bounds requests by clamping the coordinates to the edges.
     *
     * @param vertices The array of vertex data.
     * @param x        The x-coordinate in the grid.
     * @param z        The z-coordinate in the grid.
     * @param size     The size of one edge of the grid.
     * @return The height of the vertex.
     */
    private static float getHeight(float[] vertices, int x, int z, int size) {
        // Clamp coordinates to stay within the grid bounds (clamp-to-edge)
        if (x < 0) x = 0;
        if (x >= size) x = size - 1;
        if (z < 0) z = 0;
        if (z >= size) z = size - 1;

        // The Y component is at index (z * size + x) * 3 + 1
        return vertices[(z * size + x) * 3 + 1];
    }
}