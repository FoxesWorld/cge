package org.foxesworld.cge.core.file.extensions.cgmat;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.texture.Texture;

import java.io.File;
import java.io.IOException;

public class CGMATMaterialBuilder {
    private final AssetManager assetManager;

    public CGMATMaterialBuilder(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    /**
     * Загружает бинарный материал из файла и настраивает JME Material.
     * @param path путь к .cgmat файлу
     * @return настроенный Material
     * @throws IOException при ошибках чтения
     */
    public Material buildMaterial(File path) throws IOException {
        CGMATFile cgmat = new CGMATFile(path, "r");
        cgmat.readFileNew();
        CGMATMetadata meta = cgmat.getMetadata();

        // Создаем базовый PBR-материал
        Material mat = new Material(
                assetManager,
                "Common/MatDefs/Light/PBRLighting.j3md"
        );

        // Базовые свойства
        meta.getProperties().forEach((key, val) -> {
            switch (key) {
                case "Glossiness" -> mat.setFloat("Glossiness", ((Number) val).floatValue());
                case "Metallic"   -> mat.setFloat("Metallic", ((Number) val).floatValue());
                case "UseSpecGloss" -> mat.setBoolean("UseSpecGloss", (Boolean) val);
                case "UseSpecularAA"-> mat.setBoolean("UseSpecularAA", (Boolean) val);
                default -> {} // игнорируем неизвестные
            }
        });

        // Текстуры
        for (String texPath : meta.getTexturePaths()) {
            String paramName = detectParamName(texPath);
            Texture tex = assetManager.loadTexture(texPath);
            mat.setTexture(paramName, tex);
            tex.setWrap(Texture.WrapMode.Repeat);
        }

        return mat;
    }

    /**
     * Простейшая логика определения параметра по имени файла.
     * Можно заменить на маппинг в конфигурации.
     */
    private String detectParamName(String texturePath) {
        if (texturePath.toLowerCase().contains("diffuse")) return "BaseColorMap";
        if (texturePath.toLowerCase().contains("normal"))  return "NormalMap";
        if (texturePath.toLowerCase().contains("roughness")) return "RoughnessMap";
        if (texturePath.toLowerCase().contains("metallic")) return "MetallicMap";
        // fallback
        return "BaseColorMap";
    }
}