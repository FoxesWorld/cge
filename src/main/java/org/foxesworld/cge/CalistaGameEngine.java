package org.foxesworld.cge;

import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.system.AppSettings;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.io.GenericByteParser;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.core.streaming.StreamingManager;
import org.foxesworld.cge.core.streaming.StreamingParserLoader;
import org.foxesworld.cge.physics.PhysicsModule;
import org.foxesworld.cge.renderer.RendererModule;
import org.foxesworld.cge.scene.SceneModule;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;

import static org.foxesworld.cge.tools.SceneCreator.SceneCgsCreatorFrame.setupTheme;

public class CalistaGameEngine extends SimpleApplication {

    private StreamingManager<String, Byte[]> byteStreamer;
    private ModuleManager moduleManager;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;


    public static void main(String[] args) {
        CalistaGameEngine app = new CalistaGameEngine();
        setupTheme("theme/calista.properties");
        // Настройка окна (Calista Style)
        AppSettings settings = new AppSettings(false);
        settings.setTitle("Calista Game Engine");
        settings.setSettingsDialogImage("/theme/logo.png");
        settings.setResolution(1280, 720); // Примерное разрешение
        settings.setSamples(4); // Сглаживание
        settings.setVSync(true);
        settings.setFrameRate(165);
        settings.setFullscreen(false);
        settings.setResizable(true);
        try (InputStream icoStream = CalistaGameEngine.class.getClassLoader().getResourceAsStream("theme/icon/favicon.ico")) {

            ICOParser icoParser = new ICOParser();
            List<BufferedImage> iconsList = icoParser.parse(icoStream);
            BufferedImage bestIcon = icoParser.getBestIcon(iconsList);

            int width = bestIcon.getWidth();
            int height = bestIcon.getHeight();
            int pixelSize = bestIcon.getColorModel().getPixelSize();
            int numBands = bestIcon.getRaster().getNumBands();
            int imageType = bestIcon.getType();
            System.out.printf("Selected Icon: %dx%d px, %d bit, %d bands, type=%d%n", width, height, pixelSize, numBands, imageType);
            settings.setIcons(new BufferedImage[]{bestIcon});

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to load .ico icon file.");
        }

        app.setSettings(settings);
        //app.setShowSettings(false);
        app.start();
    }

    public CalistaGameEngine(){
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
        this.configService = new ConfigService();
        this.taskScheduler = new TaskScheduler();
        GenericByteParser<Byte[]> parser = new GenericByteParser<>(bytes -> {
            Byte[] boxed = new Byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                boxed[i] = bytes[i];
            }
            return boxed;
        });

        // ✅ Обёртка для StreamingManager
        StreamingParserLoader<Byte[]> loader = new StreamingParserLoader<>(parser);

        // ✅ Стриминг с кэшем
        this.byteStreamer = new StreamingManager<>(
                loader::load,
                true,
                4
        );
    }

    @Override
    public void simpleInitApp() {
        // Инициализация менеджера модулей
        moduleManager = new ModuleManager(this);
        moduleManager.register(new RendererModule(this), 10);
        moduleManager.register(new PhysicsModule(this), 20);
        moduleManager.register(new SceneModule(this), 10);
        moduleManager.initializeAll(this);

        // Настройка маппинга для клавиши F5
        inputManager.addMapping("ReloadConfig", new KeyTrigger(KeyInput.KEY_F5));
        inputManager.addListener(actionListener, "ReloadConfig");

        Box box = new Box(1f, 1f, 1f);
        Geometry geom = new Geometry("TestBox", box);
        Material mat = new Material(this.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        geom.setMaterial(mat);
        getRootNode().attachChild(geom);

    }

    private final ActionListener actionListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean isPressed, float tpf) {
            if ("ReloadConfig".equals(name) && isPressed) {
                for (Map.Entry<String, EngineModule<?>> module : moduleManager.getInstances().entrySet()) {
                    module.getValue().reloadConfig();
                }
            }
        }
    };

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
}
