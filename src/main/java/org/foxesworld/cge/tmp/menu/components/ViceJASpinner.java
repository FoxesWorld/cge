package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

/**
 * A stylish spinner/stepper UI component for cycling through a list of text options.
 * Features a central display area and increment/decrement buttons, all with hover effects.
 * Inspired by the "Vice" aesthetic.
 *
 * JA - stands for "Just An" spinner :)
 */
public final class ViceJASpinner implements InteractiveComponent, MenuComponent {

    // --- Style & Animation Constants ---
    private static final ColorRGBA COLOR_BG_DEFAULT = new ColorRGBA(0.1f, 0.1f, 0.1f, 0.7f);
    private static final ColorRGBA COLOR_BG_HOVER = new ColorRGBA(0.3f, 0.3f, 0.3f, 0.9f);
    private static final ColorRGBA COLOR_TEXT_DEFAULT = ColorRGBA.White.clone();
    private static final ColorRGBA COLOR_TEXT_HOVER = new ColorRGBA(1f, 0.2f, 0.6f, 1f).mult(1.5f); // Neon Pink Glow
    private static final float ANIMATION_SPEED = 10f;
    private static final float BUTTON_TEXT_SIZE = 28f;
    private static final float VALUE_TEXT_SIZE = 24f;

    private final Node spinnerNode;
    private final AssetManager assetManager;

    // --- Component Parts ---
    private final ViceButton buttonDecrement;
    private final ViceButton buttonIncrement;
    private final Geometry backgroundQuad;
    private final BitmapText valueText;

    // --- Data & State ---
    private final String[] options;
    private int currentIndex;
    private final Runnable onValueChange; // Callback for when the value changes
    private boolean isHoveredOnCenter = false; // Hover state for the central text area

    // --- Animation Colors ---
    private final ColorRGBA currentBgColor = new ColorRGBA();
    private final ColorRGBA currentTextColor = new ColorRGBA();

    /**
     * Creates a new Vice-styled spinner component.
     *
     * @param assetManager  The application's asset manager for loading fonts and materials.
     * @param buttonStyle   The style to apply to the increment/decrement buttons.
     * @param options       An array of strings to cycle through.
     * @param initialIndex  The starting index within the options array.
     * @param onValueChange A Runnable that gets executed whenever the selected option changes. Can be null.
     */
    public ViceJASpinner(AssetManager assetManager, ViceButton.Style buttonStyle, String[] options, int initialIndex, Runnable onValueChange) {
        this.assetManager = assetManager;
        this.options = options;
        this.currentIndex = (int) FastMath.clamp(initialIndex, 0, options.length - 1);
        this.onValueChange = onValueChange;
        this.spinnerNode = new Node("ViceJASpinner");

        // 1. Create Increment/Decrement Buttons
        // The action for each button is a lambda that calls our changeValue method.
        this.buttonDecrement = new ViceButton(assetManager, "<", buttonStyle, () -> changeValue(-1));
        this.buttonIncrement = new ViceButton(assetManager, ">", buttonStyle, () -> changeValue(1));

        // 2. Create the central background area
        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", COLOR_BG_DEFAULT);
        bgMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        this.backgroundQuad = new Geometry("SpinnerBackground", new Quad(1, 1)); // Size will be set later
        this.backgroundQuad.setMaterial(bgMat);
        currentBgColor.set(COLOR_BG_DEFAULT);

        // 3. Create the text display for the current value
        this.valueText = new BitmapText(assetManager.loadFont(buttonStyle.fontPath()));
        this.valueText.setColor(COLOR_TEXT_DEFAULT);
        currentTextColor.set(COLOR_TEXT_DEFAULT);

        // 4. Attach all parts to the main node
        spinnerNode.attachChild(backgroundQuad);
        spinnerNode.attachChild(valueText);
        spinnerNode.attachChild(buttonDecrement.getNode());
        spinnerNode.attachChild(buttonIncrement.getNode());

        // 5. Initial setup
        updateValueText();
    }

    /**
     * Changes the current value by the given direction, wrapping around the options array.
     *
     * @param direction -1 to decrement, +1 to increment.
     */
    private void changeValue(int direction) {
        currentIndex += direction;

        // Wrap around logic
        if (currentIndex < 0) {
            currentIndex = options.length - 1;
        } else if (currentIndex >= options.length) {
            currentIndex = 0;
        }

        updateValueText();

        // Trigger the callback if it exists
        if (onValueChange != null) {
            onValueChange.run();
        }
    }

    /**
     * Updates the displayed text to match the current index.
     */
    private void updateValueText() {
        if (options != null && options.length > 0) {
            valueText.setText(options[currentIndex].toUpperCase());
        }
    }

    /**
     * Sets the overall size of the component and lays out its internal parts.
     * @param width The total width of the spinner.
     * @param height The total height of the spinner.
     */
    public void setSize(float width, float height) {
        // Buttons are squares with sides equal to the component's height
        float buttonSize = height;
        buttonDecrement.setSize(buttonSize, buttonSize);
        buttonDecrement.setLabelSize(BUTTON_TEXT_SIZE);
        buttonIncrement.setSize(buttonSize, buttonSize);
        buttonIncrement.setLabelSize(BUTTON_TEXT_SIZE);

        // The central background takes the remaining width
        float bgWidth = width - (2 * buttonSize);
        ((Quad) backgroundQuad.getMesh()).updateGeometry(bgWidth, height);

        // Position the components horizontally: [ < ] [ TEXT BG ] [ > ]
        buttonDecrement.setPosition(0, 0);
        backgroundQuad.setLocalTranslation(buttonSize, 0, 0);
        buttonIncrement.setPosition(buttonSize + bgWidth, 0);

        // Configure and center the value text over its background
        valueText.setBox(new Rectangle(0, 0, bgWidth, height));
        valueText.setSize(VALUE_TEXT_SIZE);
        valueText.setAlignment(BitmapFont.Align.Center);
        valueText.setVerticalAlignment(BitmapFont.VAlign.Center);
        valueText.setLocalTranslation(buttonSize, height, 0.1f); // Position over BG, slightly in front
    }

    /**
     * Sets the position of the entire component.
     */
    public void setPosition(float x, float y) {
        spinnerNode.setLocalTranslation(x, y, 0);
    }

    public String getCurrentValue() {
        return options[currentIndex];
    }

    @Override
    public Node getNode() {
        return this.spinnerNode;
    }

    @Override
    public void update(float tpf) {
        // Animate child buttons
        buttonDecrement.update(tpf);
        buttonIncrement.update(tpf);

        // Animate the background and text color of the central area based on its hover state
        float lerpFactor = FastMath.clamp(tpf * ANIMATION_SPEED, 0, 1);

        ColorRGBA targetBgColor = isHoveredOnCenter ? COLOR_BG_HOVER : COLOR_BG_DEFAULT;
        currentBgColor.interpolateLocal(targetBgColor, lerpFactor);
        backgroundQuad.getMaterial().setColor("Color", currentBgColor);

        ColorRGBA targetTextColor = isHoveredOnCenter ? COLOR_TEXT_HOVER : COLOR_TEXT_DEFAULT;
        currentTextColor.interpolateLocal(targetTextColor, lerpFactor);
        valueText.setColor(currentTextColor);
    }

    /**
     * Distributes the hover state to the appropriate sub-component (buttons or center area).
     * @param localCursorPos The cursor position in the local coordinate space of the spinner's parent.
     */
    public void updateInteraction(Vector2f localCursorPos) {
        // Convert cursor position to be local to the spinner node itself
        Vector2f spinnerLocalCursor = localCursorPos.subtract(spinnerNode.getLocalTranslation().x, spinnerNode.getLocalTranslation().y);

        // Update hover state for each button
        buttonDecrement.setHovered(buttonDecrement.intersects(spinnerLocalCursor));
        buttonIncrement.setHovered(buttonIncrement.intersects(spinnerLocalCursor));

        // Check for hover on the central background quad
        Vector2f bgPos = new Vector2f(backgroundQuad.getLocalTranslation().getX(), backgroundQuad.getLocalTranslation().getY());
        float bgWidth = backgroundQuad.getMesh().getBound().getCenter().x * 2;
        float bgHeight = backgroundQuad.getMesh().getBound().getCenter().y * 2;

        this.isHoveredOnCenter = (spinnerLocalCursor.x >= bgPos.x && spinnerLocalCursor.x <= bgPos.x + bgWidth &&
                spinnerLocalCursor.y >= bgPos.y && spinnerLocalCursor.y <= bgPos.y + bgHeight);
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        Vector2f spinnerLocalCursor = cursor.subtract(spinnerNode.getLocalTranslation().x, spinnerNode.getLocalTranslation().y);

        if (buttonDecrement.intersects(spinnerLocalCursor)) {
            buttonDecrement.executeAction();
        } else if (buttonIncrement.intersects(spinnerLocalCursor)) {
            buttonIncrement.executeAction();
        }
    }

    @Override
    public boolean intersects(Vector2f localCursorPos) {
        // A simple bounding box check for the entire component
        float width = buttonDecrement.getWidth() + (backgroundQuad.getMesh().getBound().getCenter().x * 2) + buttonIncrement.getWidth();
        float height = buttonDecrement.getHeight();
        Vector2f pos = new Vector2f(spinnerNode.getLocalTranslation().x, spinnerNode.getLocalTranslation().y);

        return (localCursorPos.x >= pos.x && localCursorPos.x <= pos.x + width &&
                localCursorPos.y >= pos.y && localCursorPos.y <= pos.y + height);
    }

    // --- Unused Interface Methods ---
    @Override public void setActive(boolean active) { /* Not needed for this component */ }
    @Override public void setHovered(boolean hovered) { /* We use updateInteraction instead */ }
    @Override public void handleMouseDrag(Vector2f cursor) { /* Not needed for this component */ }
    @Override public void handleMouseRelease() { /* Not needed for this component */ }
}