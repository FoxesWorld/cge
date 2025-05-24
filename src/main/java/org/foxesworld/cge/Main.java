package org.foxesworld.cge;

import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import org.foxesworld.cge.core.EngineModule;
import org.foxesworld.cge.core.ModuleManager;
import org.foxesworld.cge.renderer.RendererModule;

public class Main extends SimpleApplication {

    private ModuleManager moduleManager;

    public static void main(String[] args) {
        Main app = new Main();
        app.start();
    }

    public Main(){
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
    }

    @Override
    public void simpleInitApp() {
        // Инициализация менеджера модулей
        moduleManager = new ModuleManager(stateManager);
        moduleManager.register(new RendererModule(this), 10);
        moduleManager.initializeAll(this);

        // Настройка маппинга для клавиши F5
        inputManager.addMapping("ReloadConfig", new KeyTrigger(KeyInput.KEY_F5));
        inputManager.addListener(actionListener, "ReloadConfig");
    }

    private final ActionListener actionListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean isPressed, float tpf) {
            if ("ReloadConfig".equals(name) && isPressed) {
                for (EngineModule<?> module : moduleManager.getModules()) {
                    module.reloadConfig();
                }
            }
        }
    };

}
