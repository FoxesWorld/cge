package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.DepthOfFieldFilter;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.components.ViceMenuBackground;
import org.foxesworld.cge.tmp.menu.components.ViceTabs;
import org.foxesworld.cge.tmp.menu.xml.SceneXml;
import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Manages the high-level state for the main menu, which is built declaratively from an XML file.
 * This AppState coordinates the 3D background, the XML-driven UI, and user input,
 * functioning as a state machine to switch between different menu screens.
 */
public final class MainMenuAppState extends BaseAppState implements ActionListener, AnalogListener {

    private static final String MAIN_MENU_XML = "ui/main_menu.xml";
    private static final String SETTINGS_MENU_XML = "ui/settings_menu.xml";
    private static final String MOUSE_CLICK = "MenuMouseClick";
    private static final String MOUSE_MOVE = "MenuMouseMove";

    private XmlMenuBuilder menuBuilder;
    private ViceMenuBackground background;
    private Node currentMenuNode;

    // UI elements for the current screen
    private List<ViceButton> currentButtons;
    private List<ViceTabs> currentTabGroups;
    private ViceButton selectedButton;

    private boolean inSettings = false;
    private boolean isMouseDragging = false;

    private FilterPostProcessor fpp;
    private DepthOfFieldFilter dofFilter;

    @Override
    protected void initialize(Application app) {
        this.menuBuilder = new XmlMenuBuilder(app, ViceButton.Style.getViceStyle());

        fpp = new FilterPostProcessor(app.getAssetManager());
        dofFilter = new DepthOfFieldFilter();
        dofFilter.setFocusDistance(0);
        dofFilter.setFocusRange(10);
        dofFilter.setBlurScale(1.4f);
        dofFilter.setEnabled(false);
        fpp.addFilter(dofFilter);
        setupInput();
    }

    @Override
    protected void onEnable() {
        SimpleApplication simpleApp = (SimpleApplication) getApplication();
        simpleApp.getFlyByCamera().setEnabled(false);
        simpleApp.getInputManager().setCursorVisible(true);
        simpleApp.getViewPort().addProcessor(fpp);

        showMainMenuScreen();
    }

    /**
     * Builds and displays the main menu UI and 3D background.
     */
    public void showMainMenuScreen() {
        if (background == null) {
            MenuData menuData = menuBuilder.build(MAIN_MENU_XML);
            this.background = createBackgroundFromConfig(menuData.sceneConfig());
            ((SimpleApplication) getApplication()).getRootNode().attachChild(background.getSceneNode());
        }
        switchToScreen(MAIN_MENU_XML);
        inSettings = false;
        dofFilter.setEnabled(false);
    }

    /**
     * Builds and displays the settings menu UI over the existing background.
     */
    public void showSettingsScreen() {
        switchToScreen(SETTINGS_MENU_XML);
        inSettings = true;
        dofFilter.setEnabled(true);
    }

    private void switchToScreen(String xmlPath) {
        if (currentMenuNode != null) {
            currentMenuNode.removeFromParent();
        }
        MenuData menuData = menuBuilder.build(xmlPath);
        this.currentMenuNode = menuData.uiNode();
        this.currentButtons = menuData.getButtons();
        this.currentTabGroups = menuData.getTabGroups(); // Сохраняем вкладки
        ((SimpleApplication) getApplication()).getGuiNode().attachChild(currentMenuNode);
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) return;
        if (background != null) background.update(tpf);

        Vector2f cursor = getApplication().getInputManager().getCursorPosition();

        if (inSettings) {
            if (currentTabGroups != null) {
                currentTabGroups.forEach(tabs -> tabs.update(tpf, cursor));
            }
        } else {
            handleMainMenuInteraction(tpf, cursor);
        }
    }

    private void handleMainMenuInteraction(float tpf, Vector2f cursor) {
        if (currentButtons == null) return;
        selectedButton = currentButtons.stream()
                .filter(button -> button.intersects(cursor))
                .findFirst()
                .orElse(null);

        currentButtons.forEach(button -> {
            button.setSelected(button == selectedButton);
            button.update(tpf);
        });
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!MOUSE_CLICK.equals(name)) return;
        Vector2f cursor = getApplication().getInputManager().getCursorPosition();

        if (inSettings) {
            if (isPressed) {
                isMouseDragging = true;
                if (currentTabGroups != null) {
                    currentTabGroups.forEach(tabs -> tabs.handleMousePress(cursor));
                }
            } else {
                isMouseDragging = false;
                if (currentTabGroups != null) {
                    currentTabGroups.forEach(ViceTabs::handleMouseRelease);
                }
            }
        } else {
            if (isPressed && selectedButton != null) {
                selectedButton.executeAction();
            }
        }
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        if (MOUSE_MOVE.equals(name) && inSettings && isMouseDragging) {
            if (currentTabGroups != null) {
                Vector2f cursor = getApplication().getInputManager().getCursorPosition();
                currentTabGroups.forEach(tabs -> tabs.handleMouseDrag(cursor));
            }
        }
    }

    private void setupInput() {
        InputManager inputManager = getApplication().getInputManager();
        if (!inputManager.hasMapping(MOUSE_CLICK)) {
            inputManager.addMapping(MOUSE_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        }
        if (!inputManager.hasMapping(MOUSE_MOVE)) {
            inputManager.addMapping(MOUSE_MOVE, new com.jme3.input.controls.MouseAxisTrigger(MouseInput.AXIS_X, false), new com.jme3.input.controls.MouseAxisTrigger(MouseInput.AXIS_Y, false));
        }
        inputManager.addListener(this, MOUSE_CLICK, MOUSE_MOVE);
    }

    private ViceMenuBackground createBackgroundFromConfig(SceneXml sceneConfig) {
        if (sceneConfig == null || sceneConfig.modelPath == null) {
            throw new IllegalStateException("Scene configuration or modelPath is missing in XML file.");
        }
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
        if (currentMenuNode != null) currentMenuNode.removeFromParent();

        SimpleApplication simpleApp = (SimpleApplication) getApplication();
        simpleApp.getViewPort().removeProcessor(fpp);
        simpleApp.getInputManager().setCursorVisible(false);
    }

    @Override
    protected void cleanup(Application app) {
        app.getInputManager().removeListener(this);
    }
}