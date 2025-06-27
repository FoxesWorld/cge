package org.foxesworld.cge.core.loader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.io.progressBar.ProgressListener;
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
 *
 * Now supports dynamic number of loaders based on registered loaders.
 */
public class AssetLoader {
    private static final Logger logger = LogManager.getLogger(AssetLoader.class);

    private final List<LoaderWrapper> loaders = new ArrayList<>();
    private final List<Runnable> assetsLoadedListeners = new ArrayList<>();

    public AssetLoader(CalistaGameEngine engine) {
        // Register all loaders here. Add more if needed.
        loaders.add(new LoaderWrapper(new TextureLoader(engine)));
        loaders.add(new LoaderWrapper(new ModelLoader(engine)));
        // For additional loaders, simply add:
        // loaders.add(new LoaderWrapper(new SoundLoader(engine)));
        // etc.
    }

    /**
     * Register a callback to be invoked after all assets are loaded.
     */
    public void onAssetsLoaded(Runnable r) {
        assetsLoadedListeners.add(r);
    }

    /**
     * Loads all registered loaders, logging counts and invoking callback when done.
     */
    public void loadAllAssets(ProgressListener progressListener) {
        logger.info("Starting asset loading with {} loader(s)...", loaders.size());
        CallbackLatch latch = new CallbackLatch(loaders.size(), () -> {
            logger.info("All assets loaded successfully.");
            for (Runnable r : assetsLoadedListeners) {
                try {
                    r.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    logger.warn("Exception in onAssetsLoaded listener", ex);
                }
            }
        });

        for (LoaderWrapper loader : loaders) {
            loader.setProgressListener(progressListener);
            loader.loadWithLatch(latch);
        }
    }

    /**
     * Register a new loader at runtime.
     * @param loader any object that supports setProgressListener and loadWithLatch
     */
    public void registerLoader(ILoader loader) {
        loaders.add(new LoaderWrapper(loader));
    }
}