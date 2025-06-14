package org.foxesworld.cge.tmp.obj;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

/**
 * DTO для хранения параметров материала, считанных из MTL-файла.
 * Расширяет классические параметры (ambient, diffuse, specular, shininess)
 * дополнительными свойствами для PBR: emissive, metalness и roughness,
 * а также управлением карт текстур и масштабирования.
 */
public class MaterialData {
    // === Transformations ===
    /** Локальный масштаб геометрии */
    private Vector3f scale = new Vector3f(1f, 1f, 1f);
    /** Масштаб UV-координат */
    private Vector2f textureScale = new Vector2f(1f, 1f);

    // === Standard material channels ===
    private ColorRGBA ambient;   // Ka
    private ColorRGBA diffuse;   // Kd
    private ColorRGBA specular;  // Ks
    private float shininess;     // Ns

    // === PBR extensions ===
    /** Цвет эмиссии (самосвет) */
    private ColorRGBA emissive;
    /** Уровень металличности [0..1] */
    private float metalness = 0f;
    /** Шероховатость [0..1] */
    private float roughness = 1f;

    // === Texture maps ===
    private String diffuseMap;
    private String normalMap;
    private String emissiveMap;
    private float mass;

    // === Transparency ===
    private boolean transparent = false;

    /**
     * Конструктор по умолчанию с инициализацией к нейтральным значениям.
     */
    public MaterialData() {
        this.ambient = null;
        this.diffuse = null;
        this.specular = null;
        this.shininess = 0f;
        this.emissive = null;
        this.metalness = 0f;
        this.roughness = 1f;
        this.diffuseMap = null;
        this.normalMap = null;
        this.emissiveMap = null;
        this.transparent = false;
    }

    // === Getters & Setters ===

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

    public ColorRGBA getAmbient() {
        return ambient;
    }

    public void setAmbient(ColorRGBA ambient) {
        this.ambient = ambient;
    }

    public ColorRGBA getDiffuse() {
        return diffuse;
    }

    public void setDiffuse(ColorRGBA diffuse) {
        this.diffuse = diffuse;
    }

    public ColorRGBA getSpecular() {
        return specular;
    }

    public void setSpecular(ColorRGBA specular) {
        this.specular = specular;
    }

    public float getShininess() {
        return shininess;
    }

    public void setShininess(float shininess) {
        this.shininess = shininess;
    }

    public ColorRGBA getEmissive() {
        return emissive;
    }

    public void setEmissive(ColorRGBA emissive) {
        this.emissive = emissive;
    }

    public float getMetalness() {
        return metalness;
    }

    public void setMetalness(float metalness) {
        this.metalness = metalness;
    }

    public float getRoughness() {
        return roughness;
    }

    public void setRoughness(float roughness) {
        this.roughness = roughness;
    }

    public String getDiffuseMap() {
        return diffuseMap;
    }

    public void setDiffuseMap(String diffuseMap) {
        this.diffuseMap = diffuseMap;
    }

    public String getNormalMap() {
        return normalMap;
    }

    public void setNormalMap(String normalMap) {
        this.normalMap = normalMap;
    }

    public String getEmissiveMap() {
        return emissiveMap;
    }

    public void setEmissiveMap(String emissiveMap) {
        this.emissiveMap = emissiveMap;
    }

    public boolean isTransparent() {
        return transparent;
    }

    public void setTransparent(boolean transparent) {
        this.transparent = transparent;
    }

    public void setMass(float mass) {
        this.mass = mass;
    }

    public float getMass() {
        return mass;
    }

    @Override
    public String toString() {
        return "MaterialData{" +
                "scale=" + scale +
                ", textureScale=" + textureScale +
                ", ambient=" + ambient +
                ", diffuse=" + diffuse +
                ", specular=" + specular +
                ", shininess=" + shininess +
                ", emissive=" + emissive +
                ", metalness=" + metalness +
                ", roughness=" + roughness +
                ", diffuseMap='" + diffuseMap + '\'' +
                ", normalMap='" + normalMap + '\'' +
                ", emissiveMap='" + emissiveMap + '\'' +
                ", transparent=" + transparent +
                '}';
    }
}
