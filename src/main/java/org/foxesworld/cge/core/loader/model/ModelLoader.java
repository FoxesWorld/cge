package org.foxesworld.cge.core.loader.model;

import com.google.gson.reflect.TypeToken;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.loader.AbstractAssetLoader;
import org.foxesworld.cge.core.loader.ILoader;
import org.foxesworld.cge.core.utils.CallbackLatch;
import org.foxesworld.cge.core.io.progressBar.ProgressListener;

import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;

/**
 * Asynchronous loader for 3D models using a fixed thread pool.
 * Implements ILoader for dynamic registration in AssetLoader.
 */
public class ModelLoader extends AbstractAssetLoader<String> implements ILoader {
    private final AssetManager assetManager;
    private final CalistaGameEngine engine;

    private final ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            runnable -> {
                Thread t = new Thread(runnable, "ModelLoader-" + runnable.hashCode());
                t.setDaemon(true);
                return t;
            }
    );

    public ModelLoader(CalistaGameEngine engine) {
        this.engine = engine;
        this.assetManager = engine.getAssetManager();
    }

    @Override
    protected String getJsonResourcePath() {
        return "config/data/models.json";
    }

    @Override
    protected Type getListType() {
        return new TypeToken<List<String>>() {}.getType();
    }

    @Override
    protected CompletableFuture<Integer> loadEntryAsync(String path) {
        return CompletableFuture.supplyAsync(() -> {
            Spatial model = assetManager.loadModel(path);
            String key = extractModelName(path);
            engine.getAssetRepo().addModel(key, model);
            return 1;
        }, executor);
    }

    private String extractModelName(String path) {
        String filename = Paths.get(path).getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }

    // --- ILoader interface implementation for AssetLoader ---

    @Override
    public void setProgressListener(ProgressListener listener) {
        super.setProgressListener(listener);
    }

    @Override
    public void loadWithLatch(CallbackLatch latch) {
        super.loadWithLatch(latch);
    }
}