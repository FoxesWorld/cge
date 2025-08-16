package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.DepthOfFieldFilter;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.components.ViceMenuBackground;
import org.foxesworld.cge.tmp.menu.xml.SceneXml;
import org.foxesworld.cge.ue.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main menu state — upgraded for AAA feel:
 * - smooth DOF transitions (blend)
 * - Bloom filter
 * - asynchronous background loading via Application.enqueue()
 * - robust lifecycle and safe cleanup
 * - ambient + directional lights configured for cinematic look
 */
public final class MainMenuAppState extends BaseAppState {

    private static final Logger LOG = LoggerFactory.getLogger(MainMenuAppState.class);

    private static final String MAIN_MENU_XML = "assets/Interface/main_menu.xml";

    private Settings settingsInstance;
    private final Class<?> settingsClass;
    private final Path settingsPath = Path.of(new File("settings.json").toURI());
    private XmlMenuBuilder builder;
    private ViceMenuBackground background;
    private MenuScreenHandler screenHandler;

    private CalistaGameEngine calistaGameEngine;

    // post-processing
    private FilterPostProcessor fpp;
    private DepthOfFieldFilter dofFilter;
    private BloomFilter bloomFilter;

    // smooth DOF blend [0..1] (controls blur intensity)
    private float dofBlend = 0f;
    private float dofBlendTarget = 0f;
    private final float DOF_BLEND_SPEED = 2.4f; // higher = faster

    // markers for async loading
    private final AtomicBoolean backgroundLoading = new AtomicBoolean(false);
    private final AtomicBoolean backgroundReady = new AtomicBoolean(false);

    // scene lighting nodes (kept to cleanup)
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
        SettingsManager settingsManager = new SettingsManager(settingsPath.toString());
        if(settingsPath.toFile().exists()) {
            LOG.info("Loading settings from {}", settingsPath);
            settingsInstance = settingsManager.load();
        }

        // Prepare post processor and filters but don't attach yet
        fpp = new FilterPostProcessor(app.getAssetManager());

        // Depth of Field (tweak defaults for cinematic look)
        dofFilter = new DepthOfFieldFilter();
        dofFilter.setFocusDistance(10f);   // initial approximate
        dofFilter.setFocusRange(8f);
        dofFilter.setBlurScale(1.6f);

        // Bloom for subtle glow
        bloomFilter = new BloomFilter(BloomFilter.GlowMode.Scene);
        bloomFilter.setBloomIntensity(1.0f);
        bloomFilter.setBlurScale(2.5f);
        bloomFilter.setExposurePower(1.1f);
        //bloomFilter.setDownSampling(2);

        // Attach both filters to FPP. We'll toggle them by adding/removing fpp to viewport.
        fpp.addFilter(bloomFilter);
        fpp.addFilter(dofFilter);

        LOG.info("MainMenuAppState initialized (DOF + Bloom configured)");
    }

    public Object getNestedFieldValue(Object obj, String... fieldNames) {
        Object current = obj;
        for (String fieldName : fieldNames) {
            Field f = null;
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
        // nothing heavy here — we do cleanup on disable to free resources when toggled
    }

    @Override
    protected void onEnable() {
        SimpleApplication app = (SimpleApplication) getApplication();
        app.getFlyByCamera().setEnabled(false);
        app.getInputManager().setCursorVisible(true);

        // Attach FPP only once
        try {
            app.getViewPort().addProcessor(fpp);
        } catch (Exception ex) {
            LOG.warn("Failed to add FilterPostProcessor to viewport", ex);
        }

        // Add subtle lighting for menu scene
        setupLights(app);

        // Load background asynchronously to avoid hitch on enable
        loadBackgroundAsync();

        // initialize screens (UI) on main thread but keep screen handler lightweight
        screenHandler.initialize();

        // ensure DOF is off by default (we'll blend it on demand)
        setDofEnabledSmooth(false);
    }

    private void setupLights(SimpleApplication app) {
        // Ambient light
        ambientLight = new AmbientLight();
        ambientLight.setColor(new ColorRGBA(0.35f, 0.40f, 0.45f, 1f));
        app.getRootNode().addLight(ambientLight);

        // Directional light — cinematic angle
        directionalLight = new DirectionalLight();
        directionalLight.setColor(new ColorRGBA(1.0f, 0.98f, 0.92f, 1f).multLocal(0.9f));
        directionalLight.setDirection(new Vector3f(-0.3f, -1f, -0.6f).normalizeLocal());
        app.getRootNode().addLight(directionalLight);
    }

    private void loadBackgroundAsync() {
        if (backgroundLoading.getAndSet(true)) return; // already loading

        // build menu data on render thread and attach when ready
        try {
            MenuData menuData = builder.build(MAIN_MENU_XML);
            SceneXml cfg = menuData.sceneConfig();

            // perform heavy scene creation on the render thread using enqueue()
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

                    // build background on render thread
                    background = b.build(getApplication());

                    // attach the scene node to rootNode (on render thread)
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

    /**
     * Request DOF to be enabled/disabled with a smooth blend.
     * Use showSettingsScreen()/showMainMenuScreen() to trigger normally.
     */
    public void setDofEnabledSmooth(boolean enabled) {
        dofBlendTarget = enabled ? 1f : 0f;
    }

    public void showSettingsScreen() {
        // enable DOF blend target — UI will call setDofEnabledSmooth(true) already
        setDofEnabledSmooth(true);
        screenHandler.showSettings();
    }

    public void showAboutScreen() {
        screenHandler.showAbout();
    }

    public void showMainMenuScreen() {
        setDofEnabledSmooth(false);
        if (screenHandler != null) screenHandler.showMainMenu();
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;

        // update blend fraction for DOF (smooth interpolation)
        if (Math.abs(dofBlend - dofBlendTarget) > 0.001f) {
            float step = Math.min(1f, tpf * DOF_BLEND_SPEED);
            dofBlend = com.jme3.math.FastMath.interpolateLinear(step, dofBlend, dofBlendTarget);
            applyDofBlend(dofBlend);
        }

        if (background != null) background.update(tpf);
        if (screenHandler != null) screenHandler.update(tpf);
    }

    /**
     * Apply current DOF blend to filter values. We keep the filter present in FPP,
     * but adjust blurScale/focusRange so effect is smooth. We also toggle global enable for perf.
     */
    private void applyDofBlend(float blend) {
        // blend in [0..1] drives blurScale and focusRange; tweak for cinematic feel
        float minBlur = 0.0f;
        float maxBlur = 2.1f; // stronger when fully on
        float blur = minBlur + (maxBlur - minBlur) * blend;

        float minRange = 0.2f;
        float maxRange = 12f;
        float range = maxRange - (maxRange - minRange) * blend; // smaller range => stronger bokeh

        float focus = 8f; // base focus distance
        try {
            dofFilter.setBlurScale(blur);
            dofFilter.setFocusRange(range);
            dofFilter.setFocusDistance(focus);

            // Keep the filter attached but allow it to short-circuit when effectively off
            boolean effectivelyOn = blend > 0.02f;
            dofFilter.setEnabled(effectivelyOn);
        } catch (Exception ex) {
            LOG.warn("Failed to apply DOF blend values", ex);
        }
    }

    private void safeRemoveBackground() {
        if (background != null) {
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
    }

    @Override
    protected void onDisable() {
        // disable DOF immediately
        dofBlendTarget = 0f;
        dofBlend = 0f;
        applyDofBlend(0f);

        // cleanup UI
        if (screenHandler != null) {
            try {
                screenHandler.cleanup();
            } catch (Exception ex) {
                LOG.warn("screenHandler cleanup error", ex);
            }
            screenHandler = null;
        }

        // remove background and lights on render thread for safety
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

        // remove post processor
        try {
            getApplication().getViewPort().removeProcessor(fpp);
        } catch (Exception ex) {
            LOG.warn("Failed to remove FPP from viewport", ex);
        }

        // hide cursor policy restored for game
        SimpleApplication app = (SimpleApplication) getApplication();
        app.getInputManager().setCursorVisible(false);

        LOG.info("MainMenuAppState disabled and cleaned up");
    }

    // Expose helpers for other systems
    public CalistaGameEngine getGameEngine() {
        return calistaGameEngine;
    }

    public XmlMenuBuilder getBuilder() {
        return builder;
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
        this.settingsInstance = settingsInstance;
    }
}
