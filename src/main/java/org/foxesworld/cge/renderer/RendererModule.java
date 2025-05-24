// RendererModule.java
package org.foxesworld.cge.renderer;

import org.foxesworld.cge.core.ModuleManager;
import org.foxesworld.cge.core.EngineModule;
import com.jme3.app.Application;
import org.foxesworld.cge.renderer.camera.CameraModule;
import org.foxesworld.cge.renderer.lighting.LightingModule;
import org.foxesworld.cge.renderer.postProcessing.PostProcessingModule;

/**
 * Универсальный модуль рендеринга, агрегирует камеры, свет и пост-обработку.
 */
public class RendererModule extends EngineModule<Void> {
    private static final String CONFIG_FILE = null;

    private final ModuleManager subManager;

    public RendererModule(Application application) {
        super(CONFIG_FILE, Void.class);
        subManager = new ModuleManager(application.getStateManager());
        // Регистрируем сабмодули с приоритетом
        subManager.register(new CameraModule(), 10);
        subManager.register(new LightingModule(), 20);
        subManager.register(new PostProcessingModule(), 30);
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }

    @Override
    protected void onConfigReloaded() {
    }

    @Override
    protected void initModule(Application app) {
        subManager.initializeAll(app);
    }

    @Override
    protected void updateModule(float tpf) {
        // Всё обновляется через AppState
    }

    @Override
    protected void cleanupModule(Application app) {
        // Подмодули очистят себя
    }
}
