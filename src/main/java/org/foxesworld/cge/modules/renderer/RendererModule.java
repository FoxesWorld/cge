package org.foxesworld.cge.modules.renderer;

import com.jme3.app.Application;
import com.jme3.renderer.Renderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.renderer.postProcessing.PostProcessingModule;
import org.foxesworld.cge.modules.renderer.skyBox.SkyBox;
import org.foxesworld.cge.modules.scene.SceneModule;

/**
 * RendererModule is responsible for aggregating and managing rendering-related sub-modules,
 * including SkyBox, PostProcessing, and integration with SceneModule.
 * <p>
 * This module handles initialization, configuration reloads, and cleanup of the renderer
 * and its sub-components.
 * </p>
 */
public class RendererModule extends EngineModule<RendererConfig> {

    private static final Logger logger = LogManager.getLogger(RendererModule.class);

    private final SkyBox skyBox;
    private PostProcessingModule postProcessingModule;
    private boolean postProcessingRegistered = false;

    public RendererModule(CalistaGameEngine app) {
        super(RendererModule.class, RendererConfig.class, app);
        this.skyBox = new SkyBox(this);
        app.getModuleManager().register(skyBox, 100);
    }

    @Override
    protected void onEnable() {
        // Logging or initialization if needed
    }

    @Override
    protected void onDisable() {
        // No special disable logic
    }

    @Override
    public void onConfigReloaded() {
        logger.info("Reloading RendererModule config at runtime...");
        RendererConfig cfg = getConfig();
        if (cfg == null) {
            logger.warn("RendererConfig not loaded, skipping reload");
            return;
        }

        boolean wantPP = cfg.isEnablePostEffects();

        // Register or deregister post-processing module as needed
        if (wantPP) {
            if (postProcessingModule == null) {
                postProcessingModule = new PostProcessingModule(this);
                gameEngine.getModuleManager().register(postProcessingModule, 30);
                postProcessingRegistered = true;
                logger.info("PostProcessingModule registered");
            } else {
                logger.info("PostProcessingModule already present, reloading its config");
                postProcessingModule.onConfigReloaded();
            }
        } else {
            if (postProcessingModule != null) {
                logger.info("Post effects disabled in config, removing PostProcessingModule");
                gameEngine.getModuleManager().shutdownModule(PostProcessingModule.class);
                postProcessingModule = null;
                postProcessingRegistered = false;
            }
        }

        // Re-integrate with scene module if needed
        initializeSceneModule(gameEngine);

        logger.info("RendererModule config reload complete.");
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        RendererConfig cfg = getConfig();
        if (cfg == null) {
            throw new IllegalStateException("RendererConfig not loaded");
        }

        if (cfg.isEnablePostEffects() && postProcessingModule == null) {
            postProcessingModule = new PostProcessingModule(this);
            app.getModuleManager().register(postProcessingModule, 30);
            postProcessingRegistered = true;
        }

        initializeSceneModule(app);
    }

    private void initializeSceneModule(CalistaGameEngine app) {
        SceneModule sceneModule = app.getModuleManager().getModule(SceneModule.class);
        if (sceneModule != null) {
            sceneModule.onSceneReady(ctx -> {
                logger.info("Scene loaded, updating renderer settings...");
                updateRendererSettingsBasedOnScene();
            });
        }
    }

    private void updateRendererSettingsBasedOnScene() {
        logger.info("Updating lights on scene loaded!");
        // TODO: Update light or environment settings based on scene data
    }

    @Override
    protected void updateModule(float tpf) {
        // No direct per-frame logic here
    }

    public SkyBox getSkyBox() {
        return skyBox;
    }

    @Override
    protected void cleanupModule(Application app) {
        logger.info("Cleaning up RendererModule and sub-modules...");
        // Sub-modules perform their own cleanup
        logger.info("RendererModule cleaned up.");
    }
}