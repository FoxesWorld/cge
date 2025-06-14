package org.foxesworld.cge.core.loader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.utils.CallbackLatch;

/**
 * AssetLoader orchestrates loading of textures and models via JSON loaders,
 * logging statistics for both.
 */
public class AssetLoader {
    private static final Logger logger = LogManager.getLogger(AssetLoader.class);

    private final TextureLoader textureLoader;
    private final ModelLoader modelLoader;

    public AssetLoader(CalistaGameEngine engine) {
        this.textureLoader = new TextureLoader(engine);
        this.modelLoader   = new ModelLoader(engine);
    }

    /**
     * Loads all textures and models, logging counts and invoking callback when done.
     */
    public void loadAllAssets(Runnable onAllLoaded) {
        logger.info("Starting asset loading...");
        // latch for two tasks: textures and models
        CallbackLatch latch = new CallbackLatch(2, () -> {
            logger.info("All assets loaded successfully.");
            onAllLoaded.run();
        });

        // load textures and models via abstract loaders
        textureLoader.loadWithLatch(latch);
        modelLoader.loadWithLatch(latch);
    }
}
