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

/**
 * Универсальный MaterialManager для Calista Game Engine:
 * - Десериализует .j3m вручную через парсинг, ищет все текстуры через assetRepo.getTexture("имя").
 * - Для простоты дессериализации все поля должны быть помечены типом в скобках:
 *   Например: DiffuseMap (Texture) : grass_diffuse
 *             Diffuse (Color) : 0.4 0.8 0.3 1.0
 *             Shininess (Float) : 8.0
 *             UseMaterialColors (Boolean) : true
 * - Исключён хардкодинг: используется Map<String, BiConsumer<Material, ParamContext>> для типов параметров.
 */
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
            logger.info("Загрузка текстуры '{}' для '{}'", ctx.value, ctx.key);
            String texName = ctx.value;
            Texture.WrapMode wrapMode = Texture.WrapMode.Repeat; // Значение по умолчанию

            // Если в параметре указан wrapMode через |, например: grass_diffuse|Repeat
            if (texName.contains("|")) {
                String[] parts = texName.split("\\|");
                texName = parts[0].trim();
                try {
                    wrapMode = Texture.WrapMode.valueOf(parts[1].trim());
                } catch (IllegalArgumentException e) {
                    logger.warn("Неизвестный WrapMode '{}', используется Repeat", parts[1].trim());
                }
            }

            Texture tex = assetRepo.getTexture(texName);
            if (tex != null) {
                tex.setWrap(wrapMode);
                mat.setTexture(ctx.key, tex);
                logger.debug("Текстура '{}' успешно применена к '{}' с WrapMode {}", texName, ctx.key, wrapMode);
            } else {
                logger.warn("Текстура '{}' не найдена в AssetRepo для '{}'", texName, ctx.key);
            }
        });
        typeSetters.put("color", (mat, ctx) -> {
            float[] c = parseFloats(ctx.value);
            ColorRGBA color = c.length == 3 ? new ColorRGBA(c[0], c[1], c[2], 1f)
                    : c.length == 4 ? new ColorRGBA(c[0], c[1], c[2], c[3]) : null;
            if (color != null) {
                mat.setColor(ctx.key, color);
                logger.debug("Цвет {} успешно применён к '{}'", color, ctx.key);
            } else {
                logger.warn("Невозможно распарсить цвет для '{}': '{}'", ctx.key, ctx.value);
            }
        });
        typeSetters.put("float", (mat, ctx) -> {
            mat.setFloat(ctx.key, Float.parseFloat(ctx.value));
            logger.debug("Параметр '{}' (float) = {}", ctx.key, ctx.value);
        });
        typeSetters.put("int", (mat, ctx) -> {
            mat.setInt(ctx.key, Integer.parseInt(ctx.value));
            logger.debug("Параметр '{}' (int) = {}", ctx.key, ctx.value);
        });
        typeSetters.put("boolean", (mat, ctx) -> {
            mat.setBoolean(ctx.key, Boolean.parseBoolean(ctx.value));
            logger.debug("Параметр '{}' (boolean) = {}", ctx.key, ctx.value);
        });
        typeSetters.put("vector2", (mat, ctx) -> {
            float[] v = parseFloats(ctx.value);
            mat.setVector2(ctx.key, new com.jme3.math.Vector2f(v[0], v[1]));
            logger.debug("Параметр '{}' (vector2) = {}", ctx.key, ctx.value);
        });
        typeSetters.put("vector3", (mat, ctx) -> {
            float[] v = parseFloats(ctx.value);
            mat.setVector3(ctx.key, new com.jme3.math.Vector3f(v[0], v[1], v[2]));
            logger.debug("Параметр '{}' (vector3) = {}", ctx.key, ctx.value);
        });
        typeSetters.put("vector4", (mat, ctx) -> {
            float[] v = parseFloats(ctx.value);
            mat.setVector4(ctx.key, new com.jme3.math.Vector4f(v[0], v[1], v[2], v[3]));
            logger.debug("Параметр '{}' (vector4) = {}", ctx.key, ctx.value);
        });
        // Fallback for unknown types
        typeSetters.put("string", (mat, ctx) -> {
            mat.setParam(ctx.key, VarType.ShaderStorageBufferObject, ctx.value);
            logger.debug("Параметр '{}' (string) = {}", ctx.key, ctx.value);
        });
    }

    public Material getMaterial(String name) {
        logger.debug("Запрошен материал: {}", name);
        if (materialCache.containsKey(name)) {
            logger.debug("Материал {} найден в кэше", name);
            return materialCache.get(name);
        }
        Material mat = loadMaterial(name);
        if (mat != null) {
            materialCache.put(name, mat);
            logger.debug("Материал {} загружен и добавлен в кэш", name);
        } else {
            logger.warn("Материал {} не найден или не удалось загрузить", name);
        }
        return mat;
    }

    public void putMaterial(String name, Material material) {
        logger.debug("Принудительно добавляем материал {} в кэш", name);
        materialCache.put(name, material);
    }

    public void clearCache() {
        logger.info("Очищаем кэш материалов");
        materialCache.clear();
    }

    private Material loadMaterial(String name) {
        try {
            if (name.endsWith(".j3m")) {
                logger.info("Загрузка .j3m материала: {}", name);
                return deserializeJ3M(name);
            }
            logger.warn("Неизвестный формат материала: {}", name);
            return null;
        } catch (Exception e) {
            logger.error("Ошибка при загрузке материала {}: {}", name, e.getMessage(), e);
            return null;
        }
    }

    private Material deserializeJ3M(String j3mPath) throws Exception {
        logger.info("Десериализация .j3m: {}", j3mPath);
        InputStream is = assetManager.locateAsset(new com.jme3.asset.AssetKey<>(j3mPath)).openStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String line;
        String j3md = null;
        Map<String, ParamValue> params = new HashMap<>();

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("Material ") && line.contains(":")) {
                int colon = line.indexOf(':');
                int brace = line.indexOf('{', colon);
                j3md = line.substring(colon + 1, brace > colon ? brace : line.length()).trim();
                logger.debug("Найден шаблон j3md: {}", j3md);
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
                    logger.debug("Параметр: {} (тип: {}) = {}", key, type, value);
                    params.put(key, new ParamValue(type, value));
                }
            }
        }
        reader.close();

        if (j3md == null) throw new IllegalArgumentException("J3MD not found in: " + j3mPath);
        Material mat = new Material(assetManager, j3md);

        for (Map.Entry<String, ParamValue> entry : params.entrySet()) {
            String key = entry.getKey();
            ParamValue param = entry.getValue();
            String type = param.type;
            String value = param.value;
            try {
                BiConsumer<Material, ParamContext> setter = getTypeSetter(type, key, value);
                setter.accept(mat, new ParamContext(key, value));
            } catch (Exception ex) {
                logger.error("Ошибка при установке параметра '{}', значение '{}': {}", key, value, ex.getMessage(), ex);
            }
        }
        logger.info("Материал {} успешно собран из {}", mat.getMaterialDef().getName(), j3mPath);
        return mat;
    }

    // Выбор подходящего сеттера: по типу или автоопределение, если не указан
    private BiConsumer<Material, ParamContext> getTypeSetter(String type, String key, String value) {
        if (type != null && typeSetters.containsKey(type)) {
            return typeSetters.get(type);
        }
        // Автоопределение типа
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

    // --- Вспомогательные методы ---

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