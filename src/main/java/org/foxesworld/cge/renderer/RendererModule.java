package org.foxesworld.cge.renderer;

import com.jme3.app.Application;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.renderer.camera.CameraModule;
import org.foxesworld.cge.renderer.lighting.LightingModule;
import org.foxesworld.cge.renderer.postProcessing.PostProcessingModule;
import org.foxesworld.cge.scene.SceneModule;

import java.util.EnumSet;

/**
 * Universal RendererModule: aggregates Camera, Lighting, and PostProcessing sub-modules.
 */
public class RendererModule extends EngineModule<Void> {
    private static final Logger logger = LogManager.getLogger(RendererModule.class);
    private static final String CONFIG_FILE = null;

    private final ModuleManager subManager;

    public RendererModule(CalistaGameEngine app) {
        super(CONFIG_FILE, Void.class, app);
        subManager = new ModuleManager(app);
        app.getTextureLoader().loadCgtex("test.cgtex");
        subManager.register(new CameraModule(app), 10);
        subManager.register(new LightingModule(app), 20);
        subManager.register(new PostProcessingModule(app), 30);
    }

    @Override
    protected void onEnable() {
        // Получаем объект Renderer
        com.jme3.renderer.Renderer renderer = getApplication().getRenderer();

        // Логируем основную информацию о рендерере
        logger.info("Renderer initialized:");
        //logger.info("Vendor:   {}", renderer.getVendor());
        //logger.info("Renderer: {}", renderer.getRenderer());
        //logger.info("Version:  {}", renderer.getVersion());

        // Получаем возможности рендерера
        EnumSet<com.jme3.renderer.Caps> caps = renderer.getCaps();
        logger.info("Capabilities:");
        logger.info(" - Shader Language Support: {}", caps.contains(com.jme3.renderer.Caps.GLSL100));
        logger.info(" - FrameBuffer Support:     {}", caps.contains(com.jme3.renderer.Caps.FrameBuffer));
        //logger.info(" - 3D Textures Support:     {}", caps.contains(com.jme3.renderer.Caps.Texture3D));
        //logger.info(" - Shadow Support:          {}", caps.contains(com.jme3.renderer.Caps.Shadow));

        // Можно логировать и другие Caps при необходимости
         logger.info(" - Geometry Shader:          {}", caps.contains(com.jme3.renderer.Caps.GeometryShader));
         logger.info(" - Texture Array:            {}", caps.contains(com.jme3.renderer.Caps.TextureArray));
    }


    @Override
    protected void onDisable() {
        // No-op
    }

    @Override
    protected void onConfigReloaded() {
        // No configuration
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        logger.info("Initializing RendererModule and sub-modules...");
        subManager.initializeAll(app);
        // Получаем SceneModule
        SceneModule sceneModule = app.getModuleManager().getModule(SceneModule.class);
        if (sceneModule != null) {
            sceneModule.onSceneReady(() -> {
                logger.info("Scene loaded, updating renderer settings...");
                updateRendererSettingsBasedOnScene();
            });
        }
        logger.info("RendererModule initialized.");
    }

    private void updateRendererSettingsBasedOnScene() {
        // Здесь вызываем методы подсистем освещения, постобработки и т.п.
        // Например:
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
