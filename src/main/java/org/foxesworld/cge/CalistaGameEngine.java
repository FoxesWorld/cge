package org.foxesworld.cge;

import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import jme3utilities.debug.AxesVisualizer;
import jme3utilities.debug.Dumper;
import org.foxesworld.cge.core.file.extensions.ydd.DrawableEntry;
import org.foxesworld.cge.core.file.extensions.ydd.YDDFile;
import org.foxesworld.cge.core.loader.AssetLoader;
import org.foxesworld.cge.core.AssetRepo;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.io.GenericByteParser;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.core.streaming.StreamingManager;
import org.foxesworld.cge.core.streaming.StreamingParserLoader;
import org.foxesworld.cge.physics.PhysicsModule;
import org.foxesworld.cge.player.Player;
import org.foxesworld.cge.popcycle.PopCycle;
import org.foxesworld.cge.renderer.RendererModule;
import org.foxesworld.cge.scene.SceneModule;
import org.foxesworld.cge.tmp.CollisionParticleEmitter;
import org.foxesworld.cge.tmp.ShapeParty;
import org.foxesworld.cge.tmp.obj.OBJImporter;
import org.foxesworld.cge.ui.UIModule;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

import static org.foxesworld.cge.tmp.Terrain.createTestTerrain;
import static org.foxesworld.cge.tools.SceneCGSCreator.SceneCgsCreatorFrame.setupTheme;

public class CalistaGameEngine extends SimpleApplication {
    //private final Map<String, Texture> textureMap = new HashMap<>();

    private AssetRepo assetRepo;

    private AssetLoader assetLoader;
    private final PopCycle popCycle;
    @Deprecated
    private final StreamingManager<String, Byte[]> byteStreamer;
    private ModuleManager moduleManager;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;
    private SceneModule scene;


    /*
    public static void main(String[] args) {
        CalistaGameEngine app = new CalistaGameEngine();
        setupTheme("theme/calista.properties");

        AppSettings settings = new AppSettings(false);
        settings.setTitle("Calista Game Engine");
        settings.setSettingsDialogImage("/theme/logo.png");
        settings.setFrameRate(-1);
        try (InputStream icoStream = CalistaGameEngine.class.getClassLoader().getResourceAsStream("theme/icon/engineLogo.ico")) {
            ICOParser icoParser = new ICOParser();
            BufferedImage bestIcon = icoParser.getBestIcon(icoParser.parse(icoStream));
            settings.setIcons(new BufferedImage[]{bestIcon});
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to load .ico icon file.");
        }

        app.setSettings(settings);
        app.start();
    } */

    public CalistaGameEngine(){
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
        this.assetRepo = new AssetRepo(this);
        //this.yddTest();

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
        this.byteStreamer = new StreamingManager<>(
                loader::load,
                true,
                0
        );
    }

    private void yddTest(){
        YDDFile yddFile = new YDDFile(new File("data/skydome.ydd"), "r");
        try {
            yddFile.readFileNew();
            for(DrawableEntry entry: yddFile.getDrawables()) {
                System.out.println(entry.name);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void simpleInitApp() {
        stateManager.getState(StatsAppState.class).setDisplayStatView(false);
        moduleManager = new ModuleManager(this);

        this.assetLoader = new AssetLoader(this);
        moduleManager.register(new RendererModule(this), 20);
        moduleManager.register(new PhysicsModule(this), 35);
        moduleManager.register(new SceneModule(this), 10);

        moduleManager.initializeAll(this);
        moduleManager.loadAll(this, () -> {
            this.assetManager.registerLoader(OBJImporter.class, "obj");
            scene = moduleManager.getModule(SceneModule.class);

            assetLoader.loadAllAssets(() -> {
                moduleManager.register(new UIModule(this), 5);

                createTestTerrain(this, 250f, 250f);
                ShapeParty cubeDerp = new ShapeParty(this);
                cubeDerp.startParty();
                Player player = new Player(this, new Vector3f(0,20,0));
                rootNode.attachChild(player);
            });

            this.moduleManager.getModule(SceneModule.class).onSceneReady(() ->{
                PhysicsModule physicsModule = this.getModuleManager().getModule(PhysicsModule.class);

                if (physicsModule != null) {
                    physicsModule.getBulletAppState().getPhysicsSpace().addCollisionListener(new CollisionParticleEmitter(this));
                }
            });

        });
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

    @Override
    public AssetManager getAssetManager() {
        return assetManager;
    }

    public AssetRepo getAssetRepo() {
        return assetRepo;
    }

    @Override
    public void simpleUpdate(float tpf) {
        this.moduleManager.update(tpf);
    }
}