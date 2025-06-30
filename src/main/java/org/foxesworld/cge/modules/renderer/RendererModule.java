package org.foxesworld.cge.modules.renderer;

import com.jme3.app.Application;
import com.jme3.renderer.Caps;
import com.jme3.renderer.Limits;
import com.jme3.renderer.Renderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.modules.renderer.postProcessing.PostProcessingModule;
import org.foxesworld.cge.modules.renderer.skyBox.SkyBox;
import org.foxesworld.cge.modules.scene.SceneModule;
import org.lwjgl.opengl.GL11;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.StringJoiner;

/**
 * Manages rendering-related sub-modules and settings.
 * <p>
 * This module acts as a coordinator, ensuring that core rendering components like
 * {@link SkyBox} and {@link PostProcessingModule} are registered and initialized correctly.
 * It follows a cleaner architecture where it does not manage sub-modules' lifecycles directly,
 * but instead registers them with the main {@code ModuleManager}, which handles them.
 * </p>
 * <p>
 * On initialization, this module logs a detailed and stylized report of the GPU and renderer
 * capabilities, which is useful for debugging and diagnostics.
 * </p>
 */
public class RendererModule extends EngineModule<RendererConfig> {

    private static final Logger LOGGER = LogManager.getLogger(RendererModule.class);
    private final ModuleManager subManager;
    private SkyBox skyBox;
    private PostProcessingModule postProcessingModule;

    /**
     * Constructs the RendererModule.
     * @param app The CalistaGameEngine instance.
     */
    public RendererModule(CalistaGameEngine app) {
        super(RendererModule.class, RendererConfig.class, app);
        this.subManager = new ModuleManager(app);
    }

    /**
     * Initializes the module and its dependent sub-modules.
     * It registers SkyBox and PostProcessing with the main module manager and logs GPU info.
     *
     * @param app The CalistaGameEngine instance.
     * @throws IllegalStateException if the configuration is not loaded.
     */
    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        if (getConfig() == null) {
            throw new IllegalStateException("RendererConfig not loaded. Cannot initialize RendererModule.");
        }
        // Register sub-modules with the main manager. Their lifecycle will be handled automatically.
        // This is a cleaner approach than managing a local subManager.
        this.skyBox = new SkyBox(this);
        this.postProcessingModule = new PostProcessingModule(this);
        subManager.register(skyBox, 20);
        subManager.register(postProcessingModule, 30);

        initializeSceneIntegration(app);
    }

    /**
     * Called when the configuration is reloaded.
     * This module itself doesn't have much to re-apply, but it must propagate the reload
     * event to its dependent sub-modules.
     */
    @Override
    public void onConfigReloaded() throws Exception {
        LOGGER.info("Propagating config reload to rendering sub-modules...");

        // The sub-modules are responsible for reacting to the config change.
        // For example, PostProcessingModule will enable/disable itself based on the new config.
        SkyBox skyBox = gameEngine.getModuleManager().getModule(SkyBox.class);
        if (skyBox != null) {
            skyBox.onConfigReloaded();
        }

        PostProcessingModule ppModule = gameEngine.getModuleManager().getModule(PostProcessingModule.class);
        if (ppModule != null) {
            ppModule.onConfigReloaded();
        }
    }

    /**
     * Sets up a callback to adjust renderer settings when the scene becomes ready.
     * This establishes the integration point between rendering and scene content.
     *
     * @param app The CalistaGameEngine instance.
     */
    private void initializeSceneIntegration(CalistaGameEngine app) {
        SceneModule sceneModule = app.getModuleManager().getModule(SceneModule.class);
        if (sceneModule != null) {
            sceneModule.onSceneReady(ctx -> {
                LOGGER.info("Scene is ready, performing final renderer updates (e.g., environment setup)...");
                // TODO: Implement scene-specific rendering updates, like updating environment maps or lighting probes.
            });
        } else {
            LOGGER.warn("SceneModule not found. Scene-specific renderer integration will be skipped.");
        }
    }


    // --- Accessors for dependent modules ---

    /**
     * Gets the SkyBox module instance managed by the engine.
     * @return The {@link SkyBox} instance, or null if not yet initialized.
     */
    public SkyBox getSkyBox() {
        return this.skyBox;
    }

    /**
     * Gets the PostProcessingModule instance managed by the engine.
     * @return The {@link PostProcessingModule} instance, or null if not yet initialized.
     */
    public PostProcessingModule getPostProcessingModule() {
        return gameEngine.getModuleManager().getModule(PostProcessingModule.class);
    }

    // --- Unused Lifecycle Methods ---

    @Override
    protected void onEnable() {
        // Initialization logic is in initModule()
    }

    @Override
    protected void onDisable() {
        // No special disable logic required
    }

    @Override
    protected void updateModule(float tpf) {
        // No per-frame logic; sub-modules are updated by the main loop.
    }

    @Override
    protected void cleanupModule(Application app) {
        // Sub-modules are cleaned up by the main ModuleManager, so no action is needed here.
        LOGGER.info("RendererModule has been cleaned up.");
    }
}