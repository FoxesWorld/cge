package org.foxesworld.cge.scene;

import com.jme3.app.Application;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.file.extensions.cgs.*;
import org.foxesworld.cge.core.file.extensions.cgs.parser.types.LightingParser;
import org.foxesworld.cge.core.file.extensions.cgs.parser.types.TerrainParser;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleHealthMonitor;
import org.foxesworld.cge.core.module.ModuleState;
import org.foxesworld.cge.core.streaming.StreamingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SceneModule extends EngineModule<SceneConfig> {
    private static final Logger logger = LoggerFactory.getLogger(SceneModule.class);

    // scene-specific
    private CGSMetadata cgsMetadata;
    private CGSFile sceneFile;
    private List<ChunkEntry> entries;
    private StreamingManager<Integer, SceneChunk> streamingManager;
    private Node sceneRoot;
    private final CalistaGameEngine app;
    private ChunkFieldTypeConfigLoader configLoader;
    private final AtomicInteger chunksRemaining = new AtomicInteger(0);
    private final List<Runnable> onSceneReadyCallbacks = new ArrayList<>();

    public SceneModule(CalistaGameEngine app) {
        super("scene", SceneConfig.class, app);
        this.app = app;
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        logger.debug("SceneModule: initializing...");

        try {
            configLoader = new ChunkFieldTypeConfigLoader(getClass().getClassLoader().getResourceAsStream("chunkArguments.json"));
        } catch (IOException e) {
            logger.error("Failed to load chunk field config", e);
        }
        SceneConfig cfg = getConfig();
        if (cfg == null || cfg.getScenePath() == null) {
            throw new IllegalStateException("SceneConfig or scenePath is null");
        }

        app.getByteStreamer().streamAsync(cfg.getScenePath(),
                bytes -> {
                    try {
                        this.sceneFile = new CGSFile(new File(cfg.getScenePath()), "r");
                        //CGSFileReader reader = this.sceneFile.readFile();
                        this.sceneFile.readFileNew();
                        this.cgsMetadata = sceneFile.getMetadata();
                        this.entries = new ArrayList<>(sceneFile.getChunkTable());
                        setupStreamingForChunks(this.sceneFile);

                    } catch (Exception e) {
                        logger.error("Failed to parse CGS bytes", e);
                        ModuleHealthMonitor.getInstance().reportState(getName(), ModuleState.SHUTTING_DOWN);
                    }
                },
                error -> {
                    logger.error("Failed to stream CGS file: {}", error.getMessage(), error);
                    ModuleHealthMonitor.getInstance().reportState(getName(), ModuleState.SHUTTING_DOWN);
                }
        );
    }

    private void setupStreamingForChunks(CGSFile reader) {
        this.streamingManager = new StreamingManager<>(reader::readChunk, true, 2);
        this.sceneRoot = new Node(cgsMetadata.getSceneName());
        chunksRemaining.set(entries.size());
        logger.debug("SceneModule: {} chunks to stream", entries.size());
        // Функция для обработки загруженных чанков
        Consumer<SceneChunk> onChunk = chunk -> {
            // Парсим чанк в Spatial
            Spatial spat = parseChunk(app, chunk);

            // Добавляем в sceneRoot
            app.enqueue(() -> {
                app.getRootNode().attachChild(spat);
                // Если все чанки загружены, прикрепляем sceneRoot к главному RootNode
                if (chunksRemaining.decrementAndGet() == 0) {
                    attachSceneRoot();
                }
                return null;
            });
        };

        // Функция обработки ошибки при загрузке чанка
        Consumer<Throwable> onError = err -> {
            logger.error("Failed to stream chunk: {}", err.getMessage(), err);

            // Даже если произошла ошибка, продолжаем процесс
            if (chunksRemaining.decrementAndGet() == 0) {
                attachSceneRoot();
            }
        };

        // Стримим каждый чанк
        for (ChunkEntry e : entries) {
            if (streamingManager != null) {
                streamingManager.streamAsync(e.id(), onChunk, onError);
            }
        }
    }

    private void attachSceneRoot() {
        app.enqueue(() -> {
            // Добавляем sceneRoot в главный RootNode
            app.getRootNode().attachChild(sceneRoot);
            logger.info("All chunks streamed sceneRoot attached.");
            // Уведомляем систему, что сцена готова
            ModuleHealthMonitor.getInstance().reportState(getName(), ModuleState.RUNNING);
            // Запускаем все отложенные callback-методы
            onSceneReadyCallbacks.forEach(cb -> {
                try {
                    cb.run();
                } catch (Exception ex) {
                    logger.warn("onSceneReady failed", ex);
                }
            });
        });
    }

    private Spatial parseChunk(CalistaGameEngine app, SceneChunk chunk) {
        logger.debug("Chunk {} data - {}", chunk.getEntry().type(), dumpBufferHex(chunk.getData()));
        return switch (chunk.getEntry().type()) {
            case TERRAIN -> new TerrainParser().parse(app, chunk, configLoader);
            case LIGHTING -> new LightingParser().parse(app, chunk, configLoader);
            default -> new Node("CustomChunk-" + chunk.getId());
        };
    }

    @Override
    protected void updateModule(float tpf) {
        /* мониторинг прогресса */
    }

    @Override
    protected void cleanupModule(Application app) throws Exception {
        logger.debug("SceneModule: cleaning up...");
        if (sceneRoot != null && sceneRoot.getParent() != null) {
            Node toDetach = sceneRoot;
            sceneRoot = null;
            app.enqueue(() -> {
                toDetach.getParent().detachChild(toDetach);
                ModuleHealthMonitor.getInstance().reportState(getName(), ModuleState.CLEANED_UP);
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
        logger.debug("SceneModule: config reloaded, restarting...");
        cleanupModule(app);
        initModule(app);
    }

    public void onSceneReady(Runnable callback) {
        onSceneReadyCallbacks.add(Objects.requireNonNull(callback));
    }

    public CGSMetadata getMetadata() {
        return cgsMetadata;
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }
}
