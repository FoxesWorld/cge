package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.components.ViceMenuBackground;
import org.foxesworld.cge.tmp.menu.xml.SceneXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main menu state — simplified (removed post-processing effects) and hardened:
 * - no DOF / Bloom filters (removed to lower overhead and avoid platform differences)
 * - robust async background loading
 * - safer cleanup on disable
 * - safer reflection helper for reading nested setting fields
 */
public final class MainMenuAppState extends BaseAppState {

    private static final Logger LOG = LoggerFactory.getLogger(MainMenuAppState.class);

    private static final String MAIN_MENU_XML = "assets/Interface/main_menu.xml";

    // keeping as public/static for backward compat with other code using MainMenuAppState.settingsInstance
    public static Settings settingsInstance;

    private final Class<?> settingsClass;
    private final Path settingsPath = Path.of(new File("settings.json").toURI());

    private XmlMenuBuilder builder;
    private ViceMenuBackground background;
    private MenuScreenHandler screenHandler;

    private CalistaGameEngine calistaGameEngine;

    // async markers
    private final AtomicBoolean backgroundLoading = new AtomicBoolean(false);
    private final AtomicBoolean backgroundReady = new AtomicBoolean(false);

    // lighting handles for cleanup
    private AmbientLight ambientLight;
    private DirectionalLight directionalLight;

    public MainMenuAppState(Class<?> settingsClass) {
        this.settingsClass = settingsClass;
    }

    @Override
    protected void initialize(Application app) {
        this.calistaGameEngine = (CalistaGameEngine) app;
        this.builder = new XmlMenuBuilder(this);
        this.screenHandler = new MenuScreenHandler(this);

        // load or create settings (SettingsManager is assumed present)
        SettingsManager settingsManager = new SettingsManager(settingsPath.toString());
        try {
            if (doesSettingsExist()) {
                LOG.info("Loading settings from {}", settingsPath);
                settingsInstance = settingsManager.load();
            } else {
                settingsInstance = new Settings();
                settingsManager.save(settingsInstance);
                LOG.info("Created default settings at {}", settingsPath);
            }
        } catch (Exception ex) {
            LOG.warn("Failed to load/save settings; falling back to defaults", ex);
            settingsInstance = new Settings();
        }
        LOG.info("MainMenuAppState initialized (post-processing removed)");
    }

    /**
     * Convenience: read nested fields from settings instance safely.
     * Accepts names like ("audio","master") or ("graphics","vsync").
     * Tries fields first, then getters (getX/isX). Returns null if not found.
     */
    public static Object getSettingsValue(String... fieldNames) {
        Object current = settingsInstance;
        for (String fieldName : fieldNames) {
            Field f;
            try {
                f = current.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                current = f.get(current);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return current;
    }

    @Override
    protected void cleanup(Application application) {
        // initialization-time resources were minimal; heavy cleanup is handled on disable to be safe on render thread
    }

    @Override
    protected void onEnable() {
        SimpleApplication app = (SimpleApplication) getApplication();
        app.getFlyByCamera().setEnabled(false);
        app.getInputManager().setCursorVisible(true);

        // lighting & scene
        setupLights(app);

        // async background load
        loadBackgroundAsync();

        // initialize UI screens on main thread
        try {
            screenHandler.initialize();
        } catch (Exception ex) {
            LOG.warn("screenHandler.initialize() failed", ex);
        }
    }

    private void setupLights(SimpleApplication app) {
        // Ambient light — subtle but sufficient for UI background
        ambientLight = new AmbientLight();
        ambientLight.setColor(new ColorRGBA(0.45f, 0.45f, 0.5f, 1f)); // slightly cooler
        app.getRootNode().addLight(ambientLight);

        // Directional light — soft cinematic fill
        directionalLight = new DirectionalLight();
        directionalLight.setColor(new ColorRGBA(1.0f, 0.98f, 0.92f, 1f).multLocal(0.85f));
        directionalLight.setDirection(new Vector3f(-0.3f, -1f, -0.4f).normalizeLocal());
        app.getRootNode().addLight(directionalLight);
    }

    private void loadBackgroundAsync() {
        if (backgroundLoading.getAndSet(true)) return;

        try {
            MenuData menuData = builder.build(MAIN_MENU_XML);
            SceneXml cfg = menuData.sceneConfig();

            getApplication().enqueue(() -> {
                try {
                    ViceMenuBackground.Builder b = new ViceMenuBackground.Builder(cfg.modelPath);

                    Optional.ofNullable(cfg.skyboxPath).ifPresent(b::skybox);
                    Optional.ofNullable(cfg.modelScale).ifPresent(b::modelScale);

                    Vector3f offset = new Vector3f(
                            Optional.ofNullable(cfg.modelOffsetX).orElse(0f),
                            Optional.ofNullable(cfg.modelOffsetY).orElse(0f),
                            Optional.ofNullable(cfg.modelOffsetZ).orElse(0f)
                    );
                    b.modelOffset(offset);

                    Optional.ofNullable(cfg.lookAtY).ifPresent(y -> b.cameraLookAt(new Vector3f(0, y, 0)));

                    if (cfg.cameraDistance != null && cfg.cameraHeight != null) {
                        b.cameraAnimation(0.08f, cfg.cameraDistance, cfg.cameraHeight);
                    }

                    background = b.build(getApplication());
                    if (background != null && background.getSceneNode() != null) {
                        ((SimpleApplication) getApplication()).getRootNode().attachChild(background.getSceneNode());
                    }
                    backgroundReady.set(true);
                    LOG.info("Background loaded and attached (async)");
                } catch (Exception ex) {
                    LOG.error("Failed to build/attach menu background asynchronously", ex);
                }
                return null;
            });
        } catch (Exception ex) {
            LOG.error("Failed to start async background loading", ex);
        }
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        if (background != null) background.update(tpf);
        if (screenHandler != null) screenHandler.update(tpf);
    }

    private void safeRemoveBackground() {
        if (background == null) return;
        try {
            background.cleanup();
        } catch (Exception ex) {
            LOG.warn("Exception while cleaning background", ex);
        }
        if (background.getSceneNode() != null) {
            ((SimpleApplication) getApplication()).getRootNode().detachChild(background.getSceneNode());
        }
        background = null;
        backgroundReady.set(false);
    }

    @Override
    protected void onDisable() {
        // UI cleanup
        if (screenHandler != null) {
            try {
                screenHandler.cleanup();
            } catch (Exception ex) {
                LOG.warn("screenHandler cleanup error", ex);
            }
            screenHandler = null;
        }

        // detach background & lights on render thread
        getApplication().enqueue(() -> {
            try {
                safeRemoveBackground();
                SimpleApplication app = (SimpleApplication) getApplication();
                if (ambientLight != null) app.getRootNode().removeLight(ambientLight);
                if (directionalLight != null) app.getRootNode().removeLight(directionalLight);
                ambientLight = null;
                directionalLight = null;
            } catch (Exception ex) {
                LOG.warn("Error detaching background or lights", ex);
            }
            return null;
        });

        // hide cursor policy restored for game
        SimpleApplication app = (SimpleApplication) getApplication();
        app.getInputManager().setCursorVisible(false);

        LOG.info("MainMenuAppState disabled and cleaned up (effects removed)");
    }

    public CalistaGameEngine getGameEngine() {
        return calistaGameEngine;
    }

    public XmlMenuBuilder getBuilder() {
        return builder;
    }

    public void showAboutScreen() {
        screenHandler.showAbout();
    }

    public void showMainMenuScreen() {
        screenHandler.showMainMenu();
    }

    public void showSettingsScreen() {
        screenHandler.showSettings();
    }

    public boolean isBackgroundReady() {
        return backgroundReady.get();
    }

    public Path getSettingsPath() {
        return settingsPath;
    }

    public Class<?> getSettingsClass() {
        return settingsClass;
    }

    public Object getSettingsInstance() {
        return settingsInstance;
    }

    public void setSettingsInstance(Settings settingsInstance) {
        MainMenuAppState.settingsInstance = settingsInstance;
    }

    public boolean doesSettingsExist() {
        return settingsPath.toFile().exists();
    }
}