package org.foxesworld.cge;

import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.asset.AssetManager;
import org.foxesworld.cge.core.AssetRepo;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.io.GenericByteParser;
import org.foxesworld.cge.core.loader.AssetLoader;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.core.streaming.StreamingManager;
import org.foxesworld.cge.core.streaming.StreamingParserLoader;
import org.foxesworld.cge.importers.fbx.FBXImporter;
import org.foxesworld.cge.importers.obj.OBJImporter;
import org.foxesworld.cge.modules.ModuleConfig;
import org.foxesworld.cge.modules.popcycle.PopCycle;
import org.foxesworld.cge.modules.scene.SceneModule;
import org.foxesworld.cge.tmp.ShapeParty;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Comparator;
import java.util.List;
import java.util.logging.LogManager;

import static org.foxesworld.cge.tmp.Terrain.createTestTerrain;

/**
 * Main game engine class with dynamic module loading
 */
public class CalistaGameEngine extends SimpleApplication {
    private final List<ModuleConfig> modulesToLoad;
    private AssetRepo assetRepo;
    private AssetLoader assetLoader;
    private final PopCycle popCycle;
    @Deprecated
    private final StreamingManager<String, Byte[]> byteStreamer;
    private ModuleManager moduleManager;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;
    private SceneModule scene;

    public CalistaGameEngine(List<ModuleConfig> modulesToLoad) {
        this.modulesToLoad = modulesToLoad;

        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
        this.assetRepo = new AssetRepo(this);

        this.popCycle = new PopCycle(this);
        this.configService = new ConfigService();
        this.taskScheduler = new TaskScheduler();

        GenericByteParser<Byte[]> parser = new GenericByteParser<>(bytes -> {
            Byte[] boxed = new Byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                boxed[i] = bytes[i];
            }
            return boxed;
        });

        StreamingParserLoader<Byte[]> loader = new StreamingParserLoader<>(parser);
        this.byteStreamer = new StreamingManager<>(loader::load, true, 0);
    }

    @Override
    public void simpleInitApp() {
        stateManager.getState(StatsAppState.class).setDisplayStatView(false);
        moduleManager = new ModuleManager(this);
        this.assetLoader = new AssetLoader(this);

        // Sort modules by priority and register them
        modulesToLoad.stream()
                .sorted(Comparator.comparingInt(ModuleConfig::getPriority))
                .forEach(cfg -> moduleManager.register(cfg.create(this), cfg.getPriority()));

        moduleManager.initializeAll(this);
        moduleManager.loadAll(this, () -> {
            this.assetManager.registerLoader(OBJImporter.class, "obj");
            this.assetManager.registerLoader(FBXImporter.class, "fbx");
            scene = moduleManager.getModule(SceneModule.class);

            assetLoader.loadAllAssets(() -> {
                createTestTerrain(this, 250f, 250f);
                new ShapeParty(this).startParty();
            });

            /*
            if (scene != null) {
                scene.onSceneReady(() -> {
                    PhysicsModule physicsModule = getModuleManager().getModule(PhysicsModule.class);
                    if (physicsModule != null) {
                        physicsModule.getBulletAppState().getPhysicsSpace()
                                .addCollisionListener(new CollisionParticleEmitter(this));
                    }
                });
            } */
        });
    }

    public ConfigService getConfigService() { return configService; }
    public TaskScheduler getTaskScheduler() { return taskScheduler; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public StreamingManager<String, Byte[]> getByteStreamer() { return byteStreamer; }
    public PopCycle getPopCycle() { return popCycle; }
    public SceneModule getScene() { return scene; }

    @Override
    public AssetManager getAssetManager() { return assetManager; }
    public AssetRepo getAssetRepo() { return assetRepo; }

    @Override
    public void simpleUpdate(float tpf) {
        this.moduleManager.update(tpf);
    }

    public AssetLoader getAssetLoader() {
        return assetLoader;
    }
}

// Example of creation and startup:
// List<ModuleConfig> cfg = List.of(
//     new ModuleConfig(engine -> new RendererModule(engine), 20),
//     new ModuleConfig(engine -> new PhysicsModule(engine), 35),
//     new ModuleConfig(engine -> new SceneModule(engine),   10),
//     new ModuleConfig(engine -> new UIModule(engine),        5)
// );
// CalistaGameEngine engine = new CalistaGameEngine(cfg);
// engine.start();
