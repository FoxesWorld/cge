package org.foxesworld.cge.tmp.menu;

import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.components.UIComponent;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.SoundComponent;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.components.ViceCheckbox;

import java.util.Comparator;

/**
 * Управляет UI-меню: загрузка экранов, обновление, ввод.
 */
public final class MenuScreenHandler {
    private static final String MAIN_MENU_XML = "assets/Interface/main_menu.xml";
    private static final String SETTINGS_MENU_XML = "assets/Interface/settings_menu.xml";
    private static final String ABOUT_XML = "assets/Interface/about.xml";
    private final Node guiNode;
    private final CalistaGameEngine engine;
    private final XmlMenuBuilder builder;
    private final InputHandler inputHandler;
    private MenuData currentMenu;

    /**
     * Создаёт обработчик экранов.
     */
    public MenuScreenHandler(MainMenuAppState app) {
        this.engine = app.getGameEngine();
        this.guiNode = engine.getGuiNode();
        this.builder = app.getBuilder();
        this.inputHandler = new InputHandler(engine.getInputManager());
    }

    /**
     * Инициализирует звук и слушатели.
     */
    public void initialize() {
        engine.getSoundManager().play("ui.enter");
        inputHandler.register();
        showMainMenu();
    }


    public void showMainMenu() { switchScreen(MAIN_MENU_XML); }

    /** Переход к настройкам. */
    public void showSettings() { switchScreen(SETTINGS_MENU_XML);}

    public void showAbout() { switchScreen(ABOUT_XML); }

    private void switchScreen(String xml) {
        if (currentMenu != null && currentMenu.uiNode() != null) {
            currentMenu.uiNode().removeFromParent();
        }
        currentMenu = builder.build(xml);
        inputHandler.setMenu(currentMenu);
        guiNode.attachChild(currentMenu.uiNode());
    }

    public void update(float tpf) {
        if (currentMenu == null) return;
        currentMenu.allComponents().forEach(c -> c.update(tpf));
        inputHandler.updateHover();
    }

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
                // смена hover'а
                if (hovered != null) hovered.setHovered(false);
                if (now != null) now.setHovered(true);
                hovered = now;
                if(now instanceof SoundComponent component) {
                    engine.getSoundManager().play(component.getHoverSound());
                }
            }
        }


        private InteractiveComponent findComponent(Vector2f c) {
            return menu.allComponents().stream()
                    .filter(UIComponent.class::isInstance)
                    .map(InteractiveComponent.class::cast)
                    .filter(ic -> ((UIComponent) ic).intersects(c))
                    .max(Comparator.comparing(ic -> ((UIComponent) ic).getNode().getWorldTranslation().z))
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

                if (focused != null && focused == release && release instanceof SoundComponent component) {
                    engine.getSoundManager().play(component.getClickSound());
                }

                if (focused instanceof ViceButton vb) {
                    vb.executeAction();
                }
                else if (focused instanceof ViceCheckbox cb) cb.toggle();

                if (focused != null) focused.handleMouseRelease();
                focused = null;
            }
        }

        @Override
        public void onAnalog(String name, float value, float tpf) {
            if (dragging && focused != null) focused.handleMouseDrag(im.getCursorPosition());
        }
    }
}
