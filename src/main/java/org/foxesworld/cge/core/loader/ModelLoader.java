package org.foxesworld.cge.core.loader;

import com.google.gson.reflect.TypeToken;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;

import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous model loader with loading statistics.
 */
public class ModelLoader extends AbstractAssetLoader<String> {
    private final AssetManager assetManager;
    private final CalistaGameEngine engine;

    public ModelLoader(CalistaGameEngine engine) {
        this.engine = engine;
        this.assetManager = engine.getAssetManager();
    }

    @Override
    protected String getJsonResourcePath() {
        return "models.json";
    }

    @Override
    protected Type getListType() {
        return new TypeToken<List<String>>() {}.getType();
    }

    @Override
    protected CompletableFuture<Integer> loadEntryAsync(String path) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        return CompletableFuture.supplyAsync(() -> {
            try {
                Spatial model = assetManager.loadModel(path);
                String key = extractModelName(path);
                synchronized (engine.getAssetRepo().getModelMap()) {
                    engine.getAssetRepo().addModel(key, model);
                }
                return 1;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor).whenComplete((res, ex) -> {
            executor.shutdown();
        });
    }

    private String extractModelName(String path) {
        String filename = Paths.get(path).getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }
}