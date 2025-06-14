package org.foxesworld.cge.core.loader;

import com.google.gson.reflect.TypeToken;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;

import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.List;

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
    protected int loadEntry(String path) throws Exception {
        Spatial model = assetManager.loadModel(path);
        String key = extractModelName(path);
        synchronized (engine.getAssetRepo().getModelsMap()) {
            engine.getAssetRepo().addModel(key, model);
        }
        return 1;
    }

    private String extractModelName(String path) {
        String filename = Paths.get(path).getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }
}