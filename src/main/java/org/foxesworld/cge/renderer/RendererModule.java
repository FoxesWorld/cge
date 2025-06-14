/**
 * RendererModule.java
 *
 * Part of the Calista Game Engine.
 *
 * @author Calista
 */
package org.foxesworld.cge.renderer;

import com.jme3.app.Application;
import com.jme3.renderer.Caps;
import com.jme3.renderer.Renderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.renderer.postProcessing.PostProcessingModule;
import org.foxesworld.cge.renderer.skyBox.SkyBox;
import org.foxesworld.cge.scene.SceneModule;

import java.util.EnumSet;

/**
 * RendererModule is responsible for aggregating and managing rendering-related sub-modules,
 * including SkyBox, PostProcessing, and integration with SceneModule.
 */
public class RendererModule extends EngineModule<RendererConfig> {

    private static final Logger logger = LogManager.getLogger(RendererModule.class);
    private static final String CONFIG_FILE = "render_config";

    private final ModuleManager subManager;

    /**
     * Constructs the RendererModule with a SkyBox sub-module.
     *
     * @param app the game engine instance
     */
    public RendererModule(CalistaGameEngine app) {
        super("renderer", RendererConfig.class, app);
        this.subManager = new ModuleManager(app);
        subManager.register(new SkyBox(this), 10);
    }

    @Override
    protected void onEnable() {
        Renderer renderer = getApplication().getRenderer();
        logger.info("Renderer initialized:");
        logRendererCapabilities(renderer.getCaps());
    }

    /**
     * Logs important rendering capabilities to the console.
     *
     * @param caps the set of renderer capabilities
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
        // No special logic on disable yet
    }

    @Override
    protected void onConfigReloaded() {
        // No dynamic config reload handling
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        RendererConfig cfg = getConfig();
        if (cfg == null) {
            throw new IllegalStateException("RendererConfig not loaded");
        }

        if (cfg.isEnablePostEffects()) {
            subManager.register(new PostProcessingModule(app), 30);
        }

        subManager.initializeAll(app);
        subManager.loadAll(app, () -> logger.info("All render modules are ready!"));
        initializeSceneModule(app);
    }

    /**
     * Initializes scene-dependent render settings.
     *
     * @param app the game engine
     */
    private void initializeSceneModule(CalistaGameEngine app) {
        SceneModule sceneModule = app.getModuleManager().getModule(SceneModule.class);
        if (sceneModule != null) {
            sceneModule.onSceneReady(() -> {
                logger.info("Scene loaded, updating renderer settings...");
                updateRendererSettingsBasedOnScene();
            });
        }
    }

    /**
     * Hook for scene-based renderer adjustments (e.g., lighting).
     */
    private void updateRendererSettingsBasedOnScene() {
        logger.info("Updating lights on scene loaded!");
        // Future: update light settings based on scene contents
    }

    @Override
    protected void updateModule(float tpf) {
        // Sub-modules are updated via their AppState implementations
    }

    @Override
    protected void cleanupModule(Application app) {
        logger.info("Cleaning up RendererModule and sub-modules...");
        // Sub-modules handle their own cleanup
        logger.info("RendererModule cleaned up.");
    }
}
