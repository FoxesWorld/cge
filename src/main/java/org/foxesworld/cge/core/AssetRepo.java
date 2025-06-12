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

/**
 * Thread-safe repository for game assets (textures and models).
 * Supports concurrent access and modification.
 */
public class AssetRepo {
    private static final Logger logger = LogManager.getLogger(AssetRepo.class);

    private final Map<String, Texture> textureMap = new ConcurrentHashMap<>();
    private final Map<String, Spatial> modelsMap = new ConcurrentHashMap<>();
    private final CalistaGameEngine calistaGameEngine;

    public AssetRepo(CalistaGameEngine calistaGameEngine) {
        this.calistaGameEngine = calistaGameEngine;
    }

    /**
     * Retrieves a texture by name. Returns a default texture if not found.
     */
    public Texture getTexture(String name) {
        Texture tex = textureMap.get(name);
        if (tex == null) {
            logger.warn("Texture '{}' not found!", name);
            return new Texture2D();
        }
        return tex;
    }

    /**
     * Adds or replaces a texture in the repository.
     * If an entry with the same name exists, it will be overwritten.
     */
    public void addTexture(String name, Texture texture) {
        textureMap.put(name, texture);
        logger.debug("Added texture '{}' to repository", name);
    }

    /**
     * Retrieves a model by name. Returns a new empty Node if not found.
     */
    public Spatial getModel(String name) {
        Spatial model = modelsMap.get(name);
        if (model == null) {
            logger.warn("Model '{}' not found!", name);
            return new Node();
        }
        return model;
    }

    /**
     * Adds or replaces a model in the repository.
     */
    public void addModel(String name, Spatial model) {
        modelsMap.put(name, model);
        logger.debug("Added model '{}' to repository", name);
    }

    /**
     * Provides an unmodifiable view of the texture map for safe iteration.
     */
    public Map<String, Texture> getTextureMap() {
        return Collections.unmodifiableMap(textureMap);
    }

    /**
     * Provides an unmodifiable view of the models map for safe iteration.
     */
    public Map<String, Spatial> getModelsMap() {
        return Collections.unmodifiableMap(modelsMap);
    }

    /**
     * Clears all loaded assets. Thread-safe.
     */
    public void clearAll() {
        textureMap.clear();
        modelsMap.clear();
        logger.info("Cleared all assets from repository");
    }
}