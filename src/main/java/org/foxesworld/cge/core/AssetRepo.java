package org.foxesworld.cge.core;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Thread-safe repository for game assets (textures and models).
 * Uses generic helper methods to avoid duplication.
 */
public class AssetRepo {
    private static final Logger logger = LogManager.getLogger(AssetRepo.class);

    private final Map<String, Texture> textureMap = new ConcurrentHashMap<>();
    private final Map<String, Spatial> modelMap   = new ConcurrentHashMap<>();

    private final CalistaGameEngine calistaGameEngine;

    public AssetRepo(CalistaGameEngine calistaGameEngine) {
        this.calistaGameEngine = calistaGameEngine;
    }

    /**
     * Retrieves a texture by name. Returns a default Texture2D if not found.
     */
    public Texture getTexture(String name) {
        return getAsset(
                name,
                textureMap,
                Texture2D::new,
                "texture");
    }

    /**
     * Adds or replaces a texture in the repository.
     */
    public void addTexture(String name, Texture texture) {
        putAsset(
                name,
                texture,
                textureMap,
                "texture");
    }

    /**
     * Retrieves a model by name. Returns an empty Node if not found.
     */
    public Spatial getModel(String name) {
        return getAsset(
                name,
                modelMap,
                Node::new,
                "model");
    }

    /**
     * Adds or replaces a model in the repository.
     */
    public void addModel(String name, Spatial model) {
        putAsset(
                name,
                model,
                modelMap,
                "model");
    }

    /**
     * Unmodifiable view of all textures.
     */
    public Map<String, Texture> getTextureMap() {
        return Collections.unmodifiableMap(textureMap);
    }

    /**
     * Unmodifiable view of all models.
     */
    public Map<String, Spatial> getModelMap() {
        return Collections.unmodifiableMap(modelMap);
    }

    /**
     * Clears all loaded assets. Thread-safe.
     */
    public void clearAll() {
        textureMap.clear();
        modelMap.clear();
        logger.info("Cleared all assets from repository");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Generic helpers to eliminate duplication
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Generic retrieval: lookup in map, log warning if absent, return default.
     *
     * @param name            key to look up
     * @param map             concurrent map of assets
     * @param defaultSupplier supplier for default instance when missing
     * @param assetType       textual type for logging ("texture" or "model")
     * @param <T>             asset type
     * @return existing asset or default instance
     */
    private <T> T getAsset(String name,
                           Map<String, T> map,
                           Supplier<T> defaultSupplier,
                           String assetType) {
        T asset = map.get(name);
        if (asset == null) {
            logger.warn("{} '{}' not found!", capitalize(assetType), name);
            return defaultSupplier.get();
        }
        return asset;
    }

    /**
     * Generic insertion: put into map, log debug message.
     *
     * @param name      key under which to store
     * @param asset     the asset instance
     * @param map       concurrent map of assets
     * @param assetType textual type for logging ("texture" or "model")
     * @param <T>       asset type
     */
    private <T> void putAsset(String name, T asset, Map<String, T> map, String assetType) {
        boolean replaced = map.containsKey(name);
        map.put(name, asset);
        if (replaced) {
            logger.info("Replaced {} '{}' in repository", assetType, name);
        } else {
            logger.debug("Added {} '{}' to repository", assetType, name);
        }
    }
    /**
     * Capitalizes the first letter of a string.
     */
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
