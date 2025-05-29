package org.foxesworld.cge.renderer;

import com.jme3.app.Application;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.renderer.camera.CameraModule;
import org.foxesworld.cge.renderer.postProcessing.PostProcessingModule;
import org.foxesworld.cge.scene.SceneModule;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;

/**
 * Universal RendererModule: aggregates Camera, Lighting, and PostProcessing sub-modules.
 */
public class RendererModule extends EngineModule<RendererConfig> {
    private static final Logger logger = LogManager.getLogger(RendererModule.class);
    private static final String CONFIG_FILE = "render_config";

    private final ModuleManager subManager;

    public RendererModule(CalistaGameEngine app) {
        super(CONFIG_FILE, RendererConfig.class, app);
        initialize(app);
        subManager = new ModuleManager(app);

        // Пример загрузки текстуры (можно сделать асинхронно, если нужно)
        app.getTextureLoader().loadCgtex("testData.cgtex");

        // Регистрируем подмодули в зависимостях
        subManager.register(new CameraModule(app), 10);
        if(config.enablePostEffects) {
            subManager.register(new PostProcessingModule(app), 30);
        }

        // Регистрация LightingModule с условием (можно сделать на основе конфигурации или других факторов)
        //if (isLightingEnabled()) {
            //subManager.register(new LightingModule(app), 20);
        //}
    }

    @Override
    protected void onEnable() {
        // Получаем объект Renderer
        com.jme3.renderer.Renderer renderer = getApplication().getRenderer();

        // Логируем основную информацию о рендерере
        logger.info("Renderer initialized:");
        EnumSet<com.jme3.renderer.Caps> caps = renderer.getCaps();
        logRendererCapabilities(caps);
    }

    private void logRendererCapabilities(EnumSet<com.jme3.renderer.Caps> caps) {
        // Оптимизация логирования, чтобы выводить только важную информацию
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
    protected void initModule(CalistaGameEngine app) {
        logger.info("Initializing RendererModule and sub-modules...");

        // Инициализация подмодулей асинхронно
        CompletableFuture.runAsync(() -> {
            subManager.initializeAll(app);
            initializeSceneModule(app);
        });

        logger.info("RendererModule initialized.");
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
        // Здесь вызываем методы подсистем освещения, постобработки и т.п.
        logger.info("Updating lights on scene loaded!");
    }

    private boolean isLightingEnabled() {
        // Здесь можно проверить конфигурацию или другие параметры, чтобы включить/выключить LightingModule
        return true; // Пример, можно заменить на реальную логику
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
