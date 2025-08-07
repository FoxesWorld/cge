package org.foxesworld.cge.tmp.menu;

import com.jme3.audio.AudioNode;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.SoundComponent;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.components.ViceCheckbox;

import java.util.Comparator;

/**
 * Управляет UI-меню: загрузка экранов, обновление, ввод.
 */
public final class MenuScreenHandler {
    private static final String MAIN_MENU_XML = "ui/main_menu.xml";
    private static final String SETTINGS_MENU_XML = "ui/settings_menu.xml";
    private Quad screenSize;
    private final Node guiNode;
    private final CalistaGameEngine engine;
    private final XmlMenuBuilder builder;
    private final InputHandler inputHandler;
    private MenuData currentMenu;
    private AudioNode clickSound;

    /**
     * Создаёт обработчик экранов.
     */
    public MenuScreenHandler(MainMenuAppState app) {
        this.engine = app.getCalistaGameEngine();
        this.guiNode = engine.getGuiNode();
        this.builder = app.getBuilder();
        this.inputHandler = new InputHandler(engine.getInputManager());
    }

    /**
     * Инициализирует звук и слушатели.
     */
    public void initialize() {
        clickSound = new AudioNode(engine.getAssetManager(), "assets/Sounds/ui/pop.ogg", false);
        clickSound.setPositional(false);
        clickSound.setLooping(false);
        clickSound.setVolume(1f);
        inputHandler.register();
        showMainMenu();
    }

    /** Переход к главному экрану. */
    public void showMainMenu() { switchScreen(MAIN_MENU_XML); }

    /** Переход к настройкам. */
    public void showSettings() { switchScreen(SETTINGS_MENU_XML); }

    private void switchScreen(String xml) {
        if (currentMenu != null && currentMenu.uiNode() != null) {
            currentMenu.uiNode().removeFromParent();
        }
        currentMenu = builder.build(xml);
        inputHandler.setMenu(currentMenu);
        guiNode.attachChild(currentMenu.uiNode());
    }

    /** Обновляет все компоненты и ввод. */
    public void update(float tpf) {
        if (currentMenu == null) return;
        currentMenu.allComponents().forEach(c -> c.update(tpf));
        inputHandler.updateHover();
        screenSize = new Quad(engine.getContext().getSettings().getWindowWidth(), engine.getContext().getSettings().getWindowHeight());

    }

    /** Освобождает ресурсы и слушатели. */
    public void cleanup() {
        if (currentMenu != null && currentMenu.uiNode() != null) {
            currentMenu.uiNode().removeFromParent();
        }
        inputHandler.unregister();
    }

    private class InputHandler implements ActionListener, AnalogListener {
        private static final String CLICK = "MenuClick";
        private static final String MOVE = "MenuMove";

        private final InputManager im;
        private MenuData menu;
        private InteractiveComponent focused;
        private InteractiveComponent hovered;
        private boolean dragging;

        InputHandler(InputManager im) { this.im = im; }

        void setMenu(MenuData m) {
            this.menu = m;
            this.focused = null;
            this.hovered = null;
        }

        void register() {
            if (!im.hasMapping(CLICK)) {
                im.addMapping(CLICK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
            }
            if (!im.hasMapping(MOVE)) {
                im.addMapping(MOVE, new MouseAxisTrigger(MouseInput.AXIS_X, true), new MouseAxisTrigger(MouseInput.AXIS_Y, true));
            }
            im.addListener(this, CLICK, MOVE);
        }

        void unregister() { im.removeListener(this); }

        void updateHover() {
            if (menu == null) return;
            Vector2f c = im.getCursorPosition();
            InteractiveComponent now = findComponent(c);
            if (now != hovered) {
                if (hovered != null) hovered.setHovered(false);
                if (now != null) now.setHovered(true);
                hovered = now;
            }
        }

        private InteractiveComponent findComponent(Vector2f c) {
            return menu.allComponents().stream()
                    .filter(InteractiveComponent.class::isInstance)
                    .map(InteractiveComponent.class::cast)
                    .filter(ic -> ic.intersects(c))
                    .max(Comparator.comparing(ic -> ic.getNode().getWorldTranslation().z))
                    .orElse(null);
        }

        @Override
        public void onAction(String name, boolean pressed, float tpf) {
            Vector2f c = im.getCursorPosition();
            if (pressed) {
                dragging = true;
                focused = findComponent(c);
                if (focused != null) focused.handleMousePress(c);
            } else {
                dragging = false;
                InteractiveComponent release = findComponent(c);

                // Звук по нажатию
                if (focused != null && focused == release && release instanceof SoundComponent) {
                    clickSound.playInstance();
                }

                // Выполнение действия компонента по типу
                if (focused instanceof ViceButton vb) vb.executeAction();
                else if (focused instanceof ViceCheckbox cb) cb.toggle();


                // Освобождение фокуса
                if (focused != null) focused.handleMouseRelease();
                focused = null;
            }
        }


        @Override
        public void onAnalog(String name, float value, float tpf) {
            if (dragging && focused != null) focused.handleMouseDrag(im.getCursorPosition());
        }
    }

    public Quad getScreenSize() {
        return screenSize;
    }
}
