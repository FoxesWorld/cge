package org.foxesworld.cge;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.light.AmbientLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.util.SkyFactory;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.io.GenericByteParser;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.core.streaming.StreamingManager;
import org.foxesworld.cge.core.streaming.StreamingParserLoader;
import org.foxesworld.cge.physics.PhysicsModule;
import org.foxesworld.cge.renderer.RendererModule;
import org.foxesworld.cge.scene.SceneModule;
import org.foxesworld.cge.tmp.CubeDerp;
import org.foxesworld.cge.tmp.TextureLoader;
import org.slf4j.bridge.SLF4JBridgeHandler;
import jme3utilities.sky.SkyControl;
import jme3utilities.sky.StarsOption;
import jme3utilities.sky.Updater;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogManager;

import static org.foxesworld.cge.tools.SceneCGSCreator.SceneCgsCreatorFrame.setupTheme;

public class CalistaGameEngine extends SimpleApplication {
    private final Map<String, Texture> textureMap = new HashMap<>();

    @Deprecated
    private final StreamingManager<String, Byte[]> byteStreamer;
    private ModuleManager moduleManager;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;
    private final TextureLoader textureLoader;
    private SceneModule scene;


    public static void main(String[] args) {
        CalistaGameEngine app = new CalistaGameEngine();
        setupTheme("theme/calista.properties");
        // Настройка окна (Calista Style)
        AppSettings settings = new AppSettings(false);
        settings.setTitle("Calista Game Engine");
        settings.setSettingsDialogImage("/theme/logo.png");
        settings.setResolution(1280, 720); // Примерное разрешение
        settings.setSamples(4); // Сглаживание
        settings.setVSync(false);
        settings.setFrameRate(-1);
        settings.setFullscreen(false);
        settings.setResizable(true);
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
        this.configService = new ConfigService();
        this.taskScheduler = new TaskScheduler();
        textureLoader = new TextureLoader(this);

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
        moduleManager = new ModuleManager(this);
        moduleManager.register(new RendererModule(this), 20);
        moduleManager.register(new PhysicsModule(this), 35);
        moduleManager.register(new SceneModule(this), 10);
        moduleManager.initializeAll(this);
        moduleManager.loadAll(this, () -> {


            enqueue(() -> {
            Spatial sky = SkyFactory.createSky(assetManager,
                    textureMap.get("cubemap_0"),
                    SkyFactory.EnvMapType.CubeMap);
            sky.setShadowMode(RenderQueue.ShadowMode.Off);
            SkyControl skyControl = new SkyControl(assetManager, cam, .5f, StarsOption.TopDome, true);
            rootNode.addControl(skyControl);
            skyControl.setCloudiness(0.8f);
            skyControl.setCloudsYOffset(0.4f);
            skyControl.setTopVerticalAngle(1.78f);
            skyControl.getSunAndStars().setHour(10);
            Updater updater = skyControl.getUpdater();
            updater.setAmbientLight(new AmbientLight(ColorRGBA.DarkGray));
            //updater.setMainLight(sun);
            //updater.addShadowRenderer(dlsr);
            skyControl.setEnabled(true);
        });
            scene = moduleManager.getModule(SceneModule.class);
            Material mat = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
            mat.setTexture("BaseColorMap", textureMap.get("calista_grid_test"));
            mat.setTexture("RoughnessMap", textureMap.get("calista_grid_test_normal"));
            mat.setBoolean("BackfaceShadows", false);
            mat.setFloat(        "EmissivePower",  3.0f);
            mat.setFloat("EmissiveIntensity", 2.0f);
            mat.setFloat("ParallaxHeight",  0.05f);
            mat.setFloat("NormalType", -1.0f);
            mat.setFloat("Glossiness",  1.0f);

            // ——— Геометрия для теста ———
            float width = 100f, height = 100f;
            Quad quad = new Quad(width, height);
            Geometry terrain = new Geometry("TerrainPlane", quad);
            terrain.setLocalTranslation(-width/2f, 0, height/2f);
            terrain.rotate(-FastMath.HALF_PI, 0, 0);
            terrain.setMaterial(mat);

            enqueue(() -> {
                getRootNode().attachChild(terrain);
                MeshCollisionShape shape = new MeshCollisionShape(quad);
                RigidBodyControl rbc = new RigidBodyControl(shape, 0f);
                terrain.addControl(rbc);
                PhysicsModule phys = getModuleManager().getModule(PhysicsModule.class);
                if (phys != null) {
                    phys.getBulletAppState().getPhysicsSpace().add(rbc);
                }
            });

            // ——— Камера сверху ———
            float camHeight = 5f;
            float camX = 0f;
            float camZ = 0f;
            cam.setLocation(new Vector3f(camX, camHeight, camZ));
            cam.lookAt(new Vector3f(camX, 0f, camZ), Vector3f.UNIT_Y);

            CubeDerp cubeDerp = new CubeDerp(this);
            cubeDerp.startParty();
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

    public TextureLoader getTextureLoader() {
        return textureLoader;
    }

    public SceneModule getScene() {
        return scene;
    }


}
