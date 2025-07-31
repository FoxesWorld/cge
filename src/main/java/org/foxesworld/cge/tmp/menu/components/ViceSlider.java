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
import org.foxesworld.cge.CalistaGameEngine;

/**
 * A stylized slider UI component with a border, hover effects, and a synchronized numeric spinner.
 */
public final class ViceSlider implements InteractiveComponent, MenuComponent {

    // --- Style & Animation Constants ---
    private static final ColorRGBA COLOR_BORDER_DEFAULT = new ColorRGBA(0.3f, 0.3f, 0.3f, 0.8f);
    private static final ColorRGBA COLOR_BORDER_HOVER = new ColorRGBA(0.6f, 0.6f, 0.6f, 1f);
    private static final ColorRGBA COLOR_FILL_DEFAULT = ColorRGBA.White.clone();
    private static final ColorRGBA COLOR_FILL_HOVER = new ColorRGBA(1f, 0.2f, 0.6f, 1f).mult(1.5f);
    private static final float ANIMATION_SPEED = 10f;
    private static final float SPINNER_GAP = 20f;
    private static final float SPINNER_BUTTON_SIZE = 24f;
    private static final float BORDER_THICKNESS = 1f;
    private static final float STEP_VALUE = 0.01f; // 1% increment/decrement

    private final Node sliderNode;
    private final Geometry barFill;
    private final Node borderNode;
    private final BitmapText label;
    private final BitmapText valueLabel; // For the spinner
    private final ViceButton spinnerDecrement;
    private final ViceButton spinnerIncrement;
    private final String bind;
    private final AssetManager assetManager;

    private float width, height = 12f;
    private float value;
    private boolean isHovered = false;

    private final ColorRGBA currentFillColor = new ColorRGBA();
    private final ColorRGBA currentBorderColor = new ColorRGBA();

    public ViceSlider(AssetManager assetManager, String text, ViceButton.Style buttonStyle, float initialValue, String bind) {
        this.value = initialValue;
        this.assetManager = assetManager;
        this.bind = bind;
        this.sliderNode = new Node("ViceSlider: " + text);

        this.label = new BitmapText(assetManager.loadFont(buttonStyle.fontPath()));
        label.setText(text.toUpperCase());
        label.setColor(ColorRGBA.White);
        label.setSize(24f);

        this.borderNode = new Node("SliderBorder");
        createBorder();

        Material fillMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        fillMat.setColor("Color", COLOR_FILL_DEFAULT);
        fillMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        this.barFill = new Geometry("SliderFill", new Quad(1, height - BORDER_THICKNESS * 2));
        barFill.setMaterial(fillMat);
        currentFillColor.set(COLOR_FILL_DEFAULT);

        // --- Spinner Setup ---
        this.valueLabel = (BitmapText) label.clone();
        valueLabel.setBox(new Rectangle(0, 0, 60, 30)); // Give it a fixed width box
        valueLabel.setAlignment(BitmapFont.Align.Center);

        ViceButton.Style spinnerStyle = buttonStyle; // Can be a different style if needed
        this.spinnerDecrement = new ViceButton(assetManager, "<", spinnerStyle, () -> setValue(value - STEP_VALUE));
        this.spinnerIncrement = new ViceButton(assetManager, ">", spinnerStyle, () -> setValue(value + STEP_VALUE));

        sliderNode.attachChild(label);
        sliderNode.attachChild(borderNode);
        sliderNode.attachChild(barFill);
        sliderNode.attachChild(valueLabel);
        sliderNode.attachChild(spinnerDecrement.getNode());
        sliderNode.attachChild(spinnerIncrement.getNode());

        updateValueLabel();
    }

    private void createBorder() {
        Material borderMat = new Material(this.assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        borderMat.setColor("Color", COLOR_BORDER_DEFAULT);
        currentBorderColor.set(COLOR_BORDER_DEFAULT);

        // Создаем геометрию для каждой части рамки
        Geometry top = new Geometry("BorderTop", new Quad(1, BORDER_THICKNESS));
        Geometry bottom = new Geometry("BorderBottom", new Quad(1, BORDER_THICKNESS));
        Geometry left = new Geometry("BorderLeft", new Quad(BORDER_THICKNESS, 1));
        Geometry right = new Geometry("BorderRight", new Quad(BORDER_THICKNESS, 1));

        // Применяем ОДИН И ТОТ ЖЕ материал ко всем частям.
        // Мы будем менять цвет прямо в материале, и он обновится для всех.
        top.setMaterial(borderMat);
        bottom.setMaterial(borderMat);
        left.setMaterial(borderMat);
        right.setMaterial(borderMat);

        borderNode.attachChild(top);
        borderNode.attachChild(bottom);
        borderNode.attachChild(left);
        borderNode.attachChild(right);
    }

    /**
     * Updates the size and position of the four quads that form the border.
     */
    private void updateBorder() {
        // ИСПРАВЛЕНИЕ: Получаем дочерние элементы как Geometry, а не Node
        Geometry top = (Geometry) borderNode.getChild("BorderTop");
        Geometry bottom = (Geometry) borderNode.getChild("BorderBottom");
        Geometry left = (Geometry) borderNode.getChild("BorderLeft");
        Geometry right = (Geometry) borderNode.getChild("BorderRight");

        // Обновляем геометрию (меш) каждого прямоугольника
        ((Quad) top.getMesh()).updateGeometry(width, BORDER_THICKNESS);
        ((Quad) bottom.getMesh()).updateGeometry(width, BORDER_THICKNESS);
        ((Quad) left.getMesh()).updateGeometry(BORDER_THICKNESS, height);
        ((Quad) right.getMesh()).updateGeometry(BORDER_THICKNESS, height);

        // Позиционируем каждую часть рамки, чтобы они образовали прямоугольник
        top.setLocalTranslation(0, height - BORDER_THICKNESS, 0.05f);
        bottom.setLocalTranslation(0, 0, 0.05f);
        left.setLocalTranslation(0, 0, 0.05f);
        right.setLocalTranslation(width - BORDER_THICKNESS, 0, 0.05f);
    }

    public void setSize(float width) {
        this.width = width;
        label.setLocalTranslation(0, height + 28, 0);
        //updateBorder();
        updateFill();
        layoutSpinner();
    }

// В классе ViceSlider.java

    /**
     * Positions and configures the numeric spinner components (decrement button,
     * value label, and increment button) to the right of the slider bar.
     */
    private void layoutSpinner() {
        // Начальная позиция для первого элемента спиннера (кнопки '<')
        float spinnerStartX = width + SPINNER_GAP;

        // 1. Настраиваем и позиционируем кнопку декремента ("<")
        spinnerDecrement.setSize(SPINNER_BUTTON_SIZE, SPINNER_BUTTON_SIZE);
        spinnerDecrement.setLabelSize(20f);
        spinnerDecrement.setPosition(spinnerStartX, height / 2f - SPINNER_BUTTON_SIZE / 2f);

        // 2. Настраиваем текстовую метку со значением
        // Устанавливаем ширину "слота" для текста, чтобы он не "прыгал"
        float valueLabelWidth = 60f; // Фиксированная ширина для текста (e.g., "100")
        valueLabel.setBox(new Rectangle(0, 0, valueLabelWidth, SPINNER_BUTTON_SIZE));
        valueLabel.setAlignment(BitmapFont.Align.Center);
        valueLabel.setVerticalAlignment(BitmapFont.VAlign.Center); // Центрируем и по вертикали

        // Позиционируем текстовую метку после кнопки '<'
        float valueLabelX = spinnerStartX + SPINNER_BUTTON_SIZE;
        valueLabel.setLocalTranslation(valueLabelX, height, 0);

        // 3. Настраиваем и позиционируем кнопку инкремента (">")
        spinnerIncrement.setSize(SPINNER_BUTTON_SIZE, SPINNER_BUTTON_SIZE);
        spinnerIncrement.setLabelSize(20f);
        // Позиционируем после текстовой метки
        spinnerIncrement.setPosition(valueLabelX + valueLabelWidth, height / 2f - SPINNER_BUTTON_SIZE / 2f);
    }

    public void setPosition(float x, float y) {
        sliderNode.setLocalTranslation(x, y, 0);
    }

    private void updateFill() {
        barFill.setLocalScale(width * value, 1, 1);
        barFill.setLocalTranslation(BORDER_THICKNESS, BORDER_THICKNESS, 0.1f);
    }

    private void updateValueLabel() {
        int percentValue = (int) (value * 100);
        valueLabel.setText(String.valueOf(percentValue));
    }

    public void setValue(float newValue) {
        this.value = FastMath.clamp(newValue, 0f, 1f);
        updateFill();
        updateValueLabel();
        System.out.println("Setting '" + bind + "' changed to: " + this.value);
    }

    @Override
    public boolean intersects(Vector2f localCursorPos) {
        Vector2f pos = new Vector2f(sliderNode.getLocalTranslation().x, sliderNode.getLocalTranslation().y);
        // Check main bar, then spinner buttons
        boolean inBar = localCursorPos.x >= pos.x && localCursorPos.x <= pos.x + width &&
                localCursorPos.y >= pos.y && localCursorPos.y <= pos.y + height;
        return inBar || spinnerDecrement.intersects(localCursorPos) || spinnerIncrement.intersects(localCursorPos);
    }

    public void handleDrag(Vector2f localCursorPos) {
        float relativeX = localCursorPos.x - sliderNode.getLocalTranslation().x;
        setValue(relativeX / width);
    }

    @Override
    public Node getNode() {
        return this.sliderNode;
    }

    @Override
    public void update(float tpf) {
        float lerpFactor = FastMath.clamp(tpf * ANIMATION_SPEED, 0, 1);

        ColorRGBA targetBorderColor = isHovered ? COLOR_BORDER_HOVER : COLOR_BORDER_DEFAULT;
        currentBorderColor.interpolateLocal(targetBorderColor, lerpFactor);
        ((Geometry)borderNode.getChild("BorderTop")).getMaterial().setColor("Color", currentBorderColor);
        // ... set color for other border parts ...

        ColorRGBA targetFillColor = isHovered ? COLOR_FILL_HOVER : COLOR_FILL_DEFAULT;
        currentFillColor.interpolateLocal(targetFillColor, lerpFactor);
        barFill.getMaterial().setColor("Color", currentFillColor);

        spinnerDecrement.update(tpf);
        spinnerIncrement.update(tpf);
    }

    @Override
    public void setActive(boolean active) {

    }

    @Override
    public void setHovered(boolean hovered) {
        this.isHovered = hovered;
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        if(spinnerDecrement.intersects(cursor)) spinnerDecrement.executeAction();
        else if(spinnerIncrement.intersects(cursor)) spinnerIncrement.executeAction();
    }

    @Override
    public void handleMouseDrag(Vector2f cursor) {

    }

    @Override
    public void handleMouseRelease() {

    }

    /**
     * Updates the interaction state of the component based on the cursor position.
     * @param localCursorPos The cursor position in the local coordinate space of this slider's parent node.
     */

    public void updateInteraction(Vector2f localCursorPos) {
        // Проверяем наведение на основную полосу
        Vector2f pos = new Vector2f(sliderNode.getLocalTranslation().x, sliderNode.getLocalTranslation().y);
        this.isHovered = localCursorPos.x >= pos.x && localCursorPos.x <= pos.x + width &&
                localCursorPos.y >= pos.y && localCursorPos.y <= pos.y + height;

        // Проверяем наведение на кнопки спиннера (передавая им локальные координаты относительно ИХ родителя - sliderNode)
        Vector2f spinnerLocalCursor = localCursorPos.subtract(pos);
        spinnerDecrement.setHovered(spinnerDecrement.intersects(spinnerLocalCursor));
        spinnerIncrement.setHovered(spinnerIncrement.intersects(spinnerLocalCursor));
    }
}