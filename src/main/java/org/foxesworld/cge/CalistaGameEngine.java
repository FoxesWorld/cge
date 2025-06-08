package org.foxesworld.cge;

import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import org.foxesworld.cge.core.AssetLoader;
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
import org.foxesworld.cge.tmp.OBJ;
import org.foxesworld.cge.tmp.ShapeParty;
import org.foxesworld.cge.tmp.obj.OBJImporter;
import org.foxesworld.cge.ui.UIModule;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogManager;

import static org.foxesworld.cge.tools.SceneCGSCreator.SceneCgsCreatorFrame.setupTheme;

public class CalistaGameEngine extends SimpleApplication {
    private final Map<String, Texture> textureMap = new HashMap<>();

    private final AssetLoader assetLoader;
    private final PopCycle popCycle;
    @Deprecated
    private final StreamingManager<String, Byte[]> byteStreamer;
    private ModuleManager moduleManager;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;
    private SceneModule scene;


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
    }

    public CalistaGameEngine(){
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
        this.assetLoader = new AssetLoader(this);
        this.popCycle = new PopCycle(this);
        this.configService = new ConfigService();
        this.taskScheduler = new TaskScheduler();
        //this.obj = new OBJ(this);

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

    @Override
    public void simpleInitApp() {
        stateManager.getState(StatsAppState.class).setDisplayStatView(false);
        moduleManager = new ModuleManager(this);
        assetLoader.loadTextures();
        moduleManager.register(new RendererModule(this), 20);
        moduleManager.register(new PhysicsModule(this), 35);
        moduleManager.register(new SceneModule(this), 10);
        //moduleManager.register(new UIModule(this), 5);
        moduleManager.initializeAll(this);
        moduleManager.loadAll(this, () -> {
            this.assetManager.registerLoader(OBJImporter.class, "obj");
            scene = moduleManager.getModule(SceneModule.class);


            this.moduleManager.getModule(SceneModule.class).onSceneReady(() ->{
                PhysicsModule physicsModule = this.getModuleManager().getModule(PhysicsModule.class);

                if (physicsModule != null) {
                    physicsModule.getBulletAppState().getPhysicsSpace().addCollisionListener(new CollisionParticleEmitter(this));
                }
                createTestTerrain();
                ShapeParty cubeDerp = new ShapeParty(this);
                cubeDerp.startParty();
                Player player = new Player(this, new Vector3f(0,20,0));
                rootNode.attachChild(player);
            });

        });
    }

    private void createTestTerrain() {
        Material mat = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
        mat.setTexture("BaseColorMap", textureMap.get("calista_grid_test"));
        mat.setTexture("RoughnessMap", textureMap.get("calista_grid_test_normal"));
        mat.setBoolean("BackfaceShadows", false);
        mat.setFloat("EmissivePower", 3.0f);
        mat.setFloat("EmissiveIntensity", 2.0f);
        mat.setFloat("ParallaxHeight", 0.05f);
        mat.setFloat("NormalType", -1.0f);
        mat.setFloat("Glossiness", 1.0f);
        mat.getTextureParam("BaseColorMap").getTextureValue().setWrap(Texture.WrapMode.Repeat);

        float width = 100f, height = 100f;
        Quad quad = new Quad(width, height);
        Geometry terrain = new Geometry("TerrainPlane", quad);
        terrain.getMesh().scaleTextureCoordinates(new Vector2f(8, 8));
        terrain.setLocalTranslation(-width / 2f, 0, height / 2f);
        terrain.rotate(-FastMath.HALF_PI, 0, 0);
        terrain.setMaterial(mat);
        terrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        enqueue(() -> {
            rootNode.attachChild(terrain);
            MeshCollisionShape shape = new MeshCollisionShape(quad);
            RigidBodyControl rbc = new RigidBodyControl(shape, 0f);
            terrain.addControl(rbc);
            PhysicsModule phys = moduleManager.getModule(PhysicsModule.class);
            if (phys != null) {
                phys.getBulletAppState().getPhysicsSpace().add(rbc);
            }
            return null;
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

    public void  addTexture(String name, Texture texture2D){
        this.textureMap.put(name, texture2D);
    }

    public Map<String, Texture> getTextureMap() {
        return textureMap;
    }

    public Texture getTexture(String name){
        Texture texture = textureMap.get(name);
        if(texture != null) {
            return texture;
        } else {
            System.out.println("not found texture " + name);
            return  new Texture2D();
        }
    }

    public SceneModule getScene() {
        return scene;
    }

    @Override
    public void simpleUpdate(float tpf) {
        this.moduleManager.update(tpf);
    }
}
