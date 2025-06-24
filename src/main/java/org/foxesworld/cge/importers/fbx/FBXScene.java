package org.foxesworld.cge.importers.fbx;

import java.util.*;

public class FBXScene {
    private final List<FBXMesh> meshes = new ArrayList<>();
    private final List<FBXMaterial> materials = new ArrayList<>();

    public List<FBXMesh> getMeshes() { return meshes; }
    public List<FBXMaterial> getMaterials() { return materials; }

    public static FBXScene fromFBXNode(FBXNode root) {
        FBXScene scene = new FBXScene();

        // --- Найти материалы ---
        FBXNode objects = root.findChild("Objects");
        if (objects != null) {
            for (FBXNode obj : objects.getChildren()) {
                if (obj.getName().startsWith("Material")) {
                    FBXMaterial mat = FBXMaterial.fromFBXNode(obj);
                    if (mat != null) scene.materials.add(mat);
                }
            }
            // --- Найти геометрию ---
            for (FBXNode obj : objects.getChildren()) {
                if (obj.getName().startsWith("Geometry")) {
                    FBXMesh mesh = FBXMesh.fromFBXNode(obj, scene.materials);
                    if (mesh != null) scene.meshes.add(mesh);
                }
            }
        }
        return scene;
    }
}