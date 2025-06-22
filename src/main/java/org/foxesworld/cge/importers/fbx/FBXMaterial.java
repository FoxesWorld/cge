package org.foxesworld.cge.importers.fbx;

import java.util.*;

public class FBXMaterial {
    public String name;
    public String shadingModel;
    public String diffuseTexture;

    public static FBXMaterial fromFBXNode(FBXNode node) {
        FBXMaterial mat = new FBXMaterial();
        mat.name = node.getProperties().isEmpty() ? node.getName() : node.getProperties().get(0);
        for (FBXNode child : node.getChildren()) {
            if (child.getName().equals("ShadingModel")) {
                if (!child.getProperties().isEmpty()) {
                    mat.shadingModel = child.getProperties().get(0).replaceAll("\"", "");
                }
            }
            if (child.getName().equals("Properties70")) {
                for (FBXNode prop : child.getChildren()) {
                    if (prop.getName().equals("P")) {
                        // Пример: P: "DiffuseColor", "Color", "", "A",0.8,0.8,0.8,1
                        if (!prop.getProperties().isEmpty() && prop.getProperties().get(0).contains("DiffuseColor")) {
                            // Можно разобрать диффузный цвет, если нужно
                        }
                        if (!prop.getProperties().isEmpty() && prop.getProperties().get(0).contains("Texture")) {
                            // Пример: найти путь к текстуре
                            String propLine = prop.getProperties().get(0);
                            String[] arr = propLine.split(",");
                            for (String s : arr) {
                                if (s.contains(".png") || s.contains(".jpg")) {
                                    mat.diffuseTexture = s.replaceAll("\"", "").trim();
                                }
                            }
                        }
                    }
                }
            }
        }
        return mat;
    }
}