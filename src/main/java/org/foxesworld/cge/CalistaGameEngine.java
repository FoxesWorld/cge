package org.foxesworld.cge;

import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.asset.AssetManager;
import com.jme3.phonon.*;
import com.jme3.phonon.desktop_javasound.JavaSoundPhononSettings;
import com.jme3.post.FilterPostProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.AssetRepo;
import org.foxesworld.cge.core.ConfigEditorState;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.io.GenericByteParser;
import org.foxesworld.cge.core.loader.AssetLoader;
import org.foxesworld.cge.core.loader.JmeProgressBar;
import org.foxesworld.cge.core.material.MaterialManager;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.core.io.streaming.ByteBoxingUtils;
import org.foxesworld.cge.core.io.streaming.StreamingManager;
import org.foxesworld.cge.core.io.streaming.StreamingParserLoader;
import org.foxesworld.cge.importers.fbx.FBXImporter;
import org.foxesworld.cge.importers.obj.OBJImporter;
import org.foxesworld.cge.modules.ModuleConfig;
import org.foxesworld.cge.modules.ecs.ECSModule;
import org.foxesworld.cge.modules.physics.PhysicsModule;
import org.foxesworld.cge.modules.popcycle.PopCycle;
import org.foxesworld.cge.modules.renderer.GpuInfo;
import org.foxesworld.cge.modules.renderer.RendererModule;
import org.foxesworld.cge.modules.renderer.postProcessing.PostProcessingModule;
import org.foxesworld.cge.modules.scene.SceneModule;
import org.foxesworld.cge.modules.sound.SoundModule;
import org.foxesworld.cge.tmp.CollisionParticleEmitter;
import org.foxesworld.cge.tmp.ShapeParty;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Comparator;
import java.util.List;

import static com.jme3.audio.AudioContext.setAudioRenderer;

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
    private MaterialManager materialManager;
    private ECSModule ecsModule;
    private SceneModule scene;
    private SoundModule soundModule;
    private ModuleManager moduleManager;

    /**
     * Constructs a new CalistaGameEngine with the provided list of modules to load.
     *
     * @param modulesToLoad list of module configurations to load
     */
    public CalistaGameEngine(List<ModuleConfig> modulesToLoad) throws Exception {
        this.modulesToLoad = modulesToLoad;

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
        // Disable stats view
        StatsAppState stats = stateManager.getState(StatsAppState.class);
        if (stats != null) {
            stats.setDisplayStatView(false);
        }

        // Initialize modules
        System.out.println("\n" + new GpuInfo(renderer).formatGpuInfo());
        this.moduleManager = new ModuleManager(this);
        this.assetLoader = new AssetLoader(this);
        this.materialManager = new MaterialManager(this);
        filterPostProcessor = new FilterPostProcessor(getAssetManager());
        
        modulesToLoad.stream().sorted(Comparator.comparingInt(ModuleConfig::getPriority)).forEach(cfg -> moduleManager.register(cfg.create(this), cfg.getPriority()));
        moduleManager.loadAll(this, () -> {
            // Register custom importers
            this.assetManager.registerLoader(OBJImporter.class, "obj");
            //this.assetManager.registerLoader(FBXImporter.class, "fbx");

            this.scene = moduleManager.getModule(SceneModule.class);
            this.ecsModule = moduleManager.getModule(ECSModule.class);
            this.soundModule = moduleManager.getModule(SoundModule.class);
            // Load assets
            assetLoader.loadAllAssets(new JmeProgressBar(this));
            stateManager.attach(new ConfigEditorState(configService));
            assetLoader.onAssetsLoaded(() -> new ShapeParty(this).startParty());
        });
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

    public SoundModule getSoundModule() {
        return soundModule;
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
}