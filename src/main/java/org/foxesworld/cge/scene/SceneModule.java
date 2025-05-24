package org.foxesworld.cge.scene;

import com.jme3.app.Application;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.cgs.CGSMetadata;
import org.foxesworld.cge.core.cgs.SceneChunk;
import org.foxesworld.cge.core.cgs.ChunkEntry;
import org.foxesworld.cge.core.cgs.parser.ParsedCGSFile;
import org.foxesworld.cge.core.cgs.parser.types.*;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleHealthMonitor;
import org.foxesworld.cge.core.module.ModuleState;
import org.foxesworld.cge.renderer.lighting.LightingModule;
import org.foxesworld.cge.streaming.StreamingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SceneModule extends EngineModule<SceneConfig> {
    private static final Logger logger = LoggerFactory.getLogger(SceneModule.class);

    // scene-specific
    private CGSMetadata cgsMetadata;
    private ParsedCGSFile sceneFile;
    private List<ChunkEntry> entries;

    // generic streaming manager for chunks
    private StreamingManager<Integer, SceneChunk> streamingManager;

    private Node sceneRoot;
    private CalistaGameEngine app;
    private final AtomicInteger chunksRemaining = new AtomicInteger(0);
    private final List<Runnable> onSceneReadyCallbacks = new ArrayList<>();

    public SceneModule(CalistaGameEngine app) {
        super("scene", SceneConfig.class, app);
        this.app = app;
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        // если нужно сбрасывать коллбэки при каждой инициализации:
        // onSceneReadyCallbacks.clear();

        logger.info("SceneModule: initializing...");
        SceneConfig cfg = getConfig();
        if (cfg == null || cfg.getScenePath() == null) {
            throw new IllegalStateException("SceneConfig or scenePath is null");
        }

        File file = new File(cfg.getScenePath());
        if (!file.exists()) {
            throw new IllegalStateException("CGS file not found: " + cfg.getScenePath());
        }

        // 1) Загружаем метаданные сцены
        sceneFile = new ParsedCGSFile(file);
        cgsMetadata = sceneFile.getMetadata();

        logger.info("Loaded CGS file ― magic={}, sceneName={}, version={}, tableOffset={}, chunkCount={}",
                cgsMetadata.magic(),
                cgsMetadata.sceneName(),
                cgsMetadata.version(),
                cgsMetadata.tableOffset(),
                cgsMetadata.chunkCount()
        );
        entries = new ArrayList<>(sceneFile.getChunkEntries());

        // 2) Создаём универсальный StreamingManager для Integer->SceneChunk
        streamingManager = new StreamingManager<>(
                key -> sceneFile.getChunk(key),
                true,
                2
        );

        sceneRoot = new Node(cgsMetadata.sceneName());
        chunksRemaining.set(entries.size());
        logger.info("SceneModule: {} chunks to stream", entries.size());

        // 3) Подготовка коллбэка разбора чанка
        LightingModule lighting = app.getModuleManager().getModule(LightingModule.class);
        Consumer<SceneChunk> onChunk = chunk -> {
            Spatial spat = parseChunk(app, chunk);
            app.enqueue(() -> {
                sceneRoot.attachChild(spat);
                lighting.addLight(spat, 20, 10);
                if (chunksRemaining.decrementAndGet() == 0) {
                    attachSceneRoot();
                }
                return null;
            });
        };
        Consumer<Throwable> onError = err -> {
            logger.error("Failed to stream chunk: {}", err.getMessage(), err);
            if (chunksRemaining.decrementAndGet() == 0) {
                attachSceneRoot();
            }
        };

        // 4) Запускаем асинхронный стриминг
        for (ChunkEntry e : entries) {
            // безопасность на случай, если кто-то вызвал cleanup раньше
            if (streamingManager != null) {
                streamingManager.streamAsync(e.id(), onChunk, onError);
            }
        }
    }

    private Spatial parseChunk(CalistaGameEngine app, SceneChunk chunk) {
        logger.info("Chunk {} data - {}",chunk.getType(), chunk.getData());
        return switch (chunk.getEntry().type()) {
            case GEOMETRY  -> new GeometryParser().parse(app, chunk);
            case TERRAIN   -> new TerrainParser().parse(app, chunk);
            case LIGHTING  -> new LightingParser().parse(app, chunk);
            case PHYSICS   -> new PhysicsParser().parse(app, chunk);
            case MATERIALS -> new Node("MaterialsChunk-" + chunk.getId());
            default        -> new Node("CustomChunk-" + chunk.getId());
        };
    }

    private void attachSceneRoot() {
        app.getRootNode().attachChild(sceneRoot);
        logger.info("All chunks streamed — sceneRoot attached.");
        ModuleHealthMonitor.getInstance().reportState(getName(), ModuleState.RUNNING);
        onSceneReadyCallbacks.forEach(cb -> {
            try { cb.run(); }
            catch (Exception ex) { logger.warn("onSceneReady failed", ex); }
        });
    }

    @Override
    protected void updateModule(float tpf) {
        /* мониторинг прогресса */
    }

    @Override
    protected void cleanupModule(Application app) throws Exception {
        logger.info("SceneModule: cleaning up...");
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
        if (sceneFile != null) {
            try {
                sceneFile.close();
            } catch (Exception e) {
                logger.warn("Error closing sceneFile", e);
            }
            sceneFile = null;
        }
    }

    @Override
    protected void onConfigReloaded() throws Exception {
        logger.info("SceneModule: config reloaded, restarting...");
        cleanupModule(app);
        initModule(app);
    }

    public void onSceneReady(Runnable callback) {
        onSceneReadyCallbacks.add(Objects.requireNonNull(callback));
    }

    public CGSMetadata getMetadata() {
        return cgsMetadata;
    }

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}
}
