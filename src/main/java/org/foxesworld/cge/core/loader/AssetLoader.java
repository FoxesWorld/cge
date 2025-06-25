package org.foxesworld.cge.core.loader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.loader.model.ModelLoader;
import org.foxesworld.cge.core.loader.texture.TextureLoader;
import org.foxesworld.cge.core.utils.CallbackLatch;

import java.util.ArrayList;
import java.util.List;

/**
 * AssetLoader orchestrates loading of textures and models via JSON loaders,
 * logging statistics for both.
 *
 * Supports registration of onAssetsLoaded(Runnable) events.
 */
public class AssetLoader {
    private static final Logger logger = LogManager.getLogger(AssetLoader.class);
    private final TextureLoader textureLoader;
    private final ModelLoader modelLoader;

    private final List<Runnable> assetsLoadedListeners = new ArrayList<>();

    public AssetLoader(CalistaGameEngine engine) {
        this.textureLoader = new TextureLoader(engine);
        this.modelLoader   = new ModelLoader(engine);
    }

    /**
     * Register a callback to be invoked after all assets are loaded.
     */
    public void onAssetsLoaded(Runnable r) {
        assetsLoadedListeners.add(r);
    }

    /**
     * Loads all textures and models, logging counts and invoking callback when done.
     */
    public void loadAllAssets(AssetProgressListener progressListener) {
        logger.info("Starting asset loading...");
        CallbackLatch latch = new CallbackLatch(2, () -> {
            logger.info("All assets loaded successfully.");
            // Invoke all registered listeners
            for (Runnable r : assetsLoadedListeners) {
                try {
                    r.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    logger.warn("Exception in onAssetsLoaded listener", ex);
                }
            }
        });

        textureLoader.setProgressListener(progressListener);
        modelLoader.setProgressListener(progressListener);

        textureLoader.loadWithLatch(latch);
        modelLoader.loadWithLatch(latch);
    }
}