package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.components.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.MenuComponent;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.components.ViceCheckbox;

import java.util.Comparator;
import java.util.List;

/**
 * Manages the state, rendering, and input for all 2D UI screens in the menu.
 * This class acts as the central controller for the UI, switching between different
 * XML-defined screens and delegating events.
 */
public class MenuScreenHandler {
    private static final String MAIN_MENU_XML = "ui/main_menu.xml";
    private static final String SETTINGS_MENU_XML = "ui/settings_menu.xml";

    private final Application app;
    private final Node guiNode;
    private final XmlMenuBuilder menuBuilder;
    private final InputHandler inputHandler;
    private MenuData currentMenuData;

    public MenuScreenHandler(Application app) {
        this.app = app;
        this.guiNode = ((SimpleApplication) app).getGuiNode();
        this.menuBuilder = new XmlMenuBuilder(app, ViceButton.Style.getViceStyle());
        this.inputHandler = new InputHandler(app.getInputManager());
    }

    public void initialize() {
        inputHandler.registerListeners();
        showMainMenu();
    }

    public void showMainMenu() {
        switchToScreen(MAIN_MENU_XML);
    }

    public void showSettings() {
        switchToScreen(SETTINGS_MENU_XML);
    }

    private void switchToScreen(String xmlPath) {
        if (currentMenuData != null && currentMenuData.uiNode() != null) {
            currentMenuData.uiNode().removeFromParent();
        }
        this.currentMenuData = menuBuilder.build(xmlPath);
        this.inputHandler.setCurrentMenuData(currentMenuData);
        guiNode.attachChild(currentMenuData.uiNode());
    }

    public void update(float tpf) {
        if (currentMenuData != null) {
            currentMenuData.allComponents().forEach(c -> c.update(tpf));
            inputHandler.updateInteraction();
        }
    }

    public void cleanup() {
        if (currentMenuData != null && currentMenuData.uiNode() != null) {
            currentMenuData.uiNode().removeFromParent();
        }
        inputHandler.unregisterListeners();
    }

    /**
     * A dedicated inner class to handle all menu-related input.
     */
    private static class InputHandler implements ActionListener, AnalogListener {
        private static final String MOUSE_CLICK = "MenuMouseClick";
        private static final String MOUSE_MOVE = "MenuMouseMove";

        private final InputManager inputManager;
        private MenuData currentMenuData;
        private MenuComponent focusedComponent; // Component the mouse was pressed on
        private MenuComponent hoveredComponent; // Component the mouse is currently over
        private boolean isMouseDragging = false;

        public InputHandler(InputManager inputManager) { this.inputManager = inputManager; }
        public void setCurrentMenuData(MenuData menuData) { this.currentMenuData = menuData; this.focusedComponent = null; }

        public void registerListeners() {
            if (!inputManager.hasMapping(MOUSE_CLICK)) inputManager.addMapping(MOUSE_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
            if (!inputManager.hasMapping(MOUSE_MOVE)) inputManager.addMapping(MOUSE_MOVE, new com.jme3.input.controls.MouseAxisTrigger(MouseInput.AXIS_X, true), new com.jme3.input.controls.MouseAxisTrigger(MouseInput.AXIS_Y, true));
            inputManager.addListener(this, MOUSE_CLICK, MOUSE_MOVE);
        }
        public void unregisterListeners() { inputManager.removeListener(this); }

        public void updateInteraction() {
            if (currentMenuData == null) return;
            Vector2f cursor = inputManager.getCursorPosition();

            MenuComponent newlyHovered = findHoveredComponent(cursor);
            if (newlyHovered != hoveredComponent) {
                // Reset old hover state
                if (hoveredComponent instanceof InteractiveComponent ic) ic.setHovered(false);
                // Set new hover state
                if (newlyHovered instanceof InteractiveComponent ic) ic.setHovered(true);
                hoveredComponent = newlyHovered;
            }
        }

        private MenuComponent findHoveredComponent(Vector2f cursor) {
            if (currentMenuData == null) return null;
            // Search hierarchically: check containers first, and let them check their children.
            // Sort by Z-order to check topmost components first.
            return currentMenuData.allComponents().stream()
                    .sorted(Comparator.comparing(c -> -c.getNode().getLocalTranslation().z))
                    .filter(c -> c.intersects(cursor))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public void onAction(String name, boolean isPressed, float tpf) {
            Vector2f cursor = inputManager.getCursorPosition();

            if (isPressed) {
                isMouseDragging = true;
                focusedComponent = findHoveredComponent(cursor); // Remember what we clicked on
                if (focusedComponent instanceof InteractiveComponent ic) {
                    ic.handleMousePress(cursor);
                }
            } else {
                isMouseDragging = false;
                MenuComponent releaseComponent = findHoveredComponent(cursor);

                // If we released on the same component we clicked on, it's a "click"
                if (focusedComponent != null && focusedComponent == releaseComponent) {
                    if (focusedComponent instanceof ViceButton button) button.executeAction();
                    else if (focusedComponent instanceof ViceCheckbox checkbox) checkbox.toggle();
                }

                // Always send release event to the component we originally clicked on
                if (focusedComponent instanceof InteractiveComponent ic) {
                    ic.handleMouseRelease();
                }
                focusedComponent = null;
            }
        }

        @Override
        public void onAnalog(String name, float value, float tpf) {
            // Drag events are only sent to the component that was initially clicked on
            if (isMouseDragging && focusedComponent instanceof InteractiveComponent ic) {
                ic.handleMouseDrag(inputManager.getCursorPosition());
            }
        }
    }
}