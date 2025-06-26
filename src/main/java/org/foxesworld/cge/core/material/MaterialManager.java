package org.foxesworld.cge.core.material;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.shader.VarType;
import com.jme3.texture.Texture;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.AssetRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class MaterialManager {

    private final AssetManager assetManager;
    private final AssetRepo assetRepo;
    private static final Logger logger = LoggerFactory.getLogger(MaterialManager.class);
    private final Map<String, Material> materialCache = new HashMap<>();
    private final Map<String, BiConsumer<Material, ParamContext>> typeSetters = new HashMap<>();

    public MaterialManager(CalistaGameEngine calistaGameEngine) {
        this.assetManager = calistaGameEngine.getAssetManager();
        this.assetRepo = calistaGameEngine.getAssetRepo();
        initTypeSetters();
    }

    private void initTypeSetters() {
        typeSetters.put("texture", (mat, ctx) -> {
            logger.info("Loading texture '{}' for '{}'", ctx.value, ctx.key);
            String[] parts = ctx.value.split("\\|");
            String texName = parts[0].trim();
            Texture.WrapMode wrapMode = Texture.WrapMode.Repeat;
            if (parts.length > 1) {
                try {
                    wrapMode = Texture.WrapMode.valueOf(parts[1].trim());
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown WrapMode '{}', using Repeat", parts[1].trim());
                }
            }
            Texture tex = assetRepo.getTexture(texName);
            if (tex != null) {
                tex.setWrap(wrapMode);
                mat.setTexture(ctx.key, tex);
                logger.debug("Texture '{}' set for '{}' with WrapMode {}", texName, ctx.key, wrapMode);
            } else {
                logger.warn("Texture '{}' not found in AssetRepo for '{}'", texName, ctx.key);
            }
        });

        typeSetters.put("color", (mat, ctx) -> setColor(mat, ctx, "color"));
        typeSetters.put("float", (mat, ctx) -> setPrimitive(mat, ctx, "float"));
        typeSetters.put("int", (mat, ctx) -> setPrimitive(mat, ctx, "int"));
        typeSetters.put("boolean", (mat, ctx) -> setPrimitive(mat, ctx, "boolean"));
        typeSetters.put("vector2", (mat, ctx) -> setVector(mat, ctx, 2));
        typeSetters.put("vector3", (mat, ctx) -> setVector(mat, ctx, 3));
        typeSetters.put("vector4", (mat, ctx) -> setVector(mat, ctx, 4));
        typeSetters.put("string", (mat, ctx) -> {
            mat.setParam(ctx.key, VarType.UniformBufferObject, ctx.value);
            logger.debug("Parameter '{}' (string) = {}", ctx.key, ctx.value);
        });
    }

    public Material getMaterial(String name) {
        logger.debug("Requested material: {}", name);
        return materialCache.computeIfAbsent(name, n -> {
            Material mat = loadMaterial(n);
            if (mat != null) {
                logger.debug("Material {} loaded and cached", n);
            } else {
                logger.warn("Material {} not found or could not be loaded", n);
            }
            return mat;
        });
    }

    public void putMaterial(String name, Material material) {
        logger.debug("Explicitly adding material {} to cache", name);
        materialCache.put(name, material);
    }

    public void clearCache() {
        logger.info("Clearing material cache");
        materialCache.clear();
    }

    private Material loadMaterial(String name) {
        try {
            if (name.endsWith(".j3m")) {
                logger.info("Loading .j3m material: {}", name);
                return deserializeJ3M(name);
            }
            logger.warn("Unknown material format: {}", name);
            return null;
        } catch (Exception e) {
            logger.error("Error loading material {}: {}", name, e.getMessage(), e);
            return null;
        }
    }

    private Material deserializeJ3M(String j3mPath) throws Exception {
        logger.info("Deserializing .j3m: {}", j3mPath);
        try (InputStream is = assetManager.locateAsset(new com.jme3.asset.AssetKey<>(j3mPath)).openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line, j3md = null;
            Map<String, ParamValue> params = new HashMap<>();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("Material ") && line.contains(":")) {
                    int colon = line.indexOf(':');
                    int brace = line.indexOf('{', colon);
                    j3md = line.substring(colon + 1, brace > colon ? brace : line.length()).trim();
                    logger.debug("Found j3md template: {}", j3md);
                }
                if (line.contains(":") && !line.startsWith("Material ")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String rawKey = parts[0].trim();
                        String value = parts[1].trim().replace("\"", "");
                        String key = rawKey;
                        String type = null;
                        if (rawKey.contains("(") && rawKey.contains(")")) {
                            int start = rawKey.indexOf('(');
                            int end = rawKey.indexOf(')');
                            type = rawKey.substring(start + 1, end).trim().toLowerCase();
                            key = rawKey.substring(0, start).trim();
                        }
                        logger.debug("Parameter: {} (type: {}) = {}", key, type, value);
                        params.put(key, new ParamValue(type, value));
                    }
                }
            }

            if (j3md == null) throw new IllegalArgumentException("J3MD not found in: " + j3mPath);
            Material mat = new Material(assetManager, j3md);

            for (Map.Entry<String, ParamValue> entry : params.entrySet()) {
                String key = entry.getKey();
                ParamValue param = entry.getValue();
                try {
                    getTypeSetter(param.type, key, param.value)
                            .accept(mat, new ParamContext(key, param.value));
                } catch (Exception ex) {
                    logger.error("Error setting parameter '{}', value '{}': {}", key, param.value, ex.getMessage(), ex);
                }
            }
            logger.info("Material {} successfully built from {}", mat.getMaterialDef().getName(), j3mPath);
            return mat;
        }
    }

    private BiConsumer<Material, ParamContext> getTypeSetter(String type, String key, String value) {
        if (type != null && typeSetters.containsKey(type)) return typeSetters.get(type);
        if (isTextureKey(key)) return typeSetters.get("texture");
        if (isColorValue(value)) return typeSetters.get("color");
        if (isBoolean(value)) return typeSetters.get("boolean");
        if (isInt(value)) return typeSetters.get("int");
        if (isFloat(value)) return typeSetters.get("float");
        if (isVector(value, 2)) return typeSetters.get("vector2");
        if (isVector(value, 3)) return typeSetters.get("vector3");
        if (isVector(value, 4)) return typeSetters.get("vector4");
        return typeSetters.get("string");
    }

    // --- Helper methods ---

    private static void setColor(Material mat, ParamContext ctx, String logType) {
        float[] c = parseFloats(ctx.value);
        ColorRGBA color = c.length == 3 ? new ColorRGBA(c[0], c[1], c[2], 1f)
                : c.length == 4 ? new ColorRGBA(c[0], c[1], c[2], c[3]) : null;
        if (color != null) {
            mat.setColor(ctx.key, color);
            logger.debug("Color {} set for '{}'", color, ctx.key);
        } else {
            logger.warn("Cannot parse color for '{}': '{}'", ctx.key, ctx.value);
        }
    }

    private static void setPrimitive(Material mat, ParamContext ctx, String type) {
        switch (type) {
            case "float":
                mat.setFloat(ctx.key, Float.parseFloat(ctx.value));
                break;
            case "int":
                mat.setInt(ctx.key, Integer.parseInt(ctx.value));
                break;
            case "boolean":
                mat.setBoolean(ctx.key, Boolean.parseBoolean(ctx.value));
                break;
        }
        logger.debug("Parameter '{}' ({}) = {}", ctx.key, type, ctx.value);
    }

    private static void setVector(Material mat, ParamContext ctx, int dim) {
        float[] v = parseFloats(ctx.value);
        switch (dim) {
            case 2:
                mat.setVector2(ctx.key, new com.jme3.math.Vector2f(v[0], v[1]));
                break;
            case 3:
                mat.setVector3(ctx.key, new com.jme3.math.Vector3f(v[0], v[1], v[2]));
                break;
            case 4:
                mat.setVector4(ctx.key, new com.jme3.math.Vector4f(v[0], v[1], v[2], v[3]));
                break;
        }
        logger.debug("Parameter '{}' (vector{}) = {}", ctx.key, dim, ctx.value);
    }

    private static boolean isTextureKey(String key) {
        String k = key.toLowerCase();
        return k.contains("map") || k.contains("texture");
    }

    private static boolean isColorValue(String value) {
        String[] s = value.split(" ");
        return s.length == 3 || s.length == 4;
    }

    private static boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    private static boolean isInt(String value) {
        try { Integer.parseInt(value); return true; } catch (Exception e) { return false; }
    }

    private static boolean isFloat(String value) {
        try { Float.parseFloat(value); return value.contains("."); } catch (Exception e) { return false; }
    }

    private static boolean isVector(String value, int dim) {
        String[] s = value.split(" ");
        if (s.length != dim) return false;
        for (String part : s) {
            try { Float.parseFloat(part); } catch (Exception e) { return false; }
        }
        return true;
    }

    private static float[] parseFloats(String value) {
        String[] s = value.split(" ");
        float[] f = new float[s.length];
        for (int i = 0; i < s.length; i++) f[i] = Float.parseFloat(s[i]);
        return f;
    }
}