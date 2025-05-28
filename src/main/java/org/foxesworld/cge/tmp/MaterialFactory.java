package org.foxesworld.cge.tmp;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;

public class MaterialFactory {

    private final AssetManager assetManager;

    public MaterialFactory(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public Material createLightingMaterial(Texture diffuseMap, Texture normalMap) {

        diffuseMap.setWrap(Texture.WrapMode.Repeat);

        // Создание материала с Lighting.j3md
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", new ColorRGBA(0.2f, 0.6f, 0.3f, 0.1f));
        mat.setTexture("DiffuseMap", diffuseMap);
        mat.setColor("Specular", ColorRGBA.White);
        mat.setFloat("Shininess", 2f);

        // Если задана карта нормалей — загрузить и применить
        if (normalMap != null) {
            normalMap.setWrap(Texture.WrapMode.Repeat);
            mat.setTexture("NormalMap", normalMap);
        }

        return mat;
    }
}
