package org.foxesworld.cge.tmp.obj;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.asset.ModelKey;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RendererException;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.foxesworld.cge.tmp.obj.utils.MeshUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.function.IntFunction;

import static jme3utilities.MyAsset.loadTexture;

/**
 * High-stability OBJ importer with improved error handling,
 * resource management, and unified buffer builders.
 * <p>Inspired by RAGE's robust asset loading patterns.</p>
 */
public class OBJImporter implements AssetLoader {
    private static final Logger logger = LoggerFactory.getLogger(OBJImporter.class);

    @Override
    public Spatial load(AssetInfo assetInfo) throws IOException {
        if (!(assetInfo.getKey() instanceof ModelKey mk)) {
            throw new IllegalArgumentException("Expected ModelKey for OBJ import, got "
                    + assetInfo.getKey().getClass().getSimpleName());
        }
        String path = mk.getName();
        String dir = extractDirectory(path);

        ParsedOBJ data = parseOBJ(assetInfo, dir);
        if (data.vertices.isEmpty() || data.faces.isEmpty()) {
            throw new RendererException("OBJ contains no usable geometry: " + path);
        }

        Geometry geom = buildGeometry(data, assetInfo);
        Node root = new Node("OBJRoot");
        root.attachChild(geom);
        return root;
    }

    private String extractDirectory(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(0, idx + 1) : "";
    }

    private ParsedOBJ parseOBJ(AssetInfo assetInfo, String dir) throws IOException {
        List<Vector3f> verts = new ArrayList<>();
        List<Vector3f> norms = new ArrayList<>();
        List<Vector2f> uvs   = new ArrayList<>();
        List<Face> faces     = new ArrayList<>();
        Map<String, MaterialData> mats = new HashMap<>();
        String currentMat = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(assetInfo.openStream()))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tokens = line.split("\s+");
                switch (tokens[0]) {
                    case "v"  -> parseVertex(tokens, verts);
                    case "vn" -> parseNormal(tokens, norms);
                    case "vt" -> parseUV(tokens, uvs);
                    case "f"  -> faces.add(Face.parse(line));
                    case "mtllib" -> loadMTL(assetInfo.getManager(), dir + tokens[1], mats);
                    case "usemtl" -> currentMat = tokens[1];
                    default -> logger.debug("Ignored OBJ directive: {}", tokens[0]);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to read OBJ stream", e);
            throw e;
        }
        MaterialData matData = currentMat != null ? mats.get(currentMat) : null;
        return new ParsedOBJ(verts, norms, uvs, faces, matData);
    }

    private void parseVertex(String[] t, List<Vector3f> vs) {
        vs.add(new Vector3f(parseF(t[1]), parseF(t[2]), parseF(t[3])));
    }
    private void parseNormal(String[] t, List<Vector3f> ns) {
        ns.add(new Vector3f(parseF(t[1]), parseF(t[2]), parseF(t[3])));
    }
    private void parseUV(String[] t, List<Vector2f> ts) {
        ts.add(new Vector2f(parseF(t[1]), parseF(t[2])));
    }
    private float parseF(String s) {
        try { return Float.parseFloat(s);
        } catch (NumberFormatException ex) {
            logger.warn("Invalid float '{}', defaulting to 0", s);
            return 0f;
        }
    }

    private void loadMTL(AssetManager mgr, String mtlPath, Map<String, MaterialData> mats) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(mgr.locateAsset(new ModelKey(mtlPath)).openStream()))) {
            MaterialData current = null;
            String name = null;
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\s+");
                switch (p[0]) {
                    case "newmtl" -> {
                        name = p[1]; current = new MaterialData(); mats.put(name, current);
                    }
                    case "Ka" -> current.setAmbient(toColor(p));
                    case "Kd" -> current.setDiffuse(toColor(p));
                    case "Ks" -> current.setSpecular(toColor(p));
                    case "Ns" -> current.setShininess(parseF(p[1]));
                    case "map_Kd" -> current.setDiffuseMap(p[1]);
                    case "map_Bump"-> current.setNormalMap(p[1]);
                    case "uvscale" -> applyUVScale(p, current);
                    case "size" -> applyScale(p, current);
                    default -> logger.trace("Ignored MTL token: {}", p[0]);
                }
            }
        } catch (Exception e) {
            logger.error("Error loading MTL {}", mtlPath, e);
        }
    }
    private void applyUVScale(String[] p, MaterialData md) {
        float u = parseF(p[1]); float v = p.length>2 ? parseF(p[2]) : u;
        md.setTextureScale(new Vector2f(u, v));
    }
    private void applyScale(String[] p, MaterialData md) {
        float sx = parseF(p[1]); float sy = p.length>2 ? parseF(p[2]) : sx;
        float sz = p.length>3 ? parseF(p[3]) : sx;
        md.setScale(new Vector3f(sx, sy, sz));
    }
    private ColorRGBA toColor(String[] p) {
        return new ColorRGBA(parseF(p[1]), parseF(p[2]), parseF(p[3]), 1f);
    }

    private Geometry buildGeometry(ParsedOBJ data, AssetInfo assetInfo) {
        Mesh mesh = new Mesh();
        int vCount = data.vertices.size();
        boolean hasNorm = data.normals.size() == vCount;
        boolean hasUV   = data.uvs.size()     == vCount;

        // unified buffer builder
        mesh.setBuffer(VertexBuffer.Type.Position, 3,
                buildBuffer(vCount, i-> toArray(data.vertices.get(i)), "position", assetInfo));
        mesh.setBuffer(VertexBuffer.Type.Normal,   3,
                buildBuffer(vCount, i-> hasNorm? toArray(data.normals.get(i)): new float[]{0,1,0}, "normal", assetInfo));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2,
                buildBuffer(vCount, i-> hasUV? toArray(data.uvs.get(i)): new float[]{0,0}, "texcoord", assetInfo));
        mesh.setBuffer(VertexBuffer.Type.Index,   3,
                buildIndex(data.faces, assetInfo));

        if (hasNorm && hasUV) MeshUtils.computeTangentBinormal(mesh);

        Geometry geom = new Geometry("OBJGeom", mesh);
        geom.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        if (data.material!=null) {
            Material mat = createMaterial(assetInfo.getManager(), data.material);
            geom.setMaterial(mat);
            applyAutoUV(mesh, data.vertices, data.material, assetInfo.getManager());
            if (data.material.getScale()!=null) geom.setLocalScale(data.material.getScale());
        }
        return geom;
    }
    private float[] toArray(Vector3f v) { return new float[]{v.x,v.y,v.z}; }
    private float[] toArray(Vector2f v) { return new float[]{v.x,v.y}; }

    private FloatBuffer buildBuffer(int count, IntFunction<float[]> extractor,
                                    String name, AssetInfo info) {
        float[] sample = extractor.apply(0);
        FloatBuffer buf = BufferUtils.createFloatBuffer(count * sample.length);
        for (int i=0;i<count;i++) for (float f: extractor.apply(i)) buf.put(f);
        buf.flip();
        if (!buf.hasRemaining()) throw new RendererException("Empty " + name + " for " + info.getKey());
        return buf;
    }
    private IntBuffer buildIndex(List<Face> faces, AssetInfo info) {
        int tris = faces.stream().mapToInt(f->Math.max(1, f.size()-2)).sum();
        IntBuffer ib = BufferUtils.createIntBuffer(tris*3);
        faces.forEach(f->{
            if (f.size()==3) f.getVertices().forEach(v-> ib.put(v.getVertexIndex()-1));
            else f.triangulate().forEach(t-> t.getVertices().forEach(v-> ib.put(v.getVertexIndex()-1)));
        });
        ib.flip(); if (!ib.hasRemaining()) throw new RendererException("Empty index for " + info.getKey());
        return ib;
    }

    private void applyAutoUV(Mesh mesh, List<Vector3f> verts,
                             MaterialData md, AssetManager mgr) {
        if (md.getDiffuseMap()==null) return;
        try {
            Texture tex = mgr.loadTexture(md.getDiffuseMap());
            int w=tex.getImage().getWidth(), h=tex.getImage().getHeight();
            if (w>0&&h>0) {
                Vector3f min=new Vector3f(Float.MAX_VALUE,0,Float.MAX_VALUE),
                        max=new Vector3f(Float.MIN_VALUE,0,Float.MIN_VALUE);
                verts.forEach(v-> { min.x=Math.min(min.x,v.x); min.z=Math.min(min.z,v.z);
                    max.x=Math.max(max.x,v.x); max.z=Math.max(max.z,v.z); });
                mesh.scaleTextureCoordinates(
                        new Vector2f((max.x-min.x)/w,(max.z-min.z)/h));
            }
        } catch (Exception e) { logger.warn("Auto UV scaling failed", e); }
    }

    private Material createMaterial(AssetManager mgr, MaterialData md) {
        if (md == null) {
            // Fallback to basic unshaded
            Material fallback = new Material(mgr, "Common/MatDefs/Misc/Unshaded.j3md");
            fallback.setColor("Color", ColorRGBA.White);
            return fallback;
        }
        // Use PBR material for realistic shading
        Material m = new Material(mgr, "Common/MatDefs/Light/PBRLighting.j3md");

        // Base color (albedo)
        Optional.ofNullable(md.getDiffuse()).ifPresent(c -> m.setColor("BaseColor", c));
        // Metalness and roughness from specular & shininess
        // Convert shininess (Ns) to roughness [0..1]
        float roughness = 1f;
        if (md.getShininess() > 0f) {
            float shin = md.getShininess();
            // simple mapping: high shininess -> low roughness
            roughness = FastMath.clamp(1f - (shin / (shin + 50f)), 0f, 1f);
        }
        m.setFloat("Roughness", roughness);
        // Default metalness to zero (dielectric)
        m.setFloat("Metallic", 0f);

        // Textures: albedo, normal, roughness/metalness if available
        if (md.getDiffuseMap() != null) {
            Texture albedo = loadTexture(mgr, md.getDiffuseMap(), true);
            m.setTexture("BaseColorMap", albedo);
        }
        if (md.getNormalMap() != null) {
            Texture nrm = loadTexture(mgr, md.getNormalMap(), true);
            m.setTexture("NormalMap", nrm);
            m.setFloat("NormalScale", 1f);
        }
        // Emissive map or color
        if (md.getEmissiveMap() != null) {
            Texture emi = loadTexture(mgr, md.getEmissiveMap(), true);
            m.setTexture("EmissiveMap", emi);
            m.setColor("Emissive", ColorRGBA.White);
        } else if (md.getEmissive() != null) {
            m.setColor("Emissive", md.getEmissive());
        }

        // Transparency
        if (md.isTransparent() || (md.getDiffuse() != null && md.getDiffuse().a < 1f)) {
            m.setTransparent(true);
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        }

        return m;
    }


    /**
     * Container for parsed OBJ data
     */
    private record ParsedOBJ(
            List<Vector3f> vertices,
            List<Vector3f> normals,
            List<Vector2f> uvs,
            List<Face> faces,
            MaterialData material
    ) {}
}
