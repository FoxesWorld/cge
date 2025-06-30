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
// Импортируем ваши существующие утилиты
import org.foxesworld.cge.importers.obj.utils.MTLloader;
import org.foxesworld.cge.importers.obj.utils.MeshUtils;
import org.foxesworld.cge.importers.obj.MaterialData;
import org.foxesworld.cge.importers.obj.Face; // <-- Важно: используем ваш класс Face
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
 * Улучшенный OBJ-импортер, совместимый с существующей структурой проекта.
 * Содержит улучшения в области надежности, обработки материалов и генерации UV.
 *
 * <p><b>Основные изменения:</b></p>
 * <ul>
 *   <li><b>Надежный парсинг:</b> Устойчивость к ошибкам в OBJ-файлах (пропущенные координаты, неверный формат чисел).</li>
 *   <li><b>Современные материалы:</b> Использование PBRLighting.j3md с поддержкой прозрачности.</li>
 *   <li><b>Обработка отсутствующих данных:</b> Генерация UV и нормалей-"заглушек", если они не указаны в файле.</li>
 *   <li><b>Совместимость:</b> Структура кода (триангуляция, создание буферов, работа с UV) приведена в соответствие с оригинальной версией для бесшовной интеграции.</li>
 * </ul>
 */
public final class OBJImporter implements AssetLoader {

    private static final Logger log = LoggerFactory.getLogger(OBJImporter.class);
    private static final Map<String, Texture> TEX_CACHE = new ConcurrentHashMap<>();

    public enum UVProjection {
        AUTO, XY, XZ, YZ, TRIPLANAR
    }

    private final UVProjection defaultProjection;
    private final boolean recalcTangentsAfterUV;
    private final boolean stitchUVSeams;

    public OBJImporter() {
        this(UVProjection.AUTO, true, true);
    }

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

        if (parsed.vertices.isEmpty() || parsed.facesByMat.values().stream().allMatch(List::isEmpty)) {
            log.warn("OBJ has no valid geometry: {}. Returning an empty Node.", mk.getName());
            return new Node("Empty-" + mk.getName());
        }
        return buildScene(parsed, info.getManager());
    }

    private record Parsed(
            List<Vector3f> vertices,
            List<Vector3f> normals,
            List<Vector2f> uvs,
            Map<String, List<Face>> facesByMat,
            Map<String, MaterialData> materials,
            BoundingBox bounds
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
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] tok = line.split("\\s+");
                switch (tok[0]) {
                    case "v" -> vPos.add(parseVec3(tok));
                    case "vn" -> vNrm.add(parseVec3(tok));
                    case "vt" -> {
                        if (tok.length < 3) {
                            log.warn("Not enough tokens to parse vt: {}. Skipping.", Arrays.toString(tok));
                            continue;
                        }
                        float u = parseF(tok[1]);
                        float v = 1f - parseF(tok[2]); // Flip V coordinate
                        vTex.add(new Vector2f(u, v));
                    }
                    case "f" -> {
                        // Используем ваш внешний парсер Face
                        try {
                            Face f = Face.parse(line);
                            faces.get(currentMat).add(f);
                        } catch (Exception e) {
                            log.warn("Failed to parse face: '{}'. Reason: {}", line, e.getMessage());
                        }
                    }
                    case "mtllib" -> {
                        if (tok.length > 1) {
                            mats.putAll(MTLloader.loadMTL(am, dir + tok[1]));
                        }
                    }
                    case "usemtl" -> {
                        if (tok.length > 1) {
                            currentMat = tok[1];
                            faces.computeIfAbsent(currentMat, k -> new ArrayList<>());
                        }
                    }
                    default -> log.trace("Ignoring token: {}", tok[0]);
                }
            }
        }

        BoundingBox bb = computeBounds(vPos);
        return new Parsed(vPos, vNrm, vTex, faces, mats, bb);
    }

    /**
     * Восстановлен публичный метод parseVec3 для совместимости.
     * Улучшен для обработки некорректного ввода.
     */
    public Vector3f parseVec3(String[] tokens) {
        if (tokens.length < 4) {
            log.warn("Not enough tokens to parse Vector3f: {}. Returning (0,0,0).", Arrays.toString(tokens));
            return new Vector3f(0, 0, 0);
        }
        float x = parseF(tokens[1]);
        float y = parseF(tokens[2]);
        float z = parseF(tokens[3]);
        return new Vector3f(x, y, z);
    }

    private Spatial buildScene(Parsed parsed, AssetManager am) {
        Node root = new Node("OBJ-Root");

        final int vCount = parsed.vertices.size();
        final boolean hasNormals = !parsed.normals.isEmpty();
        final boolean hasUVs = !parsed.uvs.isEmpty();

        FloatBuffer posBuf = bufFrom(parsed.vertices, 3);
        // Используем ваш метод defaultNormals, если в файле нет нормалей
        FloatBuffer nrmBuf = hasNormals ? bufFrom(parsed.normals, 3) : defaultNormals(vCount);

        BoundingBox bb = parsed.bounds;
        Vector3f size = bb.getExtent(new Vector3f()).multLocal(2f);

        for (Map.Entry<String, List<Face>> entry : parsed.facesByMat.entrySet()) {
            String matName = entry.getKey();
            List<Face> faceList = entry.getValue();
            if (faceList.isEmpty()) {
                continue;
            }

            // Восстановлена оригинальная логика триангуляции и построения индекса
            int triCount = faceList.stream().mapToInt(f -> Math.max(0, f.size() - 2)).sum();
            if (triCount == 0) continue;

            IntBuffer idxBuf = BufferUtils.createIntBuffer(triCount * 3);

            // Восстановлена оригинальная логика обработки UV
            List<Vector2f> geomUVs = hasUVs ? new ArrayList<>(parsed.uvs) : null;
            if (hasUVs && stitchUVSeams) {
                // Предполагается, что ваш MeshUtils.stitchUVSeams возвращает новый список UV,
                // который соответствует индексам вершин.
                geomUVs = MeshUtils.stitchUVSeams(faceList, parsed.vertices, parsed.uvs);
            }

            for (Face f : faceList) {
                // Восстановлена оригинальная элегантная триангуляция
                for (Face tri : (f.size() == 3 ? List.of(f) : f.triangulate())) {
                    for (var vert : tri.getVertices()) {
                        int idx = correctIndex(vert.vertexIndex(), vCount);
                        idxBuf.put(idx);
                    }
                }
            }
            idxBuf.flip();

            Mesh mesh = new Mesh();
            mesh.setBuffer(Type.Position, 3, posBuf);
            mesh.setBuffer(Type.Normal,   3, nrmBuf);

            // Логика UV-координат
            if (geomUVs != null) {
                // Предполагаем, что geomUVs теперь имеет правильный размер и порядок
                mesh.setBuffer(Type.TexCoord, 2, bufFromUV(geomUVs));
            } else if (defaultProjection != null) {
                // Улучшенная генерация UV, если их нет
                FloatBuffer generatedUvBuf = switch (defaultProjection) {
                    case TRIPLANAR -> triplanarUV(bb, parsed.vertices);
                    default -> planarUV(defaultProjection, bb, parsed.vertices);
                };
                mesh.setBuffer(Type.TexCoord, 2, generatedUvBuf);
            }

            mesh.setBuffer(Type.Index, 3, idxBuf);
            mesh.updateBound();

            if (recalcTangentsAfterUV && hasNormals && mesh.getBuffer(Type.TexCoord) != null) {
                MeshUtils.computeTangentBinormal(mesh);
            }

            Geometry geo = new Geometry(matName, mesh);
            geo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            geo.setUserData("size", size);

            MaterialData md = parsed.materials.get(matName);
            Material mat = createMaterial(am, md);
            geo.setMaterial(mat);

            applyUVTransform(mesh, md, size, am);

            if (md != null && md.getScale() != null) {
                geo.setLocalScale(md.getScale());
            }

            float mass = (md != null && md.getMass() > 0f) ? md.getMass() : 0f;
            CollisionShape shape = (mass > 0f)
                    ? CollisionShapeFactory.createDynamicMeshShape(geo)
                    : CollisionShapeFactory.createMeshShape(geo);

            geo.addControl(new RigidBodyControl(shape, mass));
            root.attachChild(geo);
        }
        return root;
    }

    private void applyUVTransform(Mesh mesh, MaterialData md, Vector3f size, AssetManager am) {
        if (md == null) return;

        Vector2f scale = (md.getTextureScale() != null) ? md.getTextureScale().clone() : new Vector2f(1f, 1f);
        Vector2f offset = (md.getTextureOffset() != null) ? md.getTextureOffset().clone() : new Vector2f(0f, 0f);

        if (md.getDiffuseMap() != null) {
            int w = texW(am, md.getDiffuseMap());
            int h = texH(am, md.getDiffuseMap());
            // Улучшено: защита от деления на ноль
            float sx = (size.x < 1e-6f) ? 1f : w / size.x;
            float sy = (size.y < 1e-6f) ? 1f : h / size.y;
            scale.multLocal(sx, sy);
        }

        if (scale.equals(Vector2f.UNIT_XY) && offset.lengthSquared() < 1e-12f) return;

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

    /**
     * Улучшенный метод создания материала с поддержкой PBR и прозрачности.
     */
    private Material createMaterial(AssetManager am, MaterialData d) {
        if (d == null) {
            Material fallback = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
            fallback.setColor("Color", ColorRGBA.White);
            return fallback;
        }
        Material mat = new Material(am, "Common/MatDefs/Light/PBRLighting.j3md");

        ColorRGBA base = d.getDiffuse();
        if (base != null) {
            mat.setColor("BaseColor", base);
            // Улучшено: поддержка прозрачности
            if (base.a < 1.0f) {
                mat.setTransparent(true);
                mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            }
        }

        float shininess = d.getShininess();
        float rough = 1f - (float) Math.sqrt(shininess / 1000f); // Эвристика для преобразования блеска в шероховатость
        mat.setFloat("Roughness", FastMath.clamp(rough, 0.01f, 1f));
        mat.setFloat("Metallic", 0f);

        if (d.getDiffuseMap() != null) {
            mat.setTexture("BaseColorMap", cachedTex(am, d.getDiffuseMap(), d.isTextureRepeat()));
        }
        if (d.getNormalMap() != null) {
            mat.setTexture("NormalMap", cachedTex(am, d.getNormalMap(), d.isTextureRepeat()));
        }
        return mat;
    }

    // --- Вспомогательные методы, приведенные в соответствие с оригиналом ---

    private Texture cachedTex(AssetManager am, String path, boolean repeat) {
        return TEX_CACHE.computeIfAbsent(path, p -> {
            Texture t = MyAsset.loadTexture(am, p, true);
            t.setWrap(repeat ? Texture.WrapMode.Repeat : Texture.WrapMode.Clamp);
            return t;
        });
    }

    private int texW(AssetManager am, String p) { return cachedTex(am, p, true).getImage().getWidth(); }
    private int texH(AssetManager am, String p) { return cachedTex(am, p, true).getImage().getHeight(); }
    private String directoryOf(String path) {
        int idx = path.lastIndexOf('/');
        return (idx >= 0) ? path.substring(0, idx + 1) : "";
    }
    private int correctIndex(int rawIndex, int totalCount) {
        return (rawIndex > 0) ? (rawIndex - 1) : (totalCount + rawIndex);
    }

    /** Восстановлен оригинальный метод bufFrom для Vector3f. */
    private FloatBuffer bufFrom(List<Vector3f> list, int components) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(list.size() * components);
        for (Vector3f v : list) {
            buffer.put(v.x).put(v.y).put(v.z);
        }
        buffer.flip();
        return buffer;
    }

    /** Восстановлен оригинальный метод bufFromUV для Vector2f. */
    private FloatBuffer bufFromUV(List<Vector2f> list) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(list.size() * 2);
        for (Vector2f v : list) {
            buffer.put(v.x).put(v.y);
        }
        buffer.flip();
        return buffer;
    }

    /** Восстановлен оригинальный метод defaultNormals. */
    private FloatBuffer defaultNormals(int count) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(count * 3);
        for (int i = 0; i < count; i++) {
            buffer.put(0f).put(1f).put(0f);
        }
        buffer.flip();
        return buffer;
    }

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
        return new BoundingBox(min, max);
    }

    private FloatBuffer triplanarUV(BoundingBox bb, List<Vector3f> verts) {
        // Трипланарная проекция сложна без нормалей. Вместо этого используем
        // улучшенную планарную проекцию по наибольшей оси, что является
        // хорошим и надежным приближением.
        return planarUV(UVProjection.AUTO, bb, verts);
    }

    private FloatBuffer planarUV(UVProjection proj, BoundingBox bb, List<Vector3f> verts) {
        Vector3f min = bb.getMin(new Vector3f());
        Vector3f size = bb.getExtent(new Vector3f()).multLocal(2f);

        // Улучшено: защита от деления на ноль для плоских моделей
        if (size.x < 1e-6f) size.x = 1f;
        if (size.y < 1e-6f) size.y = 1f;
        if (size.z < 1e-6f) size.z = 1f;

        UVProjection finalProj = proj;
        if (proj == UVProjection.AUTO) {
            float areaXY = size.x * size.y;
            float areaXZ = size.x * size.z;
            float areaYZ = size.y * size.z;
            if (areaXY >= areaXZ && areaXY >= areaYZ) finalProj = UVProjection.XY;
            else if (areaXZ >= areaYZ) finalProj = UVProjection.XZ;
            else finalProj = UVProjection.YZ;
        }

        FloatBuffer buffer = BufferUtils.createFloatBuffer(verts.size() * 2);
        for (Vector3f v : verts) {
            float u = 0, vTex = 0;
            switch (finalProj) {
                case XY -> { u = (v.x - min.x) / size.x; vTex = (v.y - min.y) / size.y; }
                case XZ -> { u = (v.x - min.x) / size.x; vTex = (v.z - min.z) / size.z; }
                case YZ -> { u = (v.y - min.y) / size.y; vTex = (v.z - min.z) / size.z; }
                default -> {} // не должно случиться
            }
            buffer.put(u).put(vTex);
        }
        buffer.flip();
        return buffer;
    }

    /** Восстановлен публичный метод parseF, улучшенный для обработки ошибок. */
    public float parseF(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            log.warn("Invalid float '{}', returning 0f.", s);
            return 0f;
        }
    }
}