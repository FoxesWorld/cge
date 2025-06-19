package org.foxesworld.cge.importers.obj;

import com.jme3.asset.*;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.RendererException;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import jme3utilities.MyAsset;
import org.foxesworld.cge.importers.obj.utils.MTLloader;
import org.foxesworld.cge.importers.obj.utils.MeshUtils;
import org.foxesworld.cge.importers.obj.MaterialData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Improved OBJ importer with optimizations for UV generation, seam stitching,
 * and a streamlined approach for bounding box and buffer management.
 * <p>
 * Key Performance Improvements:
 * <ul>
 *   <li>Bounding box computed once in parse phase and reused.</li>
 *   <li>Shared position and normal buffers without duplicate() calls where possible.</li>
 *   <li>Fail-fast checks to skip unnecessary processing when data is missing or empty.</li>
 * </ul>
 */
public final class OBJImporter implements AssetLoader {

    private static final Logger log = LoggerFactory.getLogger(OBJImporter.class);
    private static final Map<String, Texture> TEX_CACHE = new ConcurrentHashMap<>();

    /**
     * Enumeration for fallback UV projection modes:
     * AUTO, XY, XZ, YZ, TRIPLANAR
     */
    public enum UVProjection {
        AUTO, XY, XZ, YZ, TRIPLANAR
    }

    /** Default UV projection if OBJ has no UV data. */
    private final UVProjection defaultProjection;
    /** Whether to recalc tangents after UV generation. */
    private final boolean recalcTangentsAfterUV;
    /** Whether to attempt stitching UV seams. */
    private final boolean stitchUVSeams;

    /**
     * Creates an OBJImporter with auto-projection, tangent recalculation, and seam stitching by default.
     */
    public OBJImporter() {
        this(UVProjection.AUTO, true, true);
    }

    /**
     * Creates an OBJImporter with customizable UV projection specifics.
     *
     * @param uvProj                 the fallback projection mode
     * @param recalcTangentsAfterUV  if {@code true}, tangents are recalculated after UV is generated
     * @param stitchUVSeams          if {@code true}, attempt seam stitching across face boundaries
     */
    public OBJImporter(UVProjection uvProj, boolean recalcTangentsAfterUV, boolean stitchUVSeams) {
        this.defaultProjection = uvProj;
        this.recalcTangentsAfterUV = recalcTangentsAfterUV;
        this.stitchUVSeams = stitchUVSeams;
    }

    @Override
    public Spatial load(AssetInfo info) throws IOException {
        if (!(info.getKey() instanceof ModelKey mk)) {
            throw new IllegalArgumentException("ModelKey expected");
        }
        String dir = directoryOf(mk.getName());
        Parsed parsed = parseOBJ(info, dir);

        // Quick check for valid geometry
        if (parsed.vertices.isEmpty() ||
                parsed.facesByMat.values().stream().allMatch(List::isEmpty)) {
            throw new RendererException("OBJ has no valid geometry: " + mk.getName());
        }
        return buildScene(parsed, info.getManager());
    }

    /**
     * Internal record holding parsed OBJ data.
     */
    private record Parsed(
            List<Vector3f> vertices,
            List<Vector3f> normals,
            List<Vector2f> uvs,
            Map<String, List<Face>> facesByMat,
            Map<String, MaterialData> materials,
            BoundingBox bounds
    ) {}

    /**
     * Parses the OBJ data, collecting vertices, normals, UVs, faces grouped by material,
     * loaded MTL data, and a bounding box. The bounding box is computed once after parsing.
     */
    private Parsed parseOBJ(AssetInfo info, String dir) throws IOException {
        AssetManager am = info.getManager();

        List<Vector3f> vPos = new ArrayList<>();
        List<Vector3f> vNrm = new ArrayList<>();
        List<Vector2f> vTex = new ArrayList<>();
        Map<String, List<Face>> faces = new LinkedHashMap<>();
        Map<String, MaterialData> mats = new HashMap<>();

        String currentMat = "default";
        faces.put(currentMat, new ArrayList<>());

        try (BufferedReader br = new BufferedReader(new InputStreamReader(info.openStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] tok = line.split("\\s+");
                switch (tok[0]) {
                    case "v" -> vPos.add(parseVec3(tok));
                    case "vn" -> vNrm.add(parseVec3(tok));
                    case "vt" -> {
                        float u = parseF(tok[1]);
                        // Flip the V coordinate for OpenGL alignment
                        float v = 1f - parseF(tok[2]);
                        vTex.add(new Vector2f(u, v));
                    }
                    case "f" -> {
                        Face f = Face.parse(line);
                        faces.get(currentMat).add(f);
                    }
                    case "mtllib" -> {
                        // Load MTL file once and store
                        mats.putAll(MTLloader.loadMTL(am, dir + tok[1]));
                    }
                    case "usemtl" -> {
                        currentMat = tok[1];
                        faces.computeIfAbsent(currentMat, k -> new ArrayList<>());
                    }
                    default -> log.debug("Ignoring token: {}", tok[0]);
                }
            }
        }

        // Precompute bounding box once for the entire set of vertices
        BoundingBox bb = computeBounds(vPos);
        return new Parsed(vPos, vNrm, vTex, faces, mats, bb);
    }

    public Vector3f parseVec3(String[] tokens) {
        if (tokens.length < 4) {
            // Log or handle error, returning default vector
            log.warn("Not enough tokens to parse Vector3f: {}", (Object) tokens);
            return new Vector3f(0, 0, 0);
        }
        float x = parseF(tokens[1]);
        float y = parseF(tokens[2]);
        float z = parseF(tokens[3]);
        return new Vector3f(x, y, z);
    }



    /**
     * Builds a jME scene from parsed OBJ data, creating geometry per material group
     * while optimizing buffer usage where possible.
     */
    private Spatial buildScene(Parsed parsed, AssetManager am) {
        Node root = new Node("OBJ-Root");

        final int vCount = parsed.vertices.size();
        final boolean hasNormals = (parsed.normals.size() == vCount);
        final boolean hasUVs = (parsed.uvs.size() == vCount);

        // Build position and normal buffers (no duplicate() needed, share across submeshes).
        FloatBuffer posBuf = bufFrom(parsed.vertices, 3);
        FloatBuffer nrmBuf = hasNormals ? bufFrom(parsed.normals, 3) : defaultNormals(vCount);

        // We'll compute or use existing UVs on a per-Geometry basis. If we directly share a
        // single UV buffer, applying transforms would conflict among submeshes. So each geometry
        // gets its own TexCoord buffer.
        BoundingBox bb = parsed.bounds;
        Vector3f size = bb.getExtent(new Vector3f()).multLocal(2f);

        // For each material group, construct a geometry and its mesh
        for (Map.Entry<String, List<Face>> entry : parsed.facesByMat.entrySet()) {
            String matName = entry.getKey();
            List<Face> faceList = entry.getValue();
            if (faceList.isEmpty()) {
                continue;
            }

            // Triangulate and build the index buffer
            int triCount = faceList.stream().mapToInt(f -> Math.max(1, f.size() - 2)).sum();
            IntBuffer idxBuf = BufferUtils.createIntBuffer(triCount * 3);

            // If we have UV data, prepare a local copy so we can do seam stitching or transforms
            List<Vector2f> geomUVs = hasUVs ? new ArrayList<>(parsed.uvs) : null;

            if (hasUVs && stitchUVSeams) {
                // Stitching modifies a fresh copy of the vertex UV set
                geomUVs = MeshUtils.stitchUVSeams(faceList, parsed.vertices, parsed.uvs);
            }

            // Fill index buffer by triangulating each face
            for (Face f : faceList) {
                for (Face tri : (f.size() == 3 ? List.of(f) : f.triangulate())) {
                    for (var vert : tri.getVertices()) {
                        int idx = correctIndex(vert.vertexIndex(), vCount);
                        idxBuf.put(idx);
                    }
                }
            }
            idxBuf.flip();

            Mesh mesh = new Mesh();
            mesh.setBuffer(Type.Position, 3, posBuf); // share position buffer
            mesh.setBuffer(Type.Normal,   3, nrmBuf); // share normal buffer

            // Construct or fallback for UV
            if (geomUVs != null && geomUVs.size() == vCount) {
                // Use the submesh-unique UV buffer (transform may be applied below)
                mesh.setBuffer(Type.TexCoord, 2, bufFromUV(geomUVs));
            } else if (defaultProjection == UVProjection.TRIPLANAR) {
                mesh.setBuffer(Type.TexCoord, 2, triplanarUV(bb, parsed.vertices));
            } else {
                mesh.setBuffer(Type.TexCoord, 2, planarUV(defaultProjection, bb, parsed.vertices));
            }

            mesh.setBuffer(Type.Index, 3, idxBuf);
            mesh.updateBound();

            // Optional tangent-space recomputation if we have valid normals/UV
            if (recalcTangentsAfterUV && hasNormals && mesh.getBuffer(Type.TexCoord) != null) {
                MeshUtils.computeTangentBinormal(mesh);
            }

            Geometry geo = new Geometry(matName, mesh);
            geo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            geo.setUserData("size", size);

            // Build and apply material
            MaterialData md = parsed.materials.get(matName);
            Material mat = createMaterial(am, md);
            geo.setMaterial(mat);

            // Apply texture scaling/offset transforms for this geometry
            applyUVTransform(mesh, md, size, am);

            // Factor in any scaling from MTL
            if (md != null && md.getScale() != null) {
                geo.setLocalScale(md.getScale());
            }

            // Create the collision shape
            float mass = (md != null && md.getMass() > 0f) ? md.getMass() : 0f;
            CollisionShape shape = (mass > 0f)
                    ? CollisionShapeFactory.createDynamicMeshShape(geo)
                    : CollisionShapeFactory.createMeshShape(geo);

            geo.addControl(new RigidBodyControl(shape, mass));
            root.attachChild(geo);
        }

        return root;
    }

    /**
     * Applies a texture transform (scale/offset) to the TexCoord buffer in the mesh.
     */
    private void applyUVTransform(Mesh mesh, MaterialData md, Vector3f size, AssetManager am) {
        if (md == null) {
            return;
        }
        Vector2f scale = (md.getTextureScale() != null)
                ? md.getTextureScale().clone()
                : new Vector2f(1f, 1f);
        Vector2f offset = (md.getTextureOffset() != null)
                ? md.getTextureOffset().clone()
                : new Vector2f(0f, 0f);

        // If there's a diffuse map, auto-scale relative to bounding box
        if (md.getDiffuseMap() != null) {
            int w = texW(am, md.getDiffuseMap());
            int h = texH(am, md.getDiffuseMap());
            // Avoid division by zero for minimal bounding boxes
            float sx = (size.x < 1e-6f) ? 1f : w / size.x;
            float sy = (size.y < 1e-6f) ? 1f : h / size.y;
            scale.multLocal(sx, sy);
        }

        // If there's no net transform, skip
        if (Vector2f.UNIT_XY.equals(scale) && offset.lengthSquared() < 1e-12f) {
            return;
        }

        VertexBuffer uvb = mesh.getBuffer(Type.TexCoord);
        if (uvb == null || uvb.getNumComponents() != 2) {
            return;
        }
        FloatBuffer buf = (FloatBuffer) uvb.getData();
        for (int i = 0; i < buf.limit(); i += 2) {
            float u = buf.get(i) * scale.x + offset.x;
            float v = buf.get(i + 1) * scale.y + offset.y;
            buf.put(i, u);
            buf.put(i + 1, v);
        }
        uvb.updateData(buf);
    }

    /**
     * Creates a jME material from the given MaterialData, using PBRLighting if possible.
     */
    private Material createMaterial(AssetManager am, MaterialData d) {
        if (d == null) {
            Material fallback = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
            fallback.setColor("Color", ColorRGBA.White);
            return fallback;
        }
        Material mat = new Material(am, "Common/MatDefs/Light/PBRLighting.j3md");

        // Base color
        ColorRGBA base = d.getDiffuse();
        if (base != null) {
            mat.setColor("BaseColor", base);
            if (base.a < 1f) {
                mat.setTransparent(true);
                mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            }
        }

        // Shininess -> approximate roughness
        float shininess = d.getShininess();
        float rough = 1f - (shininess / (shininess + 50f));
        mat.setFloat("Roughness", FastMath.clamp(rough, 0f, 1f));
        mat.setFloat("Metallic", 0f);

        // Diffuse map
        if (d.getDiffuseMap() != null) {
            Texture tex = cachedTex(am, d.getDiffuseMap(), d.isTextureRepeat());
            mat.setTexture("BaseColorMap", tex);
        }
        // Normal map
        if (d.getNormalMap() != null) {
            Texture tex = cachedTex(am, d.getNormalMap(), d.isTextureRepeat());
            mat.setTexture("NormalMap", tex);
        }
        return mat;
    }

    /**
     * Gets or loads a texture from the cache, applying wrap mode depending on repeat flag.
     */
    private Texture cachedTex(AssetManager am, String path, boolean repeat) {
        return TEX_CACHE.computeIfAbsent(path, p -> {
            Texture t = MyAsset.loadTexture(am, p, true);
            t.setWrap(repeat ? Texture.WrapMode.Repeat : Texture.WrapMode.Clamp);
            return t;
        });
    }

    /**
     * Fetch texture width, loading it if necessary.
     */
    private int texW(AssetManager am, String p) {
        return cachedTex(am, p, true).getImage().getWidth();
    }

    /**
     * Fetch texture height, loading it if necessary.
     */
    private int texH(AssetManager am, String p) {
        return cachedTex(am, p, true).getImage().getHeight();
    }

    /**
     * Extracts the directory from a file path or returns empty if none.
     */
    private String directoryOf(String path) {
        int idx = path.lastIndexOf('/');
        return (idx >= 0) ? path.substring(0, idx + 1) : "";
    }

    /**
     * Converts 1-based or negative indices to zero-based.
     */
    private int correctIndex(int rawIndex, int totalCount) {
        return (rawIndex > 0) ? (rawIndex - 1) : (totalCount + rawIndex);
    }

    /**
     * Creates a FloatBuffer from a list of Vector3f.
     */
    private FloatBuffer bufFrom(List<Vector3f> list, int components) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(list.size() * components);
        for (Vector3f v : list) {
            buffer.put(v.x).put(v.y).put(v.z);
        }
        buffer.flip();
        return buffer;
    }

    /**
     * Creates a FloatBuffer from a list of Vector2f for UV data.
     */
    private FloatBuffer bufFromUV(List<Vector2f> list) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(list.size() * 2);
        for (Vector2f v : list) {
            buffer.put(v.x).put(v.y);
        }
        buffer.flip();
        return buffer;
    }

    /**
     * Provides default normals pointing upward if no normals exist in the OBJ.
     */
    private FloatBuffer defaultNormals(int count) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(count * 3);
        for (int i = 0; i < count; i++) {
            buffer.put(0f).put(1f).put(0f);
        }
        buffer.flip();
        return buffer;
    }

    /**
     * Computes the bounding box from all vertex positions.
     */
    public BoundingBox computeBounds(List<Vector3f> verts) {
        if (verts == null || verts.isEmpty()) {
            return new BoundingBox();
        }

        Vector3f min = new Vector3f(verts.get(0));
        Vector3f max = new Vector3f(verts.get(0));

        for (int i = 1; i < verts.size(); i++) {
            Vector3f v = verts.get(i);
            min.minLocal(v);
            max.maxLocal(v);
        }

        BoundingBox bb = new BoundingBox();
        bb.setMinMax(min, max);
        return bb;
    }

    /**
     * Generates triplanar UVs by selecting the dominant axis for each vertex.
     */
    private FloatBuffer triplanarUV(BoundingBox bb, List<Vector3f> verts) {
        Vector3f min = bb.getMin(new Vector3f());
        Vector3f size = bb.getExtent(new Vector3f()).multLocal(2f);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(verts.size() * 2);
        for (Vector3f v : verts) {
            float ax = Math.abs((v.x - min.x) / size.x);
            float ay = Math.abs((v.y - min.y) / size.y);
            float az = Math.abs((v.z - min.z) / size.z);

            if (ax >= ay && ax >= az) {
                buffer.put((v.y - min.y) / size.y).put((v.z - min.z) / size.z);
            } else if (ay >= az) {
                buffer.put((v.x - min.x) / size.x).put((v.z - min.z) / size.z);
            } else {
                buffer.put((v.x - min.x) / size.x).put((v.y - min.y) / size.y);
            }
        }
        buffer.flip();
        return buffer;
    }

    /**
     * Applies planar UV mapping in XY, YZ, XZ, or AUTO (largest bounding plane).
     */
    private FloatBuffer planarUV(UVProjection proj, BoundingBox bb, List<Vector3f> verts) {
        Vector3f min = bb.getMin(new Vector3f());
        Vector3f size = bb.getExtent(new Vector3f()).multLocal(2f);

        // Pre-calc largest areas for AUTO
        float areaXY = size.x * size.y;
        float areaXZ = size.x * size.z;
        float areaYZ = size.y * size.z;

        FloatBuffer buffer = BufferUtils.createFloatBuffer(verts.size() * 2);
        for (Vector3f v : verts) {
            switch (proj) {
                case XY -> {
                    float u = (v.x - min.x) / size.x;
                    float vT = (v.y - min.y) / size.y;
                    buffer.put(u).put(vT);
                }
                case YZ -> {
                    float u = (v.y - min.y) / size.y;
                    float vT = (v.z - min.z) / size.z;
                    buffer.put(u).put(vT);
                }
                case XZ -> {
                    float u = (v.x - min.x) / size.x;
                    float vT = (v.z - min.z) / size.z;
                    buffer.put(u).put(vT);
                }
                default -> {
                    // AUTO: choose plane with largest area
                    if (areaXY >= areaXZ && areaXY >= areaYZ) {
                        float u = (v.x - min.x) / size.x;
                        float vT = (v.y - min.y) / size.y;
                        buffer.put(u).put(vT);
                    } else if (areaXZ >= areaYZ) {
                        float u = (v.x - min.x) / size.x;
                        float vT = (v.z - min.z) / size.z;
                        buffer.put(u).put(vT);
                    } else {
                        float u = (v.y - min.y) / size.y;
                        float vT = (v.z - min.z) / size.z;
                        buffer.put(u).put(vT);
                    }
                }
            }
        }
        buffer.flip();
        return buffer;
    }

    /**
     * Safely parse a float, returning 0 if invalid.
     */
    public float parseF(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            log.warn("Invalid float '{}'", s);
            return 0f;
        }
    }
}