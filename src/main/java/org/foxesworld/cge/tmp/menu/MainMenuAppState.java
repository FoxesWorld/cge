package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.foxesworld.cge.CalistaGameEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * AppState для главного меню с 3D-фоном.
 * Финальная версия, использующая канонический паттерн JME
 * для управления ресурсами в onEnable/onDisable.
 */
public class MainMenuAppState extends BaseAppState implements ActionListener {

    // Поля для ресурсов (создаются в onEnable, уничтожаются в onDisable)
    private ViewPort sceneViewPort;
    private MenuBackground background;
    private Node menuRootNode;
    private final List<SimpleButton> buttons = new ArrayList<>();
    private int selectedIndex = 0;

    // Константы
    private static final String FONT_PATH = "Interface/Fonts/Default.fnt";
    private static final String KEY_NAV_UP = "MenuUp", KEY_NAV_DOWN = "MenuDown", KEY_CONFIRM = "MenuEnter";

    @Override
    protected void initialize(Application app) {
        // Настраиваем слушатели ввода один раз при инициализации
        setupInputListeners();
    }

    @Override
    protected void onEnable() {
        SimpleApplication calistaGameEngine = (CalistaGameEngine) getApplication();
        calistaGameEngine.getFlyByCamera().setEnabled(false);

        // 1. Создаем камеру и ViewPort для 3D-сцены
        Camera sceneCam = calistaGameEngine.getCamera().clone();
        sceneViewPort = calistaGameEngine.getRenderManager().createPreView("MenuBackgroundView", sceneCam);
        sceneViewPort.setClearFlags(true, true, true);

        // 2. Создаем "поставщика контента" для 3D-сцены
        background = new MenuBackground(calistaGameEngine, sceneCam);
        calistaGameEngine.getRootNode().attachChild(background.getSceneNode());

        // 4. Добавляем тени
        addShadows(calistaGameEngine, sceneViewPort, background.getSceneNode());

        // 5. Создаем и прикрепляем 2D-меню
        menuRootNode = new Node("MainMenuUINode");
        createMenuButtons();
        createTitle();
        updateSelection();
        calistaGameEngine.getGuiNode().attachChild(menuRootNode);
    }

    @Override
    protected void onDisable() {
        // --- УНИЧТОЖЕНИЕ ВСЕХ РЕСУРСОВ ПРИ ДЕАКТИВАЦИИ СОСТОЯНИЯ ---
        SimpleApplication simpleApp = (SimpleApplication) getApplication();

        // Уничтожаем 2D-меню
        if (menuRootNode != null) {
            menuRootNode.removeFromParent();
            menuRootNode = null;
        }
        buttons.clear();

        // Уничтожаем 3D-сцену и ViewPort
        if (sceneViewPort != null) {
            simpleApp.getRenderManager().removePreView(sceneViewPort);
            sceneViewPort = null;
        }
        if (background != null) {
            background.cleanup();
            background = null;
        }
    }

    @Override
    public void update(float tpf) {
        if (isEnabled() && background != null) {
            background.update(tpf);
        }
    }

    @Override
    protected void cleanup(Application app) {
        // Гарантируем, что ресурсы очищены, и удаляем слушатели
        if (isEnabled()) {
            onDisable();
        }
        app.getInputManager().removeListener(this);
    }

    // --- Методы для создания UI ---
    private void createMenuButtons() {
        addButton("SINGLE PLAYER", () -> ((CalistaGameEngine) getApplication()).startGameFromMenu());
        addButton("OPTIONS", () -> System.out.println("Opening Options..."));
        addButton("QUIT", () -> getApplication().stop());
        for (SimpleButton button : buttons) menuRootNode.attachChild(button.getButtonNode());
    }
    private void addButton(String text, Runnable action) {
        float MENU_X_OFFSET=60f, MENU_Y_START=500f, BTN_W=420f, BTN_H=55f, SPACING=5f;
        float yPos = MENU_Y_START - buttons.size() * (BTN_H + SPACING);
        SimpleButton button = new SimpleButton(getApplication().getAssetManager(), text, FONT_PATH, action);
        button.setSize(BTN_W, BTN_H);
        button.setPosition(MENU_X_OFFSET, yPos);
        buttons.add(button);
    }
    private void createTitle() {
        SimpleButton title = new SimpleButton(getApplication().getAssetManager(), "CALISTA TEST", FONT_PATH, null);
        title.getLabel().setColor(ColorRGBA.White);
        title.getLabel().setSize(title.getLabel().getFont().getCharSet().getRenderedSize() * 2.5f);
        title.setPosition(60f, 650f);
        title.setBackgroundVisibility(false);
        menuRootNode.attachChild(title.getButtonNode());
    }
    private void updateSelection() {
        ColorRGBA BG_COLOR = ColorRGBA.White.clone(), TXT_SEL = ColorRGBA.Black.clone(), TXT_NORM = ColorRGBA.LightGray.clone();
        for (int i=0; i < buttons.size(); i++) {
            buttons.get(i).setStyle(i == selectedIndex ? BG_COLOR : ColorRGBA.BlackNoAlpha, i == selectedIndex ? TXT_SEL : TXT_NORM);
        }
    }

    // --- Методы для обработки ввода ---
    private void setupInputListeners() {
        var inputManager = getApplication().getInputManager();
        inputManager.addMapping(KEY_NAV_UP, new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping(KEY_NAV_DOWN, new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping(KEY_CONFIRM, new KeyTrigger(KeyInput.KEY_RETURN), new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addListener(this, KEY_NAV_UP, KEY_NAV_DOWN, KEY_CONFIRM);
    }
    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isPressed || !isEnabled() || buttons.isEmpty()) return;
        int oldIndex = selectedIndex;
        switch (name) {
            case KEY_NAV_DOWN: selectedIndex = (selectedIndex + 1) % buttons.size(); break;
            case KEY_NAV_UP: selectedIndex = (selectedIndex - 1 + buttons.size()) % buttons.size(); break;
            case KEY_CONFIRM: buttons.get(selectedIndex).executeAction(); return;
        }
        if (oldIndex != selectedIndex) updateSelection();
    }

    // --- Вспомогательный метод для теней ---
    private void addShadows(SimpleApplication app, ViewPort viewPort, Node scene) {
        for (com.jme3.light.Light light : scene.getWorldLightList()) {
            if (light instanceof DirectionalLight) {
                DirectionalLightShadowRenderer dlsr = new DirectionalLightShadowRenderer(app.getAssetManager(), 1024, 3);
                dlsr.setLight((DirectionalLight) light);
                viewPort.addProcessor(dlsr);
                break; // Добавляем тени только для первого направленного света
            }
        }
    }
}