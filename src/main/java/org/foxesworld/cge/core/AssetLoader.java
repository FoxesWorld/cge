package org.foxesworld.cge.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;

public class AssetLoader {
    private static final Logger logger = LogManager.getLogger(AssetLoader.class);
    private final CalistaGameEngine calistaGameEngine;
    private final TextureLoader textureLoader;
    public AssetLoader(CalistaGameEngine calistaGameEngine){
        this.calistaGameEngine = calistaGameEngine;
       this.textureLoader = new TextureLoader(calistaGameEngine);
    }

    public void loadTextures(){
        textureLoader.loadCgtexAsync(AssetLoader.class.getClassLoader().getResourceAsStream("textures.json"))
                .thenRun(() -> {
                  logger.info("Textures Loaded");
                    //Somethibng else
                  });
    }
}
