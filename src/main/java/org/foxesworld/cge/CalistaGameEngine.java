package org.foxesworld.cge;

import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.asset.AssetManager;
import com.jme3.post.FilterPostProcessor;
import org.foxesworld.cge.core.AssetRepo;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.io.GenericByteParser;
import org.foxesworld.cge.core.loader.AssetLoader;
import org.foxesworld.cge.core.loader.JmeProgressBar;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.core.io.streaming.ByteBoxingUtils;
import org.foxesworld.cge.core.io.streaming.StreamingManager;
import org.foxesworld.cge.core.io.streaming.StreamingParserLoader;
import org.foxesworld.cge.importers.fbx.FBXImporter;
import org.foxesworld.cge.importers.obj.OBJImporter;
import org.foxesworld.cge.modules.ModuleConfig;
import org.foxesworld.cge.modules.ecs.ECSModule;
import org.foxesworld.cge.modules.popcycle.PopCycle;
import org.foxesworld.cge.modules.scene.SceneModule;
import org.foxesworld.cge.tmp.ShapeParty;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Comparator;
import java.util.List;
import java.util.logging.LogManager;

/**
 * Main game engine class with dynamic module loading
 */
public class CalistaGameEngine extends SimpleApplication {
    private FilterPostProcessor filterPostProcessor;
    private final List<ModuleConfig> modulesToLoad;
    private final AssetRepo assetRepo;
    private final PopCycle popCycle;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;
    private final StreamingManager<String, Byte[]> byteStreamer;

    private AssetLoader assetLoader;
    private ECSModule ecsModule;
    private SceneModule scene;
    private ModuleManager moduleManager;

    /**
     * Constructs a new CalistaGameEngine with the provided list of modules to load.
     *
     * @param modulesToLoad list of module configurations to load
     */
    public CalistaGameEngine(List<ModuleConfig> modulesToLoad) {
        this.modulesToLoad = modulesToLoad;

        // Configure logging
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();

        this.assetRepo = new AssetRepo(this);
        this.popCycle = new PopCycle(this);
        this.configService = new ConfigService();
        this.taskScheduler = new TaskScheduler();


        GenericByteParser<Byte[]> parser = new GenericByteParser<>(ByteBoxingUtils::toObject);
        StreamingParserLoader<Byte[]> loader = new StreamingParserLoader<>(parser);
        this.byteStreamer = new StreamingManager<>(loader::load, true, 0);
    }

    @Override
    public void simpleInitApp() {
        // Disable stats view
        StatsAppState stats = stateManager.getState(StatsAppState.class);
        if (stats != null) {
            stats.setDisplayStatView(false);
        }

        // Initialize modules
        this.moduleManager = new ModuleManager(this);
        this.assetLoader = new AssetLoader(this);
        filterPostProcessor = new FilterPostProcessor(getAssetManager());
        
        modulesToLoad.stream()
                .sorted(Comparator.comparingInt(ModuleConfig::getPriority))
                .forEach(cfg -> moduleManager.register(cfg.create(this), cfg.getPriority()));

        // Initialize and load all modules
        moduleManager.initializeAll(this);
        moduleManager.loadAll(this, () -> {
            // Register custom importers
            OBJImporter importer = new OBJImporter(OBJImporter.UVProjection.AUTO, true, true);
            this.assetManager.registerLoader(OBJImporter.class, "obj");
            //this.assetManager.registerLoader(FBXImporter.class, "fbx");

            this.scene = moduleManager.getModule(SceneModule.class);
            this.ecsModule = moduleManager.getModule(ECSModule.class);

            // Load assets
            assetLoader.loadAllAssets(() -> {
                new ShapeParty(this).startParty();
            }, new JmeProgressBar(this));
        });
    }

    @Override
    public void simpleUpdate(float tpf) {
        this.moduleManager.update(tpf);
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public StreamingManager<String, Byte[]> getByteStreamer() {
        return byteStreamer;
    }

    public PopCycle getPopCycle() {
        return popCycle;
    }

    public SceneModule getScene() {
        return scene;
    }

    public AssetRepo getAssetRepo() {
        return assetRepo;
    }

    public AssetLoader getAssetLoader() {
        return assetLoader;
    }

    public ECSModule getEcsModule() {
        return ecsModule;
    }

    public FilterPostProcessor getFilterPostProcessor() {
        return filterPostProcessor;
    }
}