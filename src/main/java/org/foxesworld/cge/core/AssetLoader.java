package org.foxesworld.cge.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;

import java.io.InputStream;

public class AssetLoader {
    private static final Logger logger = LogManager.getLogger(AssetLoader.class);
    private final CalistaGameEngine calistaGameEngine;
    private final TextureLoader textureLoader;
    private final ModelLoader modelLoader;
    public AssetLoader(CalistaGameEngine calistaGameEngine){
        this.calistaGameEngine = calistaGameEngine;
       this.textureLoader = new TextureLoader(calistaGameEngine);
       this.modelLoader = new ModelLoader(calistaGameEngine);
    }

    /**
     * Loads textures asynchronously from a JSON configuration file named "textures.json".
     * The method utilizes the textureLoader's loadCgtexAsync method, which handles parsing
     * and processing of CGTEX-format texture descriptors.
     */
    public void loadTextures(Runnable afterLoad) {
        InputStream textureConfigStream = AssetLoader.class.getClassLoader().getResourceAsStream("textures.json");

        if (textureConfigStream == null) {
            logger.error("Failed to load 'textures.json' – file not found in classpath.");
            return;
        }

        textureLoader.loadCgtexAsync(textureConfigStream)
                .thenRun(() -> {
                    logger.info("Textures successfully loaded from 'textures.json'.");
                    // Additional logic can be added here (e.g. post-processing, callback notifications, etc.)
                    afterLoad.run();
                })
                .exceptionally(ex -> {
                    logger.error("An error occurred while loading textures.", ex);
                    return null;
                });
    }

    public void loadModels(Runnable afterLoad) {
        InputStream modelStream = AssetLoader.class.getClassLoader().getResourceAsStream("models.json");

        modelLoader.loadModelsAsync(modelStream)
                .thenRun(() -> {
                    logger.info("All models successfully loaded.");
                    // Optionally: initialize scene or do post-processing
                    afterLoad.run();
                })
                .exceptionally(ex -> {
                    logger.error("Error occurred while loading models asynchronously", ex);
                    return null;
                });
    }


}
