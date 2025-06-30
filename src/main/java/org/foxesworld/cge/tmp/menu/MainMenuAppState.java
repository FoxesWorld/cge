package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.foxesworld.cge.CalistaGameEngine;

import java.util.ArrayList;
import java.util.List;

public class MainMenuAppState extends BaseAppState implements ActionListener, AnalogListener {

    // ... поля остаются без изменений ...
    private ViewPort sceneViewPort;
    private MenuBackground background;
    private Node menuRootNode;
    private final List<SimpleButton> buttons = new ArrayList<>();
    private int selectedIndex = 0;
    private boolean isMouseSelectionActive = false; // Флаг для предотвращения конфликтов с клавиатурой

    private static final String FONT_PATH = "Interface/Fonts/Default.fnt";
    private static final String KEY_NAV_UP = "MenuUp", KEY_NAV_DOWN = "MenuDown", KEY_CONFIRM = "MenuEnter";
    private static final String MOUSE_MOVE = "MenuMouseMove", MOUSE_CLICK = "MenuMouseClick";

    @Override
    protected void initialize(Application app) {
        setupInputListeners();
    }

    @Override
    protected void onEnable() {
        SimpleApplication calistaGameEngine = (CalistaGameEngine) getApplication();
        calistaGameEngine.getFlyByCamera().setEnabled(false);

        // УЛУЧШЕНО: Показываем курсор мыши, когда меню активно
        getApplication().getInputManager().setCursorVisible(true);

        // ... остальная часть onEnable без изменений ...
        Camera sceneCam = calistaGameEngine.getCamera().clone();
        sceneViewPort = calistaGameEngine.getRenderManager().createPreView("MenuBackgroundView", sceneCam);
        sceneViewPort.setClearFlags(true, true, true);
        background = new MenuBackground(calistaGameEngine);
        calistaGameEngine.getRootNode().attachChild(background.getSceneNode());
        addShadows(calistaGameEngine, sceneViewPort, background.getSceneNode());
        menuRootNode = new Node("MainMenuUINode");
        createMenuButtons();
        createTitle();
        updateSelection();
        calistaGameEngine.getGuiNode().attachChild(menuRootNode);
    }

    @Override
    protected void onDisable() {
        // УЛУЧШЕНО: Прячем курсор мыши, когда выходим из меню
        getApplication().getInputManager().setCursorVisible(false);

        // ... остальная часть onDisable без изменений ...
        SimpleApplication simpleApp = (SimpleApplication) getApplication();
        if (menuRootNode != null) {
            menuRootNode.removeFromParent();
            menuRootNode = null;
        }
        buttons.clear();
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
        if (isEnabled()) {
            onDisable();
        }
        app.getInputManager().removeListener(this);
    }

    // --- Методы для создания UI без изменений ---
    private void createMenuButtons() {
        addButton("SINGLE PLAYER", () -> ((CalistaGameEngine) getApplication()).startGameFromMenu());
        addButton("OPTIONS", () -> System.out.println("Opening Options..."));
        addButton("QUIT", () -> getApplication().stop());
        for (SimpleButton button : buttons) menuRootNode.attachChild(button.getNode());
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
        title.getLabel().setColor(ColorRGBA.Brown);
        title.getLabel().setSize(title.getLabel().getFont().getCharSet().getRenderedSize() * 2.5f);
        title.setPosition(60f, 650f);
        title.setBackgroundVisibility(false);
        menuRootNode.attachChild(title.getNode());
    }
    private void updateSelection() {
        ColorRGBA BG_COLOR = ColorRGBA.White.clone(), TXT_SEL = ColorRGBA.Black.clone(), TXT_NORM = ColorRGBA.LightGray.clone();
        for (int i=0; i < buttons.size(); i++) {
            buttons.get(i).setStyle(i == selectedIndex ? BG_COLOR : ColorRGBA.BlackNoAlpha, i == selectedIndex ? TXT_SEL : TXT_NORM);
        }
    }

    // --- Методы для обработки ввода ---

    /**
     * УЛУЧШЕНО: Добавляем маппинги для мыши.
     */
    private void setupInputListeners() {
        var inputManager = getApplication().getInputManager();
        // Клавиатура
        inputManager.addMapping(KEY_NAV_UP, new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping(KEY_NAV_DOWN, new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping(KEY_CONFIRM, new KeyTrigger(KeyInput.KEY_RETURN), new KeyTrigger(KeyInput.KEY_SPACE));
        // Мышь
        inputManager.addMapping(MOUSE_MOVE, new com.jme3.input.controls.MouseAxisTrigger(MouseInput.AXIS_X, false), new com.jme3.input.controls.MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping(MOUSE_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));

        // Регистрируем этот класс как слушатель для всех событий
        inputManager.addListener(this, KEY_NAV_UP, KEY_NAV_DOWN, KEY_CONFIRM, MOUSE_CLICK, MOUSE_MOVE);
    }

    /**
     * УЛУЧШЕНО: Обработка нажатий (клавиатура и клик мыши).
     */
    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isPressed || !isEnabled() || buttons.isEmpty()) return;

        int oldIndex = selectedIndex;

        switch (name) {
            case KEY_NAV_DOWN -> {
                isMouseSelectionActive = false; // Пользователь нажал клавишу, отключаем "залипание" мыши
                selectedIndex = (selectedIndex + 1) % buttons.size();
            }
            case KEY_NAV_UP -> {
                isMouseSelectionActive = false;
                selectedIndex = (selectedIndex - 1 + buttons.size()) % buttons.size();
            }
            case KEY_CONFIRM -> {
                buttons.get(selectedIndex).executeAction();
                return; // Выходим, чтобы не вызывать updateSelection()
            }
            case MOUSE_CLICK -> {
                // Если клик произошел, когда мышь была над кнопкой, выполняем действие
                if (isMouseSelectionActive) {
                    buttons.get(selectedIndex).executeAction();
                }
                return;
            }
        }

        if (oldIndex != selectedIndex) {
            updateSelection();
        }
    }

    /**
     * УЛУЧШЕНО: Новый метод для обработки движения мыши.
     */
    @Override
    public void onAnalog(String name, float value, float tpf) {
        if (!isEnabled() || !MOUSE_MOVE.equals(name)) return;

        // Получаем текущие координаты курсора
        Vector2f cursor = getApplication().getInputManager().getCursorPosition();
        int newSelectedIndex = -1;

        // Проверяем, находится ли курсор над какой-либо кнопкой
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).intersects(cursor)) {
                newSelectedIndex = i;
                break;
            }
        }

        if (newSelectedIndex != -1) {
            // Если курсор над кнопкой
            isMouseSelectionActive = true;
            if (selectedIndex != newSelectedIndex) {
                selectedIndex = newSelectedIndex;
                updateSelection();
            }
        } else {
            // Если курсор не над кнопкой
            isMouseSelectionActive = false;
        }
    }


    // --- Вспомогательный метод для теней без изменений ---
    private void addShadows(SimpleApplication app, ViewPort viewPort, Node scene) {
        for (com.jme3.light.Light light : scene.getWorldLightList()) {
            if (light instanceof DirectionalLight) {
                DirectionalLightShadowRenderer dlsr = new DirectionalLightShadowRenderer(app.getAssetManager(), 1024, 3);
                dlsr.setLight((DirectionalLight) light);
                viewPort.addProcessor(dlsr);
                break;
            }
        }
    }
}