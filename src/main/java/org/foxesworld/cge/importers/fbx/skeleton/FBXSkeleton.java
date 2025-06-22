package org.foxesworld.cge.importers.fbx.skeleton;


import org.foxesworld.cge.importers.fbx.FBXNode;

import java.util.*;

/**
 * Представление скелета FBX: список костей с поддержкой связей parent-child и трансформаций.
 */
public class FBXSkeleton {
    public final List<FBXBone> bones = new ArrayList<>();
    public final Map<String, FBXBone> boneByName = new HashMap<>();

    /**
     * Парсит скелет из дерева FBXNode (обычно из Objects/Model с типом "LimbNode").
     */
    public static FBXSkeleton fromFBXNode(FBXNode objectsNode) {
        FBXSkeleton skeleton = new FBXSkeleton();

        // 1. Собрать все кости (Model: "LimbNode")
        for (FBXNode model : objectsNode.getChildren()) {
            if (model.getName().startsWith("Model")) {
                String type = getModelType(model);
                if ("LimbNode".equals(type) || "Root".equals(type)) {
                    FBXBone bone = FBXBone.fromFBXNode(model);
                    skeleton.bones.add(bone);
                    skeleton.boneByName.put(bone.name, bone);
                }
            }
        }

        // 2. Установить parent-child связи (по Connections)
        FBXNode connectionsNode = objectsNode.getParent().findChild("Connections");
        if (connectionsNode != null) {
            for (FBXNode con : connectionsNode.getChildren()) {
                if (con.getName().equals("C")) {
                    // Пример: C: "OO", 12345, 67890 (OO - Object-Object)
                    List<String> props = con.getProperties();
                    if (props.size() >= 3 && props.get(0).contains("\"OO\"")) {
                        String childId = props.get(1).replaceAll("[^0-9]", "");
                        String parentId = props.get(2).replaceAll("[^0-9]", "");
                        FBXBone child = skeleton.findBoneByFbxId(childId);
                        FBXBone parent = skeleton.findBoneByFbxId(parentId);
                        if (child != null && parent != null) {
                            child.parent = parent;
                            parent.children.add(child);
                        }
                    }
                }
            }
        }

        // 3. (Опционально) — построить индексы parentIndex для быстрого доступа
        for (FBXBone bone : skeleton.bones) {
            if (bone.parent != null) {
                bone.parentIndex = skeleton.bones.indexOf(bone.parent);
            }
        }

        return skeleton;
    }

    public FBXBone findBoneByName(String name) {
        return boneByName.get(name);
    }

    private static String getModelType(FBXNode model) {
        String header = model.getProperties().isEmpty() ? model.getName() : model.getProperties().get(0);
        String[] split = header.split(",");
        if (split.length > 2) {
            return split[2].replaceAll("\"", "").trim();
        }
        return "";
    }

    public FBXBone findBoneByFbxId(String id) {
        for (FBXBone bone : bones) {
            if (bone.fbxId.equals(id)) return bone;
        }
        return null;
    }
}