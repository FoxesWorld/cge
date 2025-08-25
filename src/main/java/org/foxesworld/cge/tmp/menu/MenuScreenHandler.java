package org.foxesworld.cge.tmp.menu;

import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.components.UIComponent;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.SoundComponent;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.components.ViceCheckbox;

import java.util.Comparator;

public final class MenuScreenHandler {
    private static final String MAIN_MENU_XML = "assets/Interface/main_menu.xml";
    private static final String SETTINGS_MENU_XML = "assets/Interface/settings_menu.xml";
    private static final String ABOUT_XML = "assets/Interface/about.xml";

    private final Node guiNode;
    private final CalistaGameEngine engine;
    private final XmlMenuBuilder builder;
    private final InputHandler inputHandler;
    private MenuData currentMenu;

    public MenuScreenHandler(MainMenuAppState app) {
        this.engine = app.getGameEngine();
        this.guiNode = engine.getGuiNode();
        this.builder = app.getBuilder();
        this.inputHandler = new InputHandler(engine.getInputManager());
    }

    public void initialize() {
        engine.getSoundManager().play("music.theme");
        inputHandler.register();
        showMainMenu();
    }

    public void showMainMenu() { switchScreen(MAIN_MENU_XML); }

    public void showSettings() { switchScreen(SETTINGS_MENU_XML); }

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

        // New fields to support double click detection and last-click bookkeeping
        private InteractiveComponent lastClickTarget = null;
        private long lastClickTimestamp = 0L;
        private static final long DOUBLE_CLICK_MS = 300L;

        InputHandler(InputManager im) { this.im = im; }

        void setMenu(MenuData m) {
            this.menu = m;
            this.focused = null;
            this.hovered = null;
            this.dragging = false;
            this.lastClickTarget = null;
            this.lastClickTimestamp = 0L;
        }

        void register() {
            if (!im.hasMapping(CLICK)) {
                im.addMapping(CLICK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
            }
            if (!im.hasMapping(MOVE)) {
                im.addMapping(MOVE,
                        new MouseAxisTrigger(MouseInput.AXIS_X, true),
                        new MouseAxisTrigger(MouseInput.AXIS_Y, true));
            }
            im.addListener(this, CLICK, MOVE);
        }

        void unregister() { im.removeListener(this); }

        void updateHover() {
            if (menu == null) return;
            Vector2f c = im.getCursorPosition();
            InteractiveComponent now = findComponent(c);

            if (now != hovered) {
                if (hovered != null) {
                    try { hovered.setHovered(false); } catch (Throwable ignored) {}
                    try { hovered.handleMouseExit(c); } catch (Throwable ignored) {}
                }
                if (now != null) {
                    try { now.setHovered(true); } catch (Throwable ignored) {}
                    try { now.handleMouseEnter(c); } catch (Throwable ignored) {}
                    if (now instanceof SoundComponent component) {
                        engine.getSoundManager().play(component.getHoverSound());
                    }
                }
                hovered = now;
            } else {
                if (hovered != null) {
                    try { hovered.handleMouseMove(c); } catch (Throwable ignored) {}
                }
            }
        }

        private InteractiveComponent findComponent(Vector2f c) {
            if (menu == null) return null;
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

                if (focused != null) {
                    try { focused.handleMousePress(c); } catch (Throwable ignored) {}

                    long nowMs = System.currentTimeMillis();
                    if (lastClickTarget == focused && (nowMs - lastClickTimestamp) <= DOUBLE_CLICK_MS) {
                        try { focused.handleMouseDoubleClick(c); } catch (Throwable ignored) {}
                        // reset lastClick to avoid triple-fire
                        lastClickTarget = null;
                        lastClickTimestamp = 0L;
                    } else {
                        lastClickTarget = focused;
                        lastClickTimestamp = nowMs;
                    }
                }
            } else {
                dragging = false;
                InteractiveComponent release = findComponent(c);

                if (focused != null) {
                    if (focused == release) {
                        if (release instanceof SoundComponent component) {
                            engine.getSoundManager().play(component.getClickSound());
                        }

                        if (focused instanceof ViceButton vb) {
                            vb.executeAction();
                        } else if (focused instanceof ViceCheckbox cb) {
                            cb.toggle();
                        }

                        try { focused.handleMouseClick(c); } catch (Throwable ignored) {}
                    }

                    try { focused.handleMouseRelease(c); } catch (Throwable ignored) {}
                    focused = null;
                }
            }
        }

        @Override
        public void onAnalog(String name, float value, float tpf) {
            Vector2f c = im.getCursorPosition();
            // continuous move -> let hovered components know about mouse movement
            if (hovered != null) {
                try { hovered.handleMouseMove(c); } catch (Throwable ignored) {}
            }

            if (dragging && focused != null) {
                try { focused.handleMouseDrag(c); } catch (Throwable ignored) {}
            }
        }
    }
}
