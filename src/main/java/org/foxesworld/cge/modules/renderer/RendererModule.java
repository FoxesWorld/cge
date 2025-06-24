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

    private final ModuleManager subManager;

    /**
     * Constructs the RendererModule with its default SkyBox sub-module.
     *
     * @param app the CalistaGameEngine instance
     */
    public RendererModule(CalistaGameEngine app) {
        super("renderer", RendererConfig.class, app);
        this.subManager = new ModuleManager(app);
        // Register default SkyBox before config is loaded
        subManager.register(new SkyBox(this), 100);
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

    /**
     * Called when this module is disabled; currently no additional logic.
     */
    @Override
    protected void onDisable() {
        // No special disable logic
    }

    /**
     * Called after configuration is reloaded; can be used to apply dynamic changes.
     */
    @Override
    public void onConfigReloaded() {
        // No dynamic config reload logic yet
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
        if (cfg.isEnablePostEffects()) {
            subManager.register(new PostProcessingModule(app), 30);
        }

        // Initialize and load all registered sub-modules
        subManager.initializeAll(app);
        subManager.loadAll(app, () -> logger.info("All render modules are ready!"));

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

    /**
     * Called each frame; sub-modules handle their own updates via AppState.
     *
     * @param tpf time per frame
     */
    @Override
    protected void updateModule(float tpf) {
        // No direct per-frame logic here
    }

    /**
     * Cleans up the renderer and its sub-modules when the engine shuts down.
     *
     * @param app the Application instance
     */
    @Override
    protected void cleanupModule(Application app) {
        logger.info("Cleaning up RendererModule and sub-modules...");
        // Sub-modules perform their own cleanup
        logger.info("RendererModule cleaned up.");
    }
}