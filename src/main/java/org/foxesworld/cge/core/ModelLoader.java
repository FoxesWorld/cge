package org.foxesworld.cge.core;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous model loader that reads paths from JSON and loads them using the AssetManager.
 */
public class ModelLoader {

    private static final Logger logger = LogManager.getLogger(ModelLoader.class);
    private final CalistaGameEngine engine;
    private final AssetManager assetManager;
    public ModelLoader(CalistaGameEngine engine) {
        this.engine = engine;
        this.assetManager = engine.getAssetManager();
    }

    /**
     * Asynchronously loads models from a JSON file inside the classpath.
     *
     * @param jsonStream InputStream to the JSON file containing an array of model paths
     * @return CompletableFuture that completes when all models are loaded
     */
    public CompletableFuture<Void> loadModelsAsync(InputStream jsonStream) {
        return CompletableFuture.runAsync(() -> {
            if (jsonStream == null) {
                logger.error("Model JSON stream is null – file not found?");
                return;
            }

            try {
                Gson gson = new Gson();
                Type listType = new TypeToken<List<String>>() {}.getType();
                List<String> modelPaths = gson.fromJson(new InputStreamReader(jsonStream), listType);
                for (String path : modelPaths) {
                    //  try {
                    Spatial model = new Node();
                    if (assetManager != null) {
                        model = assetManager.loadModel(path);
                } else {
                        logger.warn("Null ASssetMgr");
                    }
                        String key = extractModelName(path);
                        synchronized (engine.getAssetRepo().getModelsMap()) {
                            engine.getAssetRepo().addModel(key, model);
                        }
                        logger.info("Model '{}' loaded from '{}'", key, path);
                    //} catch (Exception e) {
                    //logger.error("Failed to load model at path: {}", path, e);
                    //}
                }
            } catch (JsonSyntaxException e) {
                logger.error("Invalid JSON syntax in model file.", e);
            } catch (Exception e) {
                logger.error("Unexpected error during model loading.", e);
            }
        });
    }

    /**
     * Extracts the filename without its extension from the full model path.
     *
     * @param path model path (e.g. "meshes/furniture/bench/ParkBench01.obj")
     * @return model key name (e.g. "ParkBench01")
     */
    private String extractModelName(String path) {
        String filename = Paths.get(path).getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex != -1) ? filename.substring(0, dotIndex) : filename;
    }
}
