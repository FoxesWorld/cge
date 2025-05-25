package org.foxesworld.cge.core.cgs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Перечисление типов чанков в CGS-файле, с возможностью определения специфичных параметров.
 */
public enum ChunkType {
    GEOMETRY,
    PHYSICS,
    TERRAIN,
    LIGHTING,
    MATERIALS,
    CAMERAS,
    NAVMESH,
    CUSTOM;

    public static ChunkType fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Unknown chunk type ordinal: " + ordinal);
        }
        return values()[ordinal];
    }

    /**
     * Возвращает набор атрибутов для настройки по типу чанка.
     * Ключ — имя поля, значение — тип: "int", "float", "bool", "string", "enum:Option1|Option2"
     */
    public Map<String, String> getAttributes() {
        Map<String, String> map = new LinkedHashMap<>();
        switch (this) {
            case GEOMETRY -> {
                map.put("vertexCount", "int");
                map.put("hasNormals", "bool");
            }
            case PHYSICS -> {
                map.put("mass", "float");
                map.put("colliderType", "enum:Box|Sphere|Capsule|Mesh");
            }
            case TERRAIN -> {
                map.put("heightMap", "string");
                map.put("detailLevel", "int");
            }
            case LIGHTING -> {
                map.put("lightType", "enum:Directional|Point|Spot|Sky");
                map.put("intensity", "float");
                // RGBA color components (red, green, blue, alpha)
                map.put("color", "float4");
                map.put("castsShadow", "bool");
            }
            case MATERIALS -> {
                map.put("shader", "string");
                map.put("metallic", "float");
                map.put("roughness", "float");
            }
            case CAMERAS -> {
                map.put("fov", "float");
                map.put("isOrthographic", "bool");
            }
            case NAVMESH -> {
                map.put("regionSize", "float");
                map.put("agentHeight", "float");
            }
            default -> {
                // CUSTOM or unknown
            }
        }
        return map;
    }
}