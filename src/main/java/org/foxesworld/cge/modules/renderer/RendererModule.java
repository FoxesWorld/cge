package org.foxesworld.cge.modules.renderer;

import com.jme3.app.Application;
import com.jme3.renderer.Caps;
import com.jme3.renderer.Renderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.modules.renderer.postProcessing.PostProcessingModule;
import org.foxesworld.cge.modules.renderer.skyBox.SkyBox;
import org.foxesworld.cge.modules.scene.SceneModule;

import java.util.EnumSet;

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
    private static final String CONFIG_FILE = "render_config";

    private boolean postProcessingRegistered = false;
    private final SkyBox skyBox;

    /**
     * Constructs the RendererModule with its default SkyBox sub-module.
     *
     * @param app the CalistaGameEngine instance
     */
    public RendererModule(CalistaGameEngine app) {
        super(RendererModule.class, RendererConfig.class, app);
        // Always register SkyBox (idempotent, safe for multi-register)
        this.skyBox = new SkyBox(this);
        app.getModuleManager().register(skyBox, 100);
    }

    /**
     * Called when this module is enabled; logs renderer capabilities.
     */
    @Override
    protected void onEnable() {
        Renderer renderer = getApplication().getRenderer();
        logger.info("Renderer initialized:");
        logRendererCapabilities(renderer.getCaps());
    }

    /**
     * Logs key renderer capabilities for debugging purposes.
     *
     * @param caps the set of capabilities supported by the renderer
     */
    private void logRendererCapabilities(EnumSet<Caps> caps) {
        logger.info("Capabilities:");
        logger.info(" - Shader Language Support: {}", caps.contains(Caps.GLSL100));
        logger.info(" - FrameBuffer Support:     {}", caps.contains(Caps.FrameBuffer));
        logger.info(" - Geometry Shader:         {}", caps.contains(Caps.GeometryShader));
        logger.info(" - Texture Array:           {}", caps.contains(Caps.TextureArray));
    }

    @Override
    protected void onDisable() {
        // No special disable logic
    }

    /**
     * Called after configuration is reloaded; applies config and reloads submodules as needed.
     */
    @Override
    public void onConfigReloaded() {
        logger.info("Reloading RendererModule config at runtime...");
        RendererConfig cfg = getConfig();
        if (cfg == null) {
            logger.warn("RendererConfig not loaded, skipping reload");
            return;
        }
        //CalistaGameEngine app = getApplication();

        // Handle post-processing module reload
        PostProcessingModule ppModule = gameEngine.getModuleManager().getModule(PostProcessingModule.class);
        boolean hasPP = (ppModule != null);

        if (cfg.isEnablePostEffects()) {
            if (!hasPP) {
                logger.info("Post effects enabled in config, registering PostProcessingModule");
                gameEngine.getModuleManager().register(new PostProcessingModule(gameEngine), 30);
                postProcessingRegistered = true;
            } else {
                logger.info("PostProcessingModule already present, reloading its config");
                ppModule.onConfigReloaded();
            }
        } else {
            if (hasPP) {
                logger.info("Post effects disabled in config, removing PostProcessingModule");
                gameEngine.getModuleManager().shutdownModule(PostProcessingModule.class); // You must implement shutdownModule in your ModuleManager
                postProcessingRegistered = false;
            }
        }

        // SkyBox should always be present, but if you want to allow dynamic removal, handle similarly

        // Re-integrate with scene module if needed
        initializeSceneModule(gameEngine);

        logger.info("RendererModule config reload complete.");
    }

    /**
     * Initializes the module based on its configuration.
     * Registers post-processing sub-module if enabled, then initializes all sub-modules.
     *
     * @param app the CalistaGameEngine instance
     * @throws Exception if configuration is not loaded or initialization fails
     */
    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        RendererConfig cfg = getConfig();
        if (cfg == null) {
            throw new IllegalStateException("RendererConfig not loaded");
        }

        // Register post-processing if enabled in config
        if (cfg.isEnablePostEffects() && app.getModuleManager().getModule(PostProcessingModule.class) == null) {
            app.getModuleManager().register(new PostProcessingModule(app), 30);
            postProcessingRegistered = true;
        }

        // Setup integration with scene module
        initializeSceneModule(app);
    }

    /**
     * Sets up a callback to adjust renderer settings when the scene is ready.
     *
     * @param app the CalistaGameEngine instance
     */
    private void initializeSceneModule(CalistaGameEngine app) {
        SceneModule sceneModule = app.getModuleManager().getModule(SceneModule.class);
        if (sceneModule != null) {
            sceneModule.onSceneReady(ctx -> {
                logger.info("Scene loaded, updating renderer settings...");
                updateRendererSettingsBasedOnScene();
            });
        }
    }

    /**
     * Placeholder for scene-based renderer adjustments, such as lighting updates.
     */
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