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
import org.foxesworld.cge.importers.obj.utils.MTLloader;
import org.foxesworld.cge.importers.obj.utils.MeshUtils;
import org.slf4j.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static jme3utilities.MyAsset.loadTexture;

/**
 * Universal, reference-quality thread-safe OBJ importer.
 * Supports multi-material, robust indices, automatic UV projections,
 * texture scale/offset, planar/axis-aligned projections, and optional
 * tangent recalculation after UV transform.
 */
public final class OBJImporter implements AssetLoader {
    private static final Logger log = LoggerFactory.getLogger(OBJImporter.class);
    private static final Map<String, Texture> TEX_CACHE = new ConcurrentHashMap<>();

    public enum UVProjection { AUTO, XY, XZ, YZ }

    private final UVProjection defaultProjection;
    private final boolean recalcTangentsAfterUV;

    public OBJImporter() {
        this(UVProjection.AUTO, true);
    }

    public OBJImporter(UVProjection uvProj, boolean recalcTangentsAfterUV) {
        this.defaultProjection = uvProj;
        this.recalcTangentsAfterUV = recalcTangentsAfterUV;
    }

    @Override
    public Spatial load(AssetInfo info) throws IOException {
        if (!(info.getKey() instanceof ModelKey mk)) {
            throw new IllegalArgumentException("ModelKey expected");
        }
        String dir = directoryOf(mk.getName());
        Parsed parsed = parseOBJ(info, dir);

        if (parsed.vertices.isEmpty() || parsed.facesByMat.values().stream().allMatch(List::isEmpty)) {
            throw new RendererException("OBJ has no geometry: " + mk.getName());
        }
        return buildScene(parsed, info.getManager());
    }

    private record Parsed(
            List<Vector3f> vertices,
            List<Vector3f> normals,
            List<Vector2f> uvs,
            Map<String, List<Face>> facesByMat,
            Map<String, MaterialData> materials
    ) {}

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
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tok = line.split("\\s+");
                switch (tok[0]) {
                    case "v"  -> vPos.add(parseVec3(tok));
                    case "vn" -> vNrm.add(parseVec3(tok));
                    case "vt" -> vTex.add(new Vector2f(parseF(tok[1]), parseF(tok[2])));
                    case "f"  -> faces.get(currentMat).add(Face.parse(line));
                    case "mtllib" -> mats.putAll(MTLloader.loadMTL(am, dir + tok[1]));
                    case "usemtl" -> {
                        currentMat = tok[1];
                        faces.computeIfAbsent(currentMat, k -> new ArrayList<>());
                    }
                    default -> log.debug("Ignore token {}", tok[0]);
                }
            }
        }
        return new Parsed(vPos, vNrm, vTex, faces, mats);
    }

    private Spatial buildScene(Parsed p, AssetManager am) {
        Node root = new Node("OBJ");
        int vCount = p.vertices.size();
        boolean hasNormals = p.normals.size() == vCount;
        boolean hasUVs = p.uvs.size() == vCount;

        FloatBuffer posBuf = bufFrom(p.vertices, 3);
        FloatBuffer nrmBuf = hasNormals ? bufFrom(p.normals, 3) : defaultNormals(vCount);
        FloatBuffer uvBuf = hasUVs ? bufFromUV(p.uvs) : null;

        BoundingBox bb = computeBounds(p.vertices);
        Vector3f size = bb.getExtent(new Vector3f()).multLocal(2f);

        for (Map.Entry<String, List<Face>> entry : p.facesByMat.entrySet()) {
            String matName = entry.getKey();
            List<Face> faceList = entry.getValue();
            if (faceList.isEmpty()) continue;

            // Build mesh
            int triCount = faceList.stream().mapToInt(f -> Math.max(1, f.size() - 2)).sum();
            IntBuffer idxBuf = BufferUtils.createIntBuffer(triCount * 3);
            for (Face f : faceList) {
                List<Face> tris = f.size() == 3 ? List.of(f) : f.triangulate();
                for (Face t : tris) {
                    for (var vert : t.getVertices()) {
                        idxBuf.put(correctIndex(vert.vertexIndex(), vCount));
                    }
                }
            }
            idxBuf.flip();

            Mesh mesh = new Mesh();
            mesh.setBuffer(Type.Position, 3, posBuf.duplicate());
            mesh.setBuffer(Type.Normal,   3, nrmBuf.duplicate());
            mesh.setBuffer(Type.Index,    3, idxBuf);
            if (uvBuf != null) {
                mesh.setBuffer(Type.TexCoord, 2, uvBuf.duplicate());
            } else {
                mesh.setBuffer(Type.TexCoord, 2, planarUV(defaultProjection, bb, p.vertices));
            }
            mesh.updateBound();

            if (hasNormals && recalcTangentsAfterUV) {
                MeshUtils.computeTangentBinormal(mesh);
            }

            Geometry geo = new Geometry(matName, mesh);
            geo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            geo.setUserData("size", size);

            MaterialData md = p.materials.get(matName);
            geo.setMaterial(createMaterial(am, md));
            applyUVTransform(mesh, md, size, am);

            if (md != null && md.getScale() != null) {
                geo.setLocalScale(md.getScale());
            }

            float mass = (md != null && md.getMass() > 0f) ? md.getMass() : 0f;
            CollisionShape shape = mass > 0f
                    ? CollisionShapeFactory.createDynamicMeshShape(geo)
                    : CollisionShapeFactory.createMeshShape(geo);
            geo.addControl(new RigidBodyControl(shape, mass));

            root.attachChild(geo);
        }
        return root;
    }

    private void applyUVTransform(Mesh mesh, MaterialData md, Vector3f size, AssetManager am) {
        if (md == null) return;
        Vector2f scale = md.getTextureScale() != null
                ? md.getTextureScale().clone() : new Vector2f(1f, 1f);
        Vector2f offset = md.getTextureOffset() != null
                ? md.getTextureOffset().clone() : new Vector2f(0f, 0f);

        if (md.getDiffuseMap() != null) {
            int w = texW(am, md.getDiffuseMap());
            int h = texH(am, md.getDiffuseMap());
            Vector2f auto = new Vector2f(w / size.x, h / size.y);
            scale.multLocal(auto);
        }
        if (scale.equals(Vector2f.UNIT_XY) && offset.equals(Vector2f.ZERO)) {
            return;
        }

        VertexBuffer uvb = mesh.getBuffer(Type.TexCoord);
        if (uvb == null || uvb.getNumComponents() != 2) return;
        FloatBuffer buf = (FloatBuffer) uvb.getData();
        for (int i = 0; i < buf.limit(); i += 2) {
            float u = buf.get(i) * scale.x + offset.x;
            float v = buf.get(i + 1) * scale.y + offset.y;
            buf.put(i, u);
            buf.put(i + 1, v);
        }
        uvb.updateData(buf);
    }

    private Vector3f parseVec3(String[] tok) {
        return new Vector3f(parseF(tok[1]), parseF(tok[2]), parseF(tok[3]));
    }

    private float parseF(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            log.warn("Bad float '{}'", s);
            return 0f;
        }
    }

    private int correctIndex(int raw, int count) {
        return raw > 0 ? raw - 1 : count + raw;
    }

    private FloatBuffer bufFrom(List<Vector3f> list, int comps) {
        FloatBuffer b = BufferUtils.createFloatBuffer(list.size() * comps);
        list.forEach(v -> b.put(v.x).put(v.y).put(v.z));
        b.flip();
        return b;
    }

    private FloatBuffer bufFromUV(List<Vector2f> list) {
        FloatBuffer b = BufferUtils.createFloatBuffer(list.size() * 2);
        list.forEach(v -> b.put(v.x).put(v.y));
        b.flip();
        return b;
    }

    private FloatBuffer defaultNormals(int count) {
        FloatBuffer b = BufferUtils.createFloatBuffer(count * 3);
        for (int i = 0; i < count; i++) {
            b.put(0f).put(1f).put(0f);
        }
        b.flip();
        return b;
    }

    private BoundingBox computeBounds(List<Vector3f> verts) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        for (Vector3f v : verts) {
            min.minLocal(v);
            max.maxLocal(v);
        }
        BoundingBox bb = new BoundingBox();
        bb.setMinMax(min, max);
        return bb;
    }

    private FloatBuffer planarUV(UVProjection proj, BoundingBox bb, List<Vector3f> verts) {
        Vector3f min = bb.getMin(new Vector3f());
        Vector3f size = bb.getExtent(new Vector3f()).multLocal(2f);
        FloatBuffer buf = BufferUtils.createFloatBuffer(verts.size() * 2);
        for (Vector3f v : verts) {
            switch (proj) {
                case XY -> buf.put((v.x - min.x) / size.x).put((v.y - min.y) / size.y);
                case YZ -> buf.put((v.y - min.y) / size.y).put((v.z - min.z) / size.z);
                case XZ -> buf.put((v.x - min.x) / size.x).put((v.z - min.z) / size.z);
                default -> { // AUTO: choose largest area projection
                    Vector3f ext = size;
                    if (ext.x * ext.y >= ext.x * ext.z && ext.x * ext.y >= ext.y * ext.z) {
                        buf.put((v.x - min.x) / size.x).put((v.y - min.y) / size.y);
                    } else if (ext.x * ext.z >= ext.y * ext.z) {
                        buf.put((v.x - min.x) / size.x).put((v.z - min.z) / size.z);
                    } else {
                        buf.put((v.y - min.y) / size.y).put((v.z - min.z) / size.z);
                    }
                }
            }
        }
        buf.flip();
        return buf;
    }

    private Material createMaterial(AssetManager am, MaterialData d) {
        if (d == null) {
            Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setColor("Color", ColorRGBA.White);
            return m;
        }
        Material m = new Material(am, "Common/MatDefs/Light/PBRLighting.j3md");
        Optional.ofNullable(d.getDiffuse()).ifPresent(c -> m.setColor("BaseColor", c));

        float rough = 1f - (d.getShininess() / (d.getShininess() + 50f));
        m.setFloat("Roughness", FastMath.clamp(rough, 0, 1));
        m.setFloat("Metallic", 0f);

        if (d.getDiffuseMap() != null) {
            Texture tex = cachedTex(am, d.getDiffuseMap(), d.isTextureRepeat());
            m.setTexture("BaseColorMap", tex);
        }
        if (d.getNormalMap() != null) {
            Texture tex = cachedTex(am, d.getNormalMap(), d.isTextureRepeat());
            m.setTexture("NormalMap", tex);
        }

        if (d.getDiffuse() != null && d.getDiffuse().a < 1f) {
            m.setTransparent(true);
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        }
        return m;
    }

    private Texture cachedTex(AssetManager am, String path, boolean repeat) {
        return TEX_CACHE.computeIfAbsent(path, p -> {
            Texture t = loadTexture(am, p, true);
            t.setWrap(repeat ? Texture.WrapMode.Repeat : Texture.WrapMode.Clamp);
            return t;
        });
    }

    private int texW(AssetManager am, String p) {
        return cachedTex(am, p, true).getImage().getWidth();
    }
    private int texH(AssetManager am, String p) {
        return cachedTex(am, p, true).getImage().getHeight();
    }

    private String directoryOf(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(0, i + 1) : "";
    }
}