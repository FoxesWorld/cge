package org.foxesworld.cge.tmp.obj;

import com.jme3.asset.*;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
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
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public class OBJImporter implements AssetLoader {
    private static final Logger logger = LoggerFactory.getLogger(OBJImporter.class);

    @Override
    public Spatial load(AssetInfo assetInfo) {
        if (!(assetInfo.getKey() instanceof ModelKey mk))
            throw new IllegalArgumentException("AssetKey must be ModelKey.");

        String path = mk.getName();
        String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";

        List<Vector3f> verts   = new ArrayList<>();
        List<Vector3f> norms   = new ArrayList<>();
        List<Vector2f> uvs     = new ArrayList<>();
        List<Face>      faces   = new ArrayList<>();
        Map<String, MaterialData> mats = new HashMap<>();
        String currentMat = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(assetInfo.openStream()))) {
            String line; int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isEmpty() || line.charAt(0)=='#') continue;
                switch (line.charAt(0)) {
                    case 'v' -> parseVertexLine(line, verts, norms, uvs);
                    case 'f' -> faces.add(Face.parse(line));
                    case 'm' -> { // mtllib
                        String mtl = line.split("\\s+")[1];
                        loadMTL(assetInfo.getManager(), dir + "/" + mtl, mats);
                    }
                    case 'u' -> { // usemtl
                        currentMat = line.split("\\s+")[1];
                    }
                }
            }
        } catch (IOException ex) {
            logger.error("I/O error loading OBJ {}", path, ex);
            throw new RendererException("Error reading OBJ "+path);
        }

        if (verts.isEmpty() || faces.isEmpty()) {
            throw new RendererException("OBJ contains no vertices or faces: "+path);
        }

        Geometry geom = createGeometry(verts, norms, uvs, faces, mats.get(currentMat), assetInfo);
        Node root = new Node("OBJRoot");
        root.attachChild(geom);
        return root;
    }

    // Парсинг строк v, vn, vt
    private void parseVertexLine(String ln, List<Vector3f> vs, List<Vector3f> ns, List<Vector2f> ts) {
        // Пример: "v x y z", "vn x y z", "vt u v"
        String[] p = ln.split("\\s+");
        switch (p[0]) {
            case "v"  -> vs.add(new Vector3f(parseF(p[1]), parseF(p[2]), parseF(p[3])));
            case "vn" -> ns.add(new Vector3f(parseF(p[1]), parseF(p[2]), parseF(p[3])));
            case "vt" -> ts.add(new Vector2f(parseF(p[1]), parseF(p[2])));
        }
    }

    private float parseF(String s) {
        try { return Float.parseFloat(s); }
        catch (NumberFormatException ex) {
            logger.warn("Невалидное число: {}", s);
            return 0f;
        }
    }

    // Загрузка MTL
    private void loadMTL(AssetManager mgr, String mtlPath, Map<String, MaterialData> mats) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(mgr.locateAsset(new ModelKey(mtlPath)).openStream())
        )) {
            String line; MaterialData md = null; String name = null;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0)=='#') continue;
                String[] p = line.split("\\s+");
                switch (p[0]) {
                    case "newmtl" -> {
                        name = p[1]; md = new MaterialData();
                        mats.put(name, md);
                    }
                    case "Ka" -> md.setAmbient  (toColor(p));
                    case "Kd" -> md.setDiffuse  (toColor(p));
                    case "Ks" -> md.setSpecular (toColor(p));
                    case "Ns" -> md.setShininess(parseF(p[1]));
                    case "map_Kd" -> md.setDiffuseMap (p[1]);
                    case "map_Bump"-> md.setNormalMap  (p[1]);
                    case "size" -> {
                        float sx = Float.parseFloat(p[1]);
                        float sy = p.length > 2 ? Float.parseFloat(p[2]) : sx;
                        float sz = p.length > 3 ? Float.parseFloat(p[3]) : sx;
                        md.setScale(new Vector3f(sx, sy, sz));
                    }
                    case "uvscale" -> {
                        // форматы: "uvscale u v" или "uvscale u" (v=u)
                        float u = Float.parseFloat(p[1]);
                        float v = p.length > 2 ? Float.parseFloat(p[2]) : u;
                        md.setTextureScale(new Vector2f(u, v));
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Не удалось загрузить MTL: {}", mtlPath, e);
        }
    }

    private ColorRGBA toColor(String[] p) {
        return new ColorRGBA(parseF(p[1]), parseF(p[2]), parseF(p[3]), 1f);
    }

    // Построение Geometry
    private Geometry createGeometry(
            List<Vector3f> positions,
            List<Vector3f> normals,
            List<Vector2f> texCoords,
            List<Face> faces,
            MaterialData materialData,
            AssetInfo assetInfo
    ) {

        if (positions == null || faces == null || assetInfo == null) {
            throw new IllegalArgumentException("Positions, faces, and assetInfo must not be null");
        }

        logger.info("Creating mesh geometry...");

        Mesh mesh = new Mesh();

        // === Position Buffer ===
        mesh.setBuffer(VertexBuffer.Type.Position, 3,
                buildFloatBuffer(positions.size(), i -> {
                    Vector3f v = positions.get(i);
                    return new float[]{v.x, v.y, v.z};
                }, "position", assetInfo));

        // === Normal Buffer ===
        boolean hasNormals = normals != null && normals.size() == positions.size();
        mesh.setBuffer(VertexBuffer.Type.Normal, 3,
                buildFloatBuffer(positions.size(), i -> {
                    if (hasNormals) {
                        Vector3f n = normals.get(i);
                        return new float[]{n.x, n.y, n.z};
                    }
                    return new float[]{0f, 1f, 0f};
                }, "normal", assetInfo));

        // === TexCoord Buffer ===
        boolean hasTexCoords = texCoords != null && texCoords.size() == positions.size();
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2,
                buildFloatBuffer(positions.size(), i -> {
                    if (hasTexCoords) {
                        Vector2f uv = texCoords.get(i);
                        return new float[]{uv.x, uv.y};
                    }
                    return new float[]{0f, 0f};
                }, "texcoord", assetInfo));

        // === Index Buffer ===
        mesh.setBuffer(VertexBuffer.Type.Index, 3, buildIndexBuffer(faces, assetInfo));

        // === Tangents/Bitangents if available ===
        if (hasNormals && hasTexCoords) {
            logger.info("Computing tangent/binormal...");
            MeshUtils.computeTangentBinormal(mesh);
        }

        // === Geometry ===
        Geometry geometry = new Geometry("OBJGeometry", mesh);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        // === Material Assignment ===
        Material material = createMaterial(assetInfo.getManager(), materialData);
        if (material != null) {
            geometry.setMaterial(material);
            logger.info("Material assigned: " + material.getName());

            // === Auto UV Scaling ===
            if (materialData != null && materialData.getDiffuseMap() != null) {
                try {
                    Texture diffuse = assetInfo.getManager().loadTexture(materialData.getDiffuseMap());
                    if (diffuse != null) {
                        int texW = diffuse.getImage().getWidth();
                        int texH = diffuse.getImage().getHeight();

                        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY, 0, Float.POSITIVE_INFINITY);
                        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY, 0, Float.NEGATIVE_INFINITY);

                        for (Vector3f p : positions) {
                            min.x = Math.min(min.x, p.x);
                            min.z = Math.min(min.z, p.z);
                            max.x = Math.max(max.x, p.x);
                            max.z = Math.max(max.z, p.z);
                        }

                        float extentX = max.x - min.x;
                        float extentZ = max.z - min.z;

                        if (texW > 0 && texH > 0) {
                            Vector2f autoScale = new Vector2f(extentX / texW, extentZ / texH);
                            logger.info("Auto-scaling UVs: " + autoScale);
                            mesh.scaleTextureCoordinates(autoScale);
                        } else {
                            logger.warn("Texture dimensions are invalid.");
                        }
                    }
                } catch (AssetNotFoundException e) {
                    logger.warn("Diffuse texture not found: " + materialData.getDiffuseMap());
                } catch (Exception e) {
                    logger.error("Failed to load or apply diffuse texture", e);
                }
            }
        } else {
            logger.warn("No material assigned to geometry.");
        }

        // === Optional: Local scale ===
        if (materialData != null && materialData.getScale() != null) {
            geometry.setLocalScale(materialData.getScale());
            logger.info("Applied local scale: " + materialData.getScale());
        }

        logger.info("Geometry creation complete.");
        return geometry;
    }


    private <T> FloatBuffer buildFloatBuffer(
            int count,
            IntFunction<float[]> extractor,
            String bufferName,
            AssetInfo assetInfo
    ) {
        float[] sample = extractor.apply(0);
        FloatBuffer buf = BufferUtils.createFloatBuffer(count * sample.length);
        for (int i = 0; i < count; i++) {
            for (float f : extractor.apply(i)) {
                buf.put(f);
            }
        }
        buf.flip();
        if (buf.limit() == 0) {
            throw new RendererException("OBJImporter: empty " + bufferName +
                    " buffer for " + assetInfo.getKey());
        }
        return buf;
    }

    private IntBuffer buildIndexBuffer(List<Face> faces, AssetInfo assetInfo) {
        int totalTris = faces.stream().mapToInt(f -> Math.max(1, f.size() - 2)).sum();
        IntBuffer ib = BufferUtils.createIntBuffer(totalTris * 3);
        for (Face f : faces) {
            if (f.size() == 3) {
                f.getVertices().forEach(v -> ib.put(v.getVertexIndex() - 1));
            } else {
                for (Face tri : f.triangulate()) {
                    tri.getVertices().forEach(v -> ib.put(v.getVertexIndex() - 1));
                }
            }
        }
        ib.flip();
        if (ib.limit() == 0) {
            throw new RendererException("OBJImporter: empty index buffer for " +
                    assetInfo.getKey());
        }
        return ib;
    }



    // Универсальный метод для создания FloatBuffer из списка и лямбды
    private <T> FloatBuffer createFloatBuffer(
            List<T> list,
            IntFunction<float[]> extractor,
            String bufferName,
            AssetInfo assetInfo
    ) {
        int elementSize = extractor.apply(0).length;
        FloatBuffer buf = BufferUtils.createFloatBuffer(list.size() * elementSize);
        for (int i = 0; i < list.size(); i++) {
            float[] vals = extractor.apply(i);
            for (float v : vals) buf.put(v);
        }
        buf.flip();
        if (buf.limit() == 0) {
            throw new RendererException("OBJImporter: пустой буфер " +
                    bufferName + " для " + assetInfo.getKey());
        }
        return buf;
    }

    // Создаём IntBuffer с индексацией и триангуляцией
    private IntBuffer createIndexBuffer(List<Face> faces, AssetInfo assetInfo) {
        int triCount = faces.stream().mapToInt(f -> Math.max(1, f.size() - 2)).sum();
        IntBuffer ib = BufferUtils.createIntBuffer(triCount * 3);
        for (Face f : faces) {
            if (f.size() == 3) {
                f.getVertices().forEach(v -> ib.put(v.getVertexIndex() - 1));
            } else {
                for (Face tri : f.triangulate()) {
                    tri.getVertices().forEach(v -> ib.put(v.getVertexIndex() - 1));
                }
            }
        }
        ib.flip();
        if (ib.limit() == 0) {
            throw new RendererException("OBJImporter: пустой буфер индексов для " +
                    assetInfo.getKey());
        }
        return ib;
    }



    // Создание Material
    private Material createMaterial(AssetManager mgr, MaterialData md) {
        if (md == null) return null;
        Material m = new Material(mgr, "Common/MatDefs/Light/Lighting.j3md");
        if (md.getAmbient()!=null)   m.setColor("Ambient",  md.getAmbient());
        if (md.getDiffuse()!=null)   m.setColor("Diffuse",  md.getDiffuse());
        if (md.getSpecular()!=null)  m.setColor("Specular", md.getSpecular());
        if (md.getShininess()>0f)    m.setFloat("Shininess", md.getShininess());
        if (md.getDiffuseMap()!=null) m.setTexture("DiffuseMap", mgr.loadTexture(md.getDiffuseMap()));
        if (md.getNormalMap()!=null)  m.setTexture("NormalMap",  mgr.loadTexture(md.getNormalMap()));
        return m;
    }
}