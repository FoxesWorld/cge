package org.foxesworld.cge.modules.ui.novaUi.updater;

import com.jme3.app.Application;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.button.ButtonElement;

import java.util.Collection;

/**
 * Manages user interactions like mouse hover and clicks for a set of UI elements.
 * This class replaces the old, multi-purpose NovaUIUpdater.
 */
public class InteractionManager implements com.jme3.input.controls.ActionListener {

    private final InputManager inputManager;
    private final Node rootUINode;
    private Collection<UIElement> elements;

    private UIElement lastHoveredElement = null;
    private UIElement pressedElement = null;

    private static final String MOUSE_LEFT_CLICK = "NovaUI_LeftClick";

    public InteractionManager(Application app, Node rootUINode) {
        this.inputManager = app.getInputManager();
        this.rootUINode = rootUINode;

        if (!inputManager.hasMapping(MOUSE_LEFT_CLICK)) {
            inputManager.addMapping(MOUSE_LEFT_CLICK, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        }
        inputManager.addListener(this, MOUSE_LEFT_CLICK);
    }

    public void setElements(Collection<UIElement> elements) {
        this.elements = elements;
    }

    public void update(float tpf) {
        if (elements == null || elements.isEmpty()) return;

        Vector2f click2d = inputManager.getCursorPosition();
        UIElement currentHovered = findElementAt(click2d);

        // --- Handle Hover State Changes ---
        if (currentHovered != lastHoveredElement) {
            if (lastHoveredElement instanceof ButtonElement btn) {
                btn.onHoverLeave();
            }
            if (currentHovered instanceof ButtonElement btn) {
                btn.onHoverEnter();
            }
            lastHoveredElement = currentHovered;
        }

        // The press/release logic is handled by the ActionListener below
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (MOUSE_LEFT_CLICK.equals(name)) {
            if (isPressed) {
                // Mouse button was pressed
                pressedElement = lastHoveredElement; // "Arm" the element that was under the cursor
                if (pressedElement instanceof ButtonElement btn) {
                    btn.onPress();
                }
            } else {
                // Mouse button was released
                if (pressedElement != null) {
                    // Check if the cursor is still over the SAME element
                    if (pressedElement == lastHoveredElement && pressedElement instanceof ButtonElement btn) {
                        btn.onRelease(); // This will trigger the click action
                    }
                    pressedElement = null;
                }
            }
        }
    }

    private UIElement findElementAt(Vector2f screenPos) {
        Ray ray = new Ray();
        // The y-coordinate needs to be inverted for some reason with pickRay
        Vector2f correctedPos = new Vector2f(screenPos.x, rootUINode.getLocalTranslation().y - screenPos.y);

        // This method is simple but may not be the most performant.
        // It relies on JME's picking system.
        CollisionResults results = new CollisionResults();
        // A simplified ray for 2D GUI picking might be better, but this works.
        // This part might need adjustment depending on your camera setup.
        // For a 2D GUI, a simpler AABB check is often better.

        // Let's implement a simpler AABB check which is more reliable for 2D UI
        for (UIElement element : elements) {
            Node n = element.getNode();
            Vector2f worldPos = new Vector2f(n.getWorldTranslation().x, n.getWorldTranslation().y);
            float w = element.getWidth();
            float h = element.getHeight();

            if (screenPos.x >= worldPos.x && screenPos.x <= worldPos.x + w &&
                    screenPos.y >= worldPos.y && screenPos.y <= worldPos.y + h) {
                // We found a match. In a real scenario, you'd want the topmost one (highest z-value).
                // For now, the first match is fine.
                return element;
            }
        }
        return null; // Nothing found
    }

    public void cleanup() {
        if(inputManager.hasMapping(MOUSE_LEFT_CLICK)) {
            inputManager.removeListener(this);
        }
    }
}