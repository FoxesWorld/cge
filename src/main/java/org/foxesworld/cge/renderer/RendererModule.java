package org.foxesworld.cge.renderer;

import com.jme3.app.Application;
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
 * Universal RendererModule: aggregates Camera, Lighting, and PostProcessing sub-modules.
 */
public class RendererModule extends EngineModule<RendererConfig> {
    private static final Logger logger = LogManager.getLogger(RendererModule.class);
    private static final String CONFIG_FILE = "render_config";

    private final ModuleManager subManager;


    public RendererModule(CalistaGameEngine app) {
        super("renderer", RendererConfig.class, app);
        this.subManager = new ModuleManager(app);
    }
    @Override
    protected void onEnable() {
        com.jme3.renderer.Renderer renderer = getApplication().getRenderer();
        logger.info("Renderer initialized:");
        EnumSet<com.jme3.renderer.Caps> caps = renderer.getCaps();
        logRendererCapabilities(caps);
    }

    private void logRendererCapabilities(EnumSet<com.jme3.renderer.Caps> caps) {
        logger.info("Capabilities:");
        logger.info(" - Shader Language Support: {}", caps.contains(com.jme3.renderer.Caps.GLSL100));
        logger.info(" - FrameBuffer Support:     {}", caps.contains(com.jme3.renderer.Caps.FrameBuffer));
        logger.info(" - Geometry Shader:          {}", caps.contains(com.jme3.renderer.Caps.GeometryShader));
        logger.info(" - Texture Array:            {}", caps.contains(com.jme3.renderer.Caps.TextureArray));
    }

    @Override
    protected void onDisable() {
        // Cleanup when module is disabled, if needed (No-op for now)
    }

    @Override
    protected void onConfigReloaded() {
        // No configuration handling needed
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        RendererConfig cfg = getConfig();
        if (cfg == null) {
            throw new IllegalStateException("RendererConfig not loaded");
        }

        // Регистрируем SkyBox (с приоритетом выше PostProcessing)
        subManager.register(new SkyBox(this), 30);

        // Инициализация AppStates (в том числе вызов onEnable)
        subManager.initializeAll(app);

        // Последовательная загрузка SkyBox
        subManager.loadAll(app, () -> {
            logger.info("SkyBox loaded. Checking post-processing...");

            // Проверка, загружен ли SkyBox
            SkyBox skyBox = subManager.getModule(SkyBox.class);
            if (skyBox == null) {
                logger.error("SkyBox not loaded properly. Cannot continue.");
                return;
            }

            // Если эффекты включены — регистрируем и загружаем PostProcessing
            if (cfg.isEnablePostEffects()) {
                logger.info("PostProcessing enabled, registering...");
                subManager.register(new PostProcessingModule(app), 10);

                // Загружаем PostProcessing после SkyBox
                subManager.loadAll(app, () -> {
                    logger.info("PostProcessingModule loaded. All render modules are ready!");
                });

            } else {
                logger.info("PostProcessing disabled. All render modules are ready!");
            }
        });
    }



    private void initializeSceneModule(CalistaGameEngine app) {
        // Получаем SceneModule
        SceneModule sceneModule = app.getModuleManager().getModule(SceneModule.class);
        if (sceneModule != null) {
            sceneModule.onSceneReady(() -> {
                logger.info("Scene loaded, updating renderer settings...");
                updateRendererSettingsBasedOnScene();
            });
        }
    }

    private void updateRendererSettingsBasedOnScene() {
        logger.info("Updating lights on scene loaded!");
    }

    @Override
    protected void updateModule(float tpf) {
        // Sub-modules update via AppState
    }

    @Override
    protected void cleanupModule(Application app) {
        logger.info("Cleaning up RendererModule and sub-modules...");
        // Sub-modules cleanup themselves
        logger.info("RendererModule cleaned up.");
    }
}
