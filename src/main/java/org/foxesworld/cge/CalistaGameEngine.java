package org.foxesworld.cge;

import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.awt.AWTErrorDialog;
import com.jme3.post.FilterPostProcessor;
import org.foxesworld.cge.core.AssetRepo;
import org.foxesworld.cge.core.ConfigEditorState;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.io.FpsCounterState;
import org.foxesworld.cge.core.io.TTFrenderer;
import org.foxesworld.cge.core.io.GenericByteParser;
import org.foxesworld.cge.core.loader.AssetLoader;
import org.foxesworld.cge.core.io.progressBar.StatusProgressBar;
import org.foxesworld.cge.core.material.MaterialManager;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.core.io.streaming.ByteBoxingUtils;
import org.foxesworld.cge.core.io.streaming.StreamingManager;
import org.foxesworld.cge.core.io.streaming.StreamingParserLoader;
import org.foxesworld.cge.core.sound.SoundManager;
import org.foxesworld.cge.modules.ModuleConfig;
import org.foxesworld.cge.modules.ecs.ECSModule;
import org.foxesworld.cge.modules.ecs.systems.PhysicsSystem;
import org.foxesworld.cge.modules.ecs.systems.SceneGraphSystem;
import org.foxesworld.cge.modules.physics.PhysicsModule;
import org.foxesworld.cge.modules.popcycle.PopCycle;
import org.foxesworld.cge.modules.renderer.GpuInfo;
import org.foxesworld.cge.modules.ui.novaUi.NovaUI;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Comparator;
import java.util.List;

import static org.foxesworld.cge.core.loadingLogos.LoadingAppState.buildLogoChainFromJson;
import static test.Game.setupTheme;


/**
 * Main game engine class with dynamic module loading
 */
public class CalistaGameEngine extends SimpleApplication {
    public static CalistaGameEngine INSTANCE;
    private FilterPostProcessor filterPostProcessor;
    private final List<ModuleConfig> modulesToLoad;
    private final AssetRepo assetRepo;
    private final PopCycle popCycle;
    private TTFrenderer TTFrenderer;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;
    private final StreamingManager<String, Byte[]> byteStreamer;

    private AssetLoader assetLoader;
    private MaterialManager materialManager;
    private SoundManager soundManager;
    private ECSModule ecsModule;
    private ModuleManager moduleManager;
    private NovaUI novaUI;

    /**
     * Constructs a new CalistaGameEngine with the provided list of modules to load.
     *
     * @param modulesToLoad list of module configurations to load
     */
    public CalistaGameEngine(List<ModuleConfig> modulesToLoad) throws Exception {
        this.modulesToLoad = modulesToLoad;
        INSTANCE = this;
        setupTheme("assets/theme/calista.properties");
        // Configure logging
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        java.util.logging.LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
        this.assetRepo = new AssetRepo(this);
        this.popCycle = new PopCycle(this);
        this.configService = new ConfigService(this);
        this.taskScheduler = new TaskScheduler();


        // 4. Остальной ваш инициализационный код
        GenericByteParser<Byte[]> parser = new GenericByteParser<>(ByteBoxingUtils::toObject);
        StreamingParserLoader<Byte[]> loader = new StreamingParserLoader<>(parser);
        this.byteStreamer = new StreamingManager<>(loader::load, true, 0);
    }

    @Override
    public void simpleInitApp() {
        StatsAppState stats = stateManager.getState(StatsAppState.class);
        if (stats != null) {
            stats.setDisplayStatView(false);
            setDisplayFps(false);
            FpsCounterState fpsState = new FpsCounterState();
            stateManager.attach(fpsState);
        }

        // Initialize modules
        System.out.println("\n" + new GpuInfo(renderer).formatGpuInfo());
        this.moduleManager = new ModuleManager(this);
        this.assetLoader = new AssetLoader(this);
        this.materialManager = new MaterialManager(this);
        this.soundManager = new SoundManager(assetManager);
        this.soundManager.loadFromJsonResource("sounds.json");
        this.soundManager.preloadAll(true);
        filterPostProcessor = new FilterPostProcessor(getAssetManager());
        stateManager.attach(buildLogoChainFromJson(getAssetManager(), "ui/logos.json"));
        this.TTFrenderer = new TTFrenderer(assetManager);
        assetManager.registerLoader(com.atr.jme.font.asset.TrueTypeLoader.class, "ttf");
    }

    /**
     * Called by the ConfigService when a configuration file has been updated via the UI.
     * This method finds the corresponding module and triggers its reload mechanism.
     *
     * @param configFileName The name of the config file that was changed.
     */
    public void onConfigReloaded(String configFileName) {
        System.out.println("Received reload request for config: " + configFileName);
        EngineModule<?> moduleToReload = moduleManager.getModuleByConfigFile(configFileName);
        if (moduleToReload != null) {
            System.out.println("Reloading module: " + moduleToReload.getClass().getSimpleName());
            try {
                moduleToReload.onConfigReloaded();
            } catch (Exception e) {
                System.err.println("Exception while reloading module " + moduleToReload.getClass().getSimpleName());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } else {
            System.err.println("No module found for config file: " + configFileName);
        }
    }

    @Override
    public void handleError(String errMsg, Throwable t) {
        t.printStackTrace();
        AWTErrorDialog.showDialog(t);
        stop();
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

    public AssetRepo getAssetRepo() {
        return assetRepo;
    }

    public MaterialManager getMaterialManager() {
        return materialManager;
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

    public TTFrenderer getFontFactory() {
        return TTFrenderer;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public void startGameFromMenu() {
        stateManager.detach(stateManager.getState(MainMenuAppState.class));
        modulesToLoad.stream().sorted(Comparator.comparingInt(ModuleConfig::getPriority)).forEach(cfg -> moduleManager.register(cfg.create(this), cfg.getPriority()));

        moduleManager.loadAll(this, () -> {
            // Register custom importers
            //this.assetManager.registerLoader(OBJImporter.class, "obj");
            //this.assetManager.registerLoader(FBXImporter.class, "fbx");

            //this.scene = moduleManager.getModule(SceneModule.class);

            //ECS System
            this.ecsModule = moduleManager.getModule(ECSModule.class);
            ecsModule.addSystem(new PhysicsSystem(moduleManager.getModule(PhysicsModule.class)));
            ecsModule.addSystem(new SceneGraphSystem(this.getRootNode()));

            // Load assets
            assetLoader.loadAllAssets(new StatusProgressBar());
            stateManager.attach(new ConfigEditorState(configService));
            //assetLoader.onAssetsLoaded(() -> {
             //   ShapeParty spawner = new ShapeParty(this);
             //   spawner.startParty();
            //});
        });
    }
}