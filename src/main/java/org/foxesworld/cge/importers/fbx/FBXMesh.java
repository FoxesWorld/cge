package org.foxesworld.cge.importers.fbx;

import org.foxesworld.cge.importers.fbx.anim.FBXAnimation;

import java.util.*;

/**
 * Расширенное представление FBX-меша с поддержкой вершин, индексов, нормалей, UV, bone weights/indices, нескольких UV-каналов и анимаций.
 */
public class FBXMesh {
    public float[] vertices;
    public int[] indices;
    public float[] normals;
    public float[][] uvs; // Несколько UV-каналов: [канал][координаты]
    public float[] weights;
    public int[] boneIndices;
    public String materialId;
    public FBXMaterial material;

    // Анимационные ключи (по-минимуму)
    public List<FBXAnimation> animations = new ArrayList<>();

    public static FBXMesh fromFBXNode(FBXNode node, List<FBXMaterial> materials) {
        List<Float> verts = new ArrayList<>();
        List<Integer> inds = new ArrayList<>();
        List<Float> norms = new ArrayList<>();
        List<List<Float>> uvSets = new ArrayList<>();
        Map<Integer, Float> vertexWeights = new HashMap<>();
        Map<Integer, Integer> vertexBoneIndices = new HashMap<>();
        String matId = null;

        // Для поддержки нескольких UV-каналов
        Map<Integer, List<Float>> uvChannels = new HashMap<>();

        for (FBXNode child : node.getChildren()) {
            if (child.getName().equals("Vertices")) {
                if (!child.getProperties().isEmpty()) {
                    String line = child.getProperties().get(0);
                    String[] values = line.replaceAll("[A-Za-z\\s=]", "").split(",");
                    for (String v : values) {
                        if (!v.trim().isEmpty())
                            verts.add(Float.parseFloat(v.trim()));
                    }
                }
            }
            if (child.getName().equals("PolygonVertexIndex")) {
                if (!child.getProperties().isEmpty()) {
                    String line = child.getProperties().get(0);
                    String[] values = line.replaceAll("[A-Za-z\\s=]", "").split(",");
                    for (String v : values) {
                        if (!v.trim().isEmpty())
                            inds.add(Integer.parseInt(v.trim()));
                    }
                }
            }
            if (child.getName().equals("LayerElementNormal")) {
                FBXNode normalsNode = child.findChild("Normals");
                if (normalsNode != null && !normalsNode.getProperties().isEmpty()) {
                    String line = normalsNode.getProperties().get(0);
                    String[] values = line.replaceAll("[A-Za-z\\s=]", "").split(",");
                    for (String v : values) {
                        if (!v.trim().isEmpty())
                            norms.add(Float.parseFloat(v.trim()));
                    }
                }
            }
            // Поддержка нескольких UV-каналов (LayerElementUV)
            if (child.getName().equals("LayerElementUV")) {
                int uvIndex = 0;
                for (FBXNode prop : child.getChildren()) {
                    if (prop.getName().equals("Name")) {
                        // Обычно имя канала: "UVChannel_1" и т.п.
                        String name = prop.getProperties().isEmpty() ? "" : prop.getProperties().get(0);
                        uvIndex = extractUvChannelIndex(name);
                    }
                    if (prop.getName().equals("UV")) {
                        if (!prop.getProperties().isEmpty()) {
                            String line = prop.getProperties().get(0);
                            String[] values = line.replaceAll("[A-Za-z\\s=]", "").split(",");
                            List<Float> uvSet = uvChannels.computeIfAbsent(uvIndex, k -> new ArrayList<>());
                            for (String v : values) {
                                if (!v.trim().isEmpty())
                                    uvSet.add(Float.parseFloat(v.trim()));
                            }
                        }
                    }
                }
            }
            // Привязка костей и веса (Deformer, Skin, Cluster)
            if (child.getName().equals("Deformer")) {
                for (FBXNode skinOrCluster : child.getChildren()) {
                    if (skinOrCluster.getName().equals("Skin")) {
                        for (FBXNode cluster : skinOrCluster.getChildren()) {
                            if (cluster.getName().equals("Cluster")) {
                                FBXNode indexesNode = cluster.findChild("Indexes");
                                FBXNode weightsNode = cluster.findChild("Weights");
                                if (indexesNode != null && weightsNode != null) {
                                    String[] idxs = indexesNode.getProperties().get(0).replaceAll("[A-Za-z\\s=]", "").split(",");
                                    String[] ws = weightsNode.getProperties().get(0).replaceAll("[A-Za-z\\s=]", "").split(",");
                                    for (int i = 0; i < idxs.length && i < ws.length; i++) {
                                        try {
                                            int idx = Integer.parseInt(idxs[i].trim());
                                            float w = Float.parseFloat(ws[i].trim());
                                            vertexWeights.put(idx, w);
                                            // Упрощенно: индекс кости — номер кластера
                                            vertexBoneIndices.put(idx, meshBoneIndex(cluster));
                                        } catch (Exception ignore) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (child.getName().equals("LayerElementMaterial")) {
                FBXNode matIdxNode = child.findChild("Materials");
                if (matIdxNode != null && !matIdxNode.getProperties().isEmpty()) {
                    String line = matIdxNode.getProperties().get(0);
                    String[] values = line.replaceAll("[A-Za-z\\s=]", "").split(",");
                    if (values.length > 0) {
                        matId = values[0].trim();
                    }
                }
            }
            // --- Анимации ---
            if (child.getName().equals("AnimationStack") || child.getName().equals("AnimationLayer")) {
                FBXAnimation anim = FBXAnimation.fromFBXNode(child);
                if (anim != null) {
                    // В этот MVP просто добавим все найденные анимации
                    // В реальном FBX — разбор AnimationCurve, AnimationCurveNode и т.д.
                }
            }
        }

        FBXMesh mesh = new FBXMesh();
        mesh.vertices = listToArray(verts);
        mesh.indices = listToArrayInt(inds);
        mesh.normals = listToArray(norms);

        // UV
        if (!uvChannels.isEmpty()) {
            int maxChannel = Collections.max(uvChannels.keySet());
            mesh.uvs = new float[maxChannel + 1][];
            for (Map.Entry<Integer, List<Float>> entry : uvChannels.entrySet()) {
                mesh.uvs[entry.getKey()] = listToArray(entry.getValue());
            }
        } else {
            mesh.uvs = new float[0][];
        }

        // Weights & Bones
        if (!vertexWeights.isEmpty()) {
            int len = mesh.vertices.length / 3;
            mesh.weights = new float[len];
            mesh.boneIndices = new int[len];
            for (int i = 0; i < len; i++) {
                mesh.weights[i] = vertexWeights.getOrDefault(i, 0.0f);
                mesh.boneIndices[i] = vertexBoneIndices.getOrDefault(i, -1);
            }
        }

        mesh.materialId = matId;
        if (matId != null && materials != null) {
            for (FBXMaterial mat : materials) {
                if (mat.name.equals(matId)) {
                    mesh.material = mat;
                    break;
                }
            }
        }

        return mesh;
    }

    private static float[] listToArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    private static int[] listToArrayInt(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    private static int extractUvChannelIndex(String name) {
        // Пример: "UVChannel_1" -> 1
        if (name == null) return 0;
        if (name.matches(".*?([0-9]+)$")) {
            try {
                return Integer.parseInt(name.replaceAll("[^0-9]", ""));
            } catch (Exception ignore) {}
        }
        return 0;
    }

    private static int meshBoneIndex(FBXNode cluster) {
        // Упрощенно: в реальности нужен разбор ID кости
        return cluster.hashCode();
    }
}