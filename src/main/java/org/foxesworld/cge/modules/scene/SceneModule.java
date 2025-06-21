package org.foxesworld.cge.modules.scene;

import com.jme3.app.Application;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.file.extensions.cgs.*;
import org.foxesworld.cge.core.file.extensions.cgs.parser.types.LightingParser;
import org.foxesworld.cge.core.file.extensions.cgs.parser.types.TerrainParser;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.health.ModuleHealthMonitor;
import org.foxesworld.cge.core.module.ModuleState;
import org.foxesworld.cge.core.io.streaming.StreamingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SceneModule extends EngineModule<SceneConfig> {
    private static final Logger logger = LoggerFactory.getLogger(SceneModule.class);

    private CGSMetadata cgsMetadata;
    private CGSFile sceneFile;
    private List<ChunkEntry> entries;
    private StreamingManager<Integer, SceneChunk> streamingManager;
    private Node sceneRoot;
    private final CalistaGameEngine app;
    private ChunkFieldTypeConfigLoader configLoader;
    private final AtomicInteger chunksRemaining = new AtomicInteger(0);

    private final List<Consumer<SceneReadyContext>> onSceneReadyCallbacks = new CopyOnWriteArrayList<>();
    private volatile boolean sceneReady = false;
    private volatile SceneReadyContext readyContext = null;

    public SceneModule(CalistaGameEngine app) {
        super("scene", SceneConfig.class, app, false);
        this.app = app;
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        logger.debug("SceneModule: initializing...");

        try {
            configLoader = new ChunkFieldTypeConfigLoader(
                    getClass().getClassLoader().getResourceAsStream("config/chunkArguments.json"));
        } catch (IOException e) {
            logger.error("Failed to load chunk field config", e);
            throw e;
        }

        SceneConfig cfg = getConfig();
        if (cfg == null || cfg.getScenePath() == null) {
            throw new IllegalStateException("SceneConfig or scenePath is null");
        }

        resetState();

        app.getByteStreamer().streamAsync(cfg.getScenePath(),
                bytes -> {
                    try {
                        this.sceneFile = new CGSFile(new File(cfg.getScenePath()), "r");
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

    private void resetState() {
        sceneReady = false;
        readyContext = null;
        sceneRoot = new Node("SceneRoot");
        onSceneReadyCallbacks.clear();
    }

    private void setupStreamingForChunks(CGSFile reader) {
        this.streamingManager = new StreamingManager<>(reader::readChunk, true, 2);
        this.sceneRoot = new Node(cgsMetadata.getSceneName());
        chunksRemaining.set(entries.size());
        logger.debug("SceneModule: {} chunks to stream", entries.size());

        for (ChunkEntry entry : entries) {
            streamingManager.streamAsync(entry.id(), this::handleChunk, this::handleChunkError);
        }
    }

    private void handleChunk(SceneChunk chunk) {
        Spatial spat = parseChunk(app, chunk);
        app.enqueue(() -> {
            sceneRoot.attachChild(spat);
            if (chunksRemaining.decrementAndGet() == 0) {
                attachSceneRoot();
            }
            return null;
        });
    }

    private void handleChunkError(Throwable error) {
        logger.error("Failed to stream chunk: {}", error.getMessage(), error);
        if (chunksRemaining.decrementAndGet() == 0) {
            attachSceneRoot();
        }
    }

    private void attachSceneRoot() {
        app.enqueue(() -> {
            app.getRootNode().attachChild(sceneRoot);
            logger.info("All chunks streamed, sceneRoot attached.");
            ModuleHealthMonitor.getInstance().reportState(getName(), ModuleState.RUNNING);

            // Создание ECS-контекста только после завершения загрузки
            SceneEntityContext ecsContext = new SceneEntityContext(sceneRoot, cgsMetadata, entries, app.getEcsModule().getEntityData());

            sceneReady = true;
            readyContext = new SceneReadyContext(sceneRoot, cgsMetadata, entries, ecsContext);

            for (Consumer<SceneReadyContext> cb : onSceneReadyCallbacks) {
                safeCallback(cb, readyContext);
            }
        });
    }

    private Spatial parseChunk(CalistaGameEngine app, SceneChunk chunk) {
        logger.debug("Chunk {} data - {}", chunk.getEntry().type(), dumpBufferHex(chunk.getData()));

        // Создание ECS-контекста на лету для конкретного чанка
        //SceneEntityContext ctx = new SceneEntityContext(sceneRoot, cgsMetadata, entries, app.getEcsModule().getEntityData());

        return switch (chunk.getEntry().type()) {
            case TERRAIN -> new TerrainParser().parse(app, chunk, configLoader);
            case LIGHTING -> new LightingParser().parse(app, chunk, configLoader);
            default -> new Node("CustomChunk-" + chunk.getId());
        };
    }

    private void safeCallback(Consumer<SceneReadyContext> cb, SceneReadyContext ctx) {
        try {
            cb.accept(ctx);
        } catch (Exception ex) {
            logger.warn("onSceneReady callback failed", ex);
        }
    }

    public void onSceneReady(Consumer<SceneReadyContext> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        onSceneReadyCallbacks.add(callback);
        if (sceneReady && readyContext != null) {
            app.enqueue(() -> safeCallback(callback, readyContext));
        }
    }

    @Override
    protected void updateModule(float tpf) {
        // Future ECS updates could go here
    }

    @Override
    protected void cleanupModule(Application app) throws Exception {
        logger.debug("SceneModule: cleaning up...");
        if (sceneRoot != null && sceneRoot.getParent() != null) {
            Node toDetach = sceneRoot;
            sceneRoot = null;
            app.enqueue(() -> {
                toDetach.getParent().detachChild(toDetach);
                return null;
            });
        }
        if (streamingManager != null) {
            streamingManager.shutdown();
            streamingManager = null;
        }
        resetState();
        ModuleHealthMonitor.getInstance().reportState(getName(), ModuleState.CLEANED_UP);
    }

    @Override
    protected void onConfigReloaded() throws Exception {
        logger.debug("SceneModule: config reloaded, restarting...");
        cleanupModule(app);
        initModule(app);
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}

    public static class SceneReadyContext {
        public final Node sceneRoot;
        public final CGSMetadata metadata;
        public final List<ChunkEntry> chunkEntries;
        public final SceneEntityContext ecsContext;

        public SceneReadyContext(Node sceneRoot, CGSMetadata metadata, List<ChunkEntry> chunkEntries, SceneEntityContext ecsContext) {
            this.sceneRoot = sceneRoot;
            this.metadata = metadata;
            this.chunkEntries = chunkEntries;
            this.ecsContext = ecsContext;
        }
    }
}
