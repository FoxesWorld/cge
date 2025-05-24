package org.foxesworld.cge.scene;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleHealthMonitor;
import org.foxesworld.cge.core.module.ModuleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Module responsible for loading and displaying a 3D scene.
 */
public class SceneModule extends EngineModule<SceneConfig> {
    private static final Logger logger = LoggerFactory.getLogger(SceneModule.class);
    private AssetManager assetManager;
    private Spatial sceneRoot;

    public SceneModule(CalistaGameEngine calistaGameEngine) {
        super("scene", SceneConfig.class, calistaGameEngine);
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        logger.info("SceneModule: loading scene...");
        this.assetManager = app.getAssetManager();
        SceneConfig cfg = getConfig();

        if (cfg == null || cfg.getScenePath() == null) {
            throw new IllegalStateException("SceneConfig or scenePath is null");
        }

        // 1) Загрузка модели можно сделать в любом потоке, т. к. это просто чтение ассетов
        Spatial loaded = assetManager.loadModel(cfg.getScenePath());
        loaded.setName("SceneRoot");
        loaded.setLocalTranslation(cfg.getTranslation());
        loaded.setLocalRotation(cfg.getRotation());
        loaded.setLocalScale(cfg.getScale());

        // 2) А вот прикрепление к сцене — только в render потоке:
        app.enqueue(() -> {
            sceneRoot = loaded;
            app.getRootNode().attachChild(sceneRoot);
            logger.info("Scene '{}' attached on render thread", cfg.getScenePath());
            // Переводим состояние в RUNNING именно после attach
            ModuleHealthMonitor.getInstance()
                    .reportState(getName(), ModuleState.RUNNING);
            return null;
        });
    }


    @Override
    protected void updateModule(float tpf) throws Exception {
        // No per-frame logic by default; the scene graph renders automatically
    }

    @Override
    protected void cleanupModule(Application app) throws Exception {
        logger.info("SceneModule: scheduling scene detach...");
        if (sceneRoot != null) {
            Spatial toDetach = sceneRoot;
            sceneRoot = null;
            app.enqueue(() -> {
                sceneRoot.getParent().detachChild(toDetach);
                logger.info("Scene detached on render thread");
                ModuleHealthMonitor.getInstance()
                        .reportState(getName(), ModuleState.CLEANED_UP);
                return null;
            });
        }
    }

    @Override
    protected void onConfigReloaded() throws Exception {
        logger.info("SceneModule config reloaded: reloading scene");
        // Detach old
        if (sceneRoot != null) {
            gameEngine.getRootNode().detachChild(sceneRoot);
        }
        // Load new
        initModule(gameEngine);
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }
}