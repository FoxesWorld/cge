package org.foxesworld.cge.renderer.camera;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import com.jme3.app.Application;
import org.foxesworld.cge.renderer.RendererModule;

public class CameraModule extends EngineModule<CameraConfig> {
    private static final Logger log = LogManager.getLogger(CameraModule.class);
    private static final String CONFIG_FILE = "camera_config";

    public CameraModule(CalistaGameEngine calistaGameEngine) {
        super(CONFIG_FILE, CameraConfig.class, calistaGameEngine);
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }

    @Override
    protected void onConfigReloaded() {
        log.info("CameraConfig reloaded: {}", getConfig());
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        setupCamera(app);
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
                getConfig().fov,
                (float) getConfig().resolutionWidth / getConfig().resolutionHeight,
                getConfig().nearClip,
                getConfig().farClip
        );
        app.getFlyByCamera().setMoveSpeed(getConfig().moveSpeed);
        app.getFlyByCamera().setEnabled(true);
    }
}
