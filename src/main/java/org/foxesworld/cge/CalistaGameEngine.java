package org.foxesworld.cge;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.system.AppSettings;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;
import org.foxesworld.cge.physics.PhysicsModule;
import org.foxesworld.cge.renderer.RendererModule;
import org.foxesworld.cge.scene.SceneModule;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Map;
import java.util.logging.LogManager;

import static org.foxesworld.cge.tools.SceneCreator.SceneCgsCreatorFrame.setupTheme;

public class CalistaGameEngine extends SimpleApplication {

    private ModuleManager moduleManager;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;


    public static void main(String[] args) {
        CalistaGameEngine app = new CalistaGameEngine();
        setupTheme("theme/calista.properties");
        // Настройка окна (Calista Style)
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Calista Game Engine");
        settings.setResolution(1280, 720); // Примерное разрешение
        settings.setSamples(4); // Сглаживание
        settings.setVSync(true);
        settings.setFrameRate(165);
        settings.setFullscreen(false);
        settings.setResizable(true);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    public CalistaGameEngine(){
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
        this.configService = new ConfigService();
        this.taskScheduler = new TaskScheduler();
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
}
