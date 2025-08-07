package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.DepthOfFieldFilter;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.components.ViceMenuBackground;
import org.foxesworld.cge.tmp.menu.xml.SceneXml;

import java.util.Optional;

public final class MainMenuAppState extends BaseAppState {

    private static final String MAIN_MENU_XML = "ui/main_menu.xml";
    private XmlMenuBuilder builder;
    private ViceMenuBackground background;
    private MenuScreenHandler screenHandler;

    private CalistaGameEngine calistaGameEngine;
    private FilterPostProcessor fpp;
    private DepthOfFieldFilter dofFilter;

    public MainMenuAppState(){}
    @Override
    protected void initialize(Application app) {
        this.calistaGameEngine = (CalistaGameEngine) app;
        builder = new XmlMenuBuilder((CalistaGameEngine) getApplication());
        screenHandler = new MenuScreenHandler(this);

        fpp = new FilterPostProcessor(app.getAssetManager());
        dofFilter = new DepthOfFieldFilter();
        dofFilter.setFocusDistance(0);
        dofFilter.setFocusRange(10);
        dofFilter.setBlurScale(1.4f);
        fpp.addFilter(dofFilter);
    }

    @Override
    protected void cleanup(Application application) {
    }

    @Override
    protected void onEnable() {
        SimpleApplication app = (SimpleApplication) getApplication();
        app.getFlyByCamera().setEnabled(false);
        app.getInputManager().setCursorVisible(true);
        app.getViewPort().addProcessor(fpp);
        setupBackground();
        screenHandler.initialize();
    }

    private void setupBackground() {
        MenuData menuData = builder.build(MAIN_MENU_XML);
        background = createBackgroundFromConfig(menuData.sceneConfig());
        ((SimpleApplication) getApplication()).getRootNode().attachChild(background.getSceneNode());
    }

    public void showSettingsScreen() {
        dofFilter.setEnabled(true);
        screenHandler.showSettings();
    }

    public void showMainMenuScreen() {
        dofFilter.setEnabled(false);
        screenHandler.showMainMenu();
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;
        if (background != null) background.update(tpf);
        if (screenHandler != null) screenHandler.update(tpf);
    }

    private ViceMenuBackground createBackgroundFromConfig(SceneXml sceneConfig) {
        if (sceneConfig == null || sceneConfig.modelPath == null) return null;

        ViceMenuBackground.Builder builder = new ViceMenuBackground.Builder(sceneConfig.modelPath);

        Optional.ofNullable(sceneConfig.skyboxPath).ifPresent(builder::skybox);
        Optional.ofNullable(sceneConfig.modelScale).ifPresent(builder::modelScale);

        Vector3f offset = new Vector3f(
                Optional.ofNullable(sceneConfig.modelOffsetX).orElse(0f),
                Optional.ofNullable(sceneConfig.modelOffsetY).orElse(0f),
                Optional.ofNullable(sceneConfig.modelOffsetZ).orElse(0f)
        );
        builder.modelOffset(offset);

        Optional.ofNullable(sceneConfig.lookAtY).ifPresent(y -> builder.cameraLookAt(new Vector3f(0, y, 0)));

        if (sceneConfig.cameraDistance != null && sceneConfig.cameraHeight != null) {
            builder.cameraAnimation(0.08f, sceneConfig.cameraDistance, sceneConfig.cameraHeight);
        }

        return builder.build(getApplication());
    }

    @Override
    protected void onDisable() {
        if (background != null) background.cleanup();
        if (screenHandler != null) screenHandler.cleanup();

        SimpleApplication app = (SimpleApplication) getApplication();
        app.getViewPort().removeProcessor(fpp);
        app.getInputManager().setCursorVisible(false);
    }

    public CalistaGameEngine getCalistaGameEngine() {
        return calistaGameEngine;
    }

    public XmlMenuBuilder getBuilder() {
        return builder;
    }
}
