package org.foxesworld.cge.scene;

import com.jme3.app.Application;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleHealthMonitor;
import org.foxesworld.cge.core.module.ModuleState;
import org.foxesworld.cge.renderer.lighting.LightingModule;
import org.foxesworld.cge.streaming.StreamingManager;
import org.foxesworld.cge.core.cgs.SceneChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * SceneModule загружает CGS файл через StreamingManager и асинхронно собирает сцену.
 */
public class SceneModule extends EngineModule<SceneConfig> {
    private static final Logger logger = LoggerFactory.getLogger(SceneModule.class);

    private final List<Runnable> onSceneReadyCallbacks = new ArrayList<>();
    private StreamingManager streamingManager;
    private Node sceneRoot;
    private CalistaGameEngine app;

    // Для учета прогресса загрузки чанков
    private AtomicInteger chunksToLoad = new AtomicInteger(0);

    public SceneModule(CalistaGameEngine app) {
        super("scene", SceneConfig.class, app);
        this.app = app;
    }

    private void notifySceneReady() {
        for (Runnable cb : onSceneReadyCallbacks) {
            cb.run();
        }
    }

    public void onSceneReady(Runnable callback) {
        onSceneReadyCallbacks.add(callback);
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        logger.info("SceneModule: starting streaming scene load...");
        SceneConfig cfg = getConfig();
        if (cfg == null || cfg.getScenePath() == null) {
            throw new IllegalStateException("SceneConfig or scenePath is null");
        }

        File cgsFile = new File(cfg.getScenePath());
        if (!cgsFile.exists()) {
            throw new IllegalStateException("CGS file not found: " + cfg.getScenePath());
        }

        streamingManager = new StreamingManager(app.getAssetManager());
        streamingManager.loadScene(cgsFile);
        sceneRoot = new Node("SceneRoot");

        // Начинаем поток загрузки чанков
        var allChunks = streamingManager.getCurrentScene().loadAllChunks();
        chunksToLoad.set(allChunks.size());

        Consumer<SceneChunk> chunkConsumer = chunk -> {
            // Здесь логика обработки чанка (десериализация в Spatial, например)
            // Для теста создадим пустой Node с именем чанка:
            Spatial spatial = new Node("Chunk-" + chunk.getId());
            LightingModule lightingModule = app.getModuleManager().getModule(LightingModule.class);

            // Обновляем сцену в render потоке
            app.enqueue(() -> {
                sceneRoot.attachChild(spatial);
                int remaining = chunksToLoad.decrementAndGet();
                logger.info("Chunk {} loaded and attached. Remaining: {}", chunk.getId(), remaining);
                lightingModule.addLight(spatial, 20, 10);
                if (remaining == 0) {
                    app.getRootNode().attachChild(sceneRoot);
                    logger.info("All chunks loaded, scene attached.");
                    ModuleHealthMonitor.getInstance().reportState(getName(), ModuleState.RUNNING);
                    notifySceneReady();
                }
                return null;
            });
        };

        for (var chunkEntry : allChunks) {
            streamingManager.streamChunkAsync(chunkEntry.getId(), chunkConsumer);
        }
    }

    @Override
    protected void updateModule(float tpf) throws Exception {
        // Если нужно, можно обновлять логику стриминга тут
    }

    @Override
    protected void cleanupModule(Application app) throws Exception {
        logger.info("SceneModule: scheduling scene detach...");
        if (sceneRoot != null && sceneRoot.getParent() != null) {
            Spatial toDetach = sceneRoot;
            sceneRoot = null;
            app.enqueue(() -> {
                toDetach.getParent().detachChild(toDetach);
                logger.info("Scene detached on render thread");
                ModuleHealthMonitor.getInstance()
                        .reportState(getName(), ModuleState.CLEANED_UP);
                return null;
            });
        }
        if (streamingManager != null) {
            streamingManager.shutdown();
            streamingManager = null;
        }
    }

    @Override
    protected void onConfigReloaded() throws Exception {
        logger.info("SceneModule config reloaded: reloading scene");
        cleanupModule(app);
        initModule(app);
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}
}
