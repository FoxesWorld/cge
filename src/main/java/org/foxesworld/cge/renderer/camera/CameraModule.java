package org.foxesworld.cge.renderer.camera;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.EngineModule;
import com.jme3.app.Application;

import java.nio.file.Paths;
public class CameraModule extends EngineModule<CameraConfig> {
    private static final Logger log = LogManager.getLogger(CameraModule.class);
    private static final String CONFIG_FILE = "camera_config.json";

    public CameraModule() {
        super(CONFIG_FILE, CameraConfig.class);
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }

    @Override
    protected void onConfigReloaded() {
        log.info("CameraConfig reloaded: {}", config);
    }

    @Override
    protected void initModule(Application app) {
        setupCamera((com.jme3.app.SimpleApplication) app);
    }

    @Override
    protected void updateModule(float tpf) {
        // можно добавить динамику камеры
    }

    @Override
    protected void cleanupModule(Application app) {
        // ничего не требуется
    }

    private void setupCamera(com.jme3.app.SimpleApplication app) {
        app.getCamera().setFrustumPerspective(
                config.fov,
                (float) config.resolutionWidth / config.resolutionHeight,
                config.nearClip,
                config.farClip
        );
        app.getFlyByCamera().setMoveSpeed(config.moveSpeed);
        app.getFlyByCamera().setEnabled(true);
    }
}
