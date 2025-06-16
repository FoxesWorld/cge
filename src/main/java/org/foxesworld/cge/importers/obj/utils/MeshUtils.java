package org.foxesworld.cge.importers.obj.utils;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;
import org.foxesworld.cge.importers.obj.Face;
import org.foxesworld.cge.importers.obj.Vertex;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for computing tangent and binormal (bitangent) vectors for a mesh.
 * <p>
 * This optimized version minimizes allocations by using primitive arrays,
 * processes buffers directly, and includes checks for degenerate UVs.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>
 * MeshUtils.computeTangentBinormal(mesh);
 * </pre>
 *
 * <h2>Performance Notes</h2>
 * <ul>
 *   <li>Avoids per-vertex Vector3f allocations by pooling result arrays.</li>
 *   <li>Processes UV and position data as float[] for cache friendliness.</li>
 *   <li>Handles degenerate UV triangles gracefully by skipping them.</li>
 * </ul>
 */
public class MeshUtils {

    /**
     * Stitches UV seams for a mesh by averaging UVs of shared vertex positions.
     * This is essential for AAA-quality normal mapping and mipmapping.
     * Returns a new List<Vector2f> that can be used as the mesh's UV buffer.
     *
     * @param faces List of faces using the mesh (each Face has a list of vertices with vertex and uv indices)
     * @param positions List of Vector3f positions
     * @param uvs List of Vector2f original UVs
     * @return List<Vector2f> of seam-stitched UVs, same order as positions
     */
    /**
     * Stitches UV seams for a mesh by averaging UVs of shared vertex positions.
     * Handles OBJ 1-based and negative indices!
     *
     * @param faces     List of faces (each Face has Vertex with OBJ indices)
     * @param positions List of vertex positions (Vector3f)
     * @param uvs       List of UVs (Vector2f)
     * @return List<Vector2f> of stitched UVs, output size = positions.size()
     */
    public static List<Vector2f> stitchUVSeams(
            List<Face> faces,
            List<Vector3f> positions,
            List<Vector2f> uvs
    ) {
        // Map from mesh position list index (0-based) to all UVs using it
        Map<Integer, List<Vector2f>> uvGroups = new HashMap<>();
        int posCount = positions.size();
        int uvCount = uvs.size();

        for (Face face : faces) {
            for (Vertex v : face.getVertices()) {
                int posIdx = resolveIndex(v.vertexIndex(), posCount);
                int uvIdx = v.hasTexture() ? resolveIndex(v.texCoordIndex(), uvCount) : -1;
                if (posIdx < 0 || posIdx >= posCount || uvIdx < 0 || uvIdx >= uvCount) continue;
                uvGroups.computeIfAbsent(posIdx, k -> new ArrayList<>()).add(uvs.get(uvIdx));
            }
        }

        // Average all UVs per position index
        List<Vector2f> stitched = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            List<Vector2f> group = uvGroups.get(i);
            if (group == null || group.isEmpty()) {
                stitched.add(new Vector2f(0, 0));
            } else {
                float u = 0f, v = 0f;
                for (Vector2f uv : group) {
                    u += uv.x;
                    v += uv.y;
                }
                u /= group.size();
                v /= group.size();
                stitched.add(new Vector2f(u, v));
            }
        }
        return stitched;
    }

    /**
     * Resolves an OBJ index (1-based or negative) to a 0-based Java index, given the list size.
     * Returns -1 if 0 (OBJ spec means "absent").
     */
    private static int resolveIndex(int objIndex, int listSize) {
        if (objIndex > 0) return objIndex - 1;
        else if (objIndex < 0) return listSize + objIndex;
        else return -1;
    }

    /**
     * Compute and set tangent and binormal (bitangent) buffers on the given mesh.
     * @param mesh The mesh to process. Must have POSITION, TEXCOORD, and INDEX buffers.
     * @throws IllegalArgumentException if required buffers are missing or invalid.
     */
    public static void computeTangentBinormal(Mesh mesh) {
        // Retrieve buffers
        VertexBuffer posBuffer = mesh.getBuffer(VertexBuffer.Type.Position);
        VertexBuffer uvBuffer  = mesh.getBuffer(VertexBuffer.Type.TexCoord);
        VertexBuffer idxBuffer = mesh.getBuffer(VertexBuffer.Type.Index);
        if (posBuffer == null || uvBuffer == null || idxBuffer == null) {
            throw new IllegalArgumentException("Mesh must contain Position, TexCoord, and Index buffers");
        }

        FloatBuffer posBuf = (FloatBuffer) posBuffer.getData();
        FloatBuffer uvBuf  = (FloatBuffer) uvBuffer.getData();
        IndexBuffer  ib    = mesh.getIndexBuffer();

        int vertexCount   = mesh.getVertexCount();
        int triCount      = mesh.getTriangleCount();

        // Read raw arrays
        float[] positions = new float[vertexCount * 3];
        float[] uvs       = new float[vertexCount * 2];
        posBuf.rewind(); posBuf.get(positions);
        uvBuf.rewind();  uvBuf.get(uvs);

        // Allocate accumulation arrays
        float[] tanAccum = new float[vertexCount * 3];
        float[] binAccum = new float[vertexCount * 3];

        // Iterate triangles
        for (int t = 0; t < triCount; t++) {
            int i1 = ib.get(t * 3);
            int i2 = ib.get(t * 3 + 1);
            int i3 = ib.get(t * 3 + 2);

            // Vertex positions
            int p1 = i1 * 3, p2 = i2 * 3, p3 = i3 * 3;
            float v1x = positions[p1],     v1y = positions[p1+1], v1z = positions[p1+2];
            float v2x = positions[p2],     v2y = positions[p2+1], v2z = positions[p2+2];
            float v3x = positions[p3],     v3y = positions[p3+1], v3z = positions[p3+2];

            // UV coords
            int uv1 = i1 * 2, uv2 = i2 * 2, uv3 = i3 * 2;
            float u1 = uvs[uv1],     v1 = uvs[uv1+1];
            float u2 = uvs[uv2],     v2 = uvs[uv2+1];
            float u3 = uvs[uv3],     v3 = uvs[uv3+1];

            // Edges
            float e1x = v2x - v1x, e1y = v2y - v1y, e1z = v2z - v1z;
            float e2x = v3x - v1x, e2y = v3y - v1y, e2z = v3z - v1z;

            float du1 = u2 - u1, dv1 = v2 - v1;
            float du2 = u3 - u1, dv2 = v3 - v1;

            float denom = du1 * dv2 - du2 * dv1;
            if (Math.abs(denom) < 1e-6f) {
                // Degenerate UV, skip this triangle
                continue;
            }
            float inv = 1.0f / denom;

            // Compute tangent and binormal
            float tx = inv * (dv2 * e1x - dv1 * e2x);
            float ty = inv * (dv2 * e1y - dv1 * e2y);
            float tz = inv * (dv2 * e1z - dv1 * e2z);

            float bx = inv * (-du2 * e1x + du1 * e2x);
            float by = inv * (-du2 * e1y + du1 * e2y);
            float bz = inv * (-du2 * e1z + du1 * e2z);

            // Accumulate
            accumulate(tanAccum, i1, tx, ty, tz);
            accumulate(tanAccum, i2, tx, ty, tz);
            accumulate(tanAccum, i3, tx, ty, tz);
            accumulate(binAccum, i1, bx, by, bz);
            accumulate(binAccum, i2, bx, by, bz);
            accumulate(binAccum, i3, bx, by, bz);
        }

        // Normalize and create buffers
        FloatBuffer tanBuf = BufferUtils.createFloatBuffer(vertexCount * 3);
        FloatBuffer binBuf = BufferUtils.createFloatBuffer(vertexCount * 3);
        for (int i = 0; i < vertexCount; i++) {
            // Tangent
            int ti = i * 3;
            Vector3f tvec = new Vector3f(tanAccum[ti], tanAccum[ti+1], tanAccum[ti+2]).normalizeLocal();
            tanBuf.put(tvec.x).put(tvec.y).put(tvec.z);
            // Binormal
            Vector3f bvec = new Vector3f(binAccum[ti], binAccum[ti+1], binAccum[ti+2]).normalizeLocal();
            binBuf.put(bvec.x).put(bvec.y).put(bvec.z);
        }
        tanBuf.flip(); binBuf.flip();

        // Set buffers on mesh
        mesh.setBuffer(VertexBuffer.Type.Tangent, 3, tanBuf);
        mesh.setBuffer(VertexBuffer.Type.Binormal, 3, binBuf);
    }

    /**
     * Helper to accumulate a vector into an array.
     */
    private static void accumulate(float[] arr, int idx, float x, float y, float z) {
        int i = idx * 3;
        arr[i]   += x;
        arr[i+1] += y;
        arr[i+2] += z;
    }
}
