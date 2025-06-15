package org.foxesworld.cge.importers.obj;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

public class MaterialData {

    private ColorRGBA ambient = ColorRGBA.Black.clone();
    private ColorRGBA diffuse = ColorRGBA.White.clone();
    private ColorRGBA specular = ColorRGBA.Black.clone();
    private float shininess = 0f;

    private String diffuseMap;
    private String normalMap;

    private Vector2f textureScale = new Vector2f(1, 1);    // uvscale
    private Vector2f textureOffset = new Vector2f(0, 0);   // uvoffset
    private boolean textureRepeat = true;                  // repeat

    private Vector3f scale;                                // size
    private float mass = 1f;                               // mass

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

    // === Diffuse Map ===
    public String getDiffuseMap() {
        return diffuseMap;
    }

    public void setDiffuseMap(String diffuseMap) {
        this.diffuseMap = diffuseMap;
    }

    // === Normal Map ===
    public String getNormalMap() {
        return normalMap;
    }

    public void setNormalMap(String normalMap) {
        this.normalMap = normalMap;
    }

    // === Texture Scale (uvscale) ===
    public Vector2f getTextureScale() {
        return textureScale;
    }

    public void setTextureScale(Vector2f textureScale) {
        this.textureScale = textureScale;
    }

    // === Texture Offset (uvoffset) ===
    public Vector2f getTextureOffset() {
        return textureOffset;
    }

    public void setTextureOffset(Vector2f textureOffset) {
        this.textureOffset = textureOffset;
    }

    // === Texture Repeat ===
    public boolean isTextureRepeat() {
        return textureRepeat;
    }

    public void setTextureRepeat(boolean textureRepeat) {
        this.textureRepeat = textureRepeat;
    }

    // === Object Scale (size) ===
    public Vector3f getScale() {
        return scale;
    }

    public void setScale(Vector3f scale) {
        this.scale = scale;
    }

    // === Physical Mass ===
    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        this.mass = mass;
    }

    // === Debug ===
    @Override
    public String toString() {
        return "MaterialData{" +
                "ambient=" + ambient +
                ", diffuse=" + diffuse +
                ", specular=" + specular +
                ", shininess=" + shininess +
                ", diffuseMap='" + diffuseMap + '\'' +
                ", normalMap='" + normalMap + '\'' +
                ", textureScale=" + textureScale +
                ", textureOffset=" + textureOffset +
                ", textureRepeat=" + textureRepeat +
                ", scale=" + scale +
                ", mass=" + mass +
                '}';
    }
}
