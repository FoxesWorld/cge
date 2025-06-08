package org.foxesworld.cge.tmp.obj;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

/**
 * DTO для хранения параметров материала из MTL-файла.
 */
public class MaterialData {
    private Vector3f scale = new Vector3f(1f, 1f, 1f);
    private Vector2f textureScale = new Vector2f(1f, 1f);
    private ColorRGBA ambient;
    private ColorRGBA diffuse;
    private ColorRGBA specular;
    private float shininess;
    private String diffuseMap;
    private String normalMap;

    public MaterialData() {
        // Значения по умолчанию
        this.ambient = null;
        this.diffuse = null;
        this.specular = null;
        this.shininess = 0f;
        this.diffuseMap = null;
        this.normalMap = null;
    }

    // === Ambient ===
    public ColorRGBA getAmbient() {
        return ambient;
    }

    public void setAmbient(ColorRGBA ambient) {
        this.ambient = ambient;
    }

    // === Diffuse ===
    public ColorRGBA getDiffuse() {
        return diffuse;
    }

    public void setDiffuse(ColorRGBA diffuse) {
        this.diffuse = diffuse;
    }

    // === Specular ===
    public ColorRGBA getSpecular() {
        return specular;
    }

    public void setSpecular(ColorRGBA specular) {
        this.specular = specular;
    }

    // === Shininess ===
    public float getShininess() {
        return shininess;
    }

    public void setShininess(float shininess) {
        this.shininess = shininess;
    }

    // === Diffuse Map (карта диффузии) ===
    public String getDiffuseMap() {
        return diffuseMap;
    }

    public void setDiffuseMap(String diffuseMap) {
        this.diffuseMap = diffuseMap;
    }

    // === Normal Map (нормал карта) ===
    public String getNormalMap() {
        return normalMap;
    }

    public void setNormalMap(String normalMap) {
        this.normalMap = normalMap;
    }


    public Vector3f getScale() {
        return scale;
    }
    public void setScale(Vector3f scale) {
        this.scale = scale;
    }

    public Vector2f getTextureScale() {
        return textureScale;
    }

    public void setTextureScale(Vector2f textureScale) {
        this.textureScale = textureScale;
    }
    @Override
    public String toString() {
        return "MaterialData{" +
                "ambient=" + ambient +
                ", diffuse=" + diffuse +
                ", specular=" + specular +
                ", shininess=" + shininess +
                ", diffuseMap='" + diffuseMap + '\'' +
                ", normalMap='" + normalMap + '\'' +
                '}';
    }
}
