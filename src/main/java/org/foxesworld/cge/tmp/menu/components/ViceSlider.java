package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import org.foxesworld.cge.core.utils.ColorUtils;

public final class ViceSlider extends UIComponent implements InteractiveComponent, MenuComponent, SoundComponent {

    private static final float DEFAULT_HEIGHT = 12f;
    private static final float DEFAULT_FONT_SIZE = 24f;
    private static final float ANIMATION_SPEED = 10f;
    private static final float BORDER_THICKNESS = 2f;
    private static final float VALUE_LABEL_WIDTH = 60f;
    private static final float VALUE_LABEL_HEIGHT = 24f;
    private static final ColorRGBA DEFAULT_FILL_COLOR = new ColorRGBA(0f, 1f, 0.3f, 1f);
    private static final ColorRGBA DEFAULT_BORDER_COLOR = new ColorRGBA(1f, 1f, 1f, 0.5f);
    private static final float THUMB_RADIUS = 10f;

    private final Node sliderNode;
    private final Geometry barFill;
    private final Node borderNode;
    private final BitmapText label;
    private final BitmapText valueLabel;

    private final AssetManager assetManager;
    private final String bind;

    private float width;
    private float height = DEFAULT_HEIGHT;

    private float value;
    private float displayedValue;

    private final ColorRGBA fillColor;
    private final ColorRGBA borderColor;

    // Новый ползунок — круглый thumb
    private final Geometry thumb;
    private final Material thumbMaterial;

    private ValueChangeListener valueChangeListener;

    public interface ValueChangeListener {
        void onValueChanged(float newValue);
    }

    public ViceSlider(String id, AssetManager assetManager, String text, String hexColor, ViceButton.Style buttonStyle, float initialValue, String bind) {
        super(id);
        this.assetManager = assetManager;
        this.bind = bind;
        this.value = FastMath.clamp(initialValue, 0f, 1f);
        this.displayedValue = this.value;
        this.fillColor = hexColor.isEmpty() ? DEFAULT_FILL_COLOR.clone() : ColorUtils.fromHexString(hexColor);
        this.borderColor = DEFAULT_BORDER_COLOR.clone();

        this.sliderNode = new Node("ViceSlider: " + text);

        BitmapFont font = assetManager.loadFont(buttonStyle.fontPath());
        this.label = new BitmapText(font);
        label.setText(text.toUpperCase());
        label.setColor(ColorRGBA.White);
        label.setSize(DEFAULT_FONT_SIZE);

        this.borderNode = new Node("SliderBorder");
        createBorder();

        Material fillMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        fillMat.setColor("Color", fillColor);
        fillMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        this.barFill = new Geometry("SliderFill", new Quad(1, height - BORDER_THICKNESS * 2));
        barFill.setMaterial(fillMat);

        this.valueLabel = label.clone();
        valueLabel.setBox(new com.jme3.font.Rectangle(0, 0, VALUE_LABEL_WIDTH, VALUE_LABEL_HEIGHT));
        valueLabel.setAlignment(BitmapFont.Align.Center);
        valueLabel.setVerticalAlignment(BitmapFont.VAlign.Center);

        // Создаем thumb — сферу радиусом THUMB_RADIUS
        Sphere sphere = new Sphere(16, 16, THUMB_RADIUS);
        thumb = new Geometry("SliderThumb", sphere);
        thumbMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        thumbMaterial.setColor("Color", fillColor);
        thumbMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        thumb.setMaterial(thumbMaterial);
        // По умолчанию положим thumb в начальную позицию — при value=initialValue
        updateThumbPosition();

        sliderNode.attachChild(label);
        sliderNode.attachChild(borderNode);
        sliderNode.attachChild(barFill);
        sliderNode.attachChild(valueLabel);
        sliderNode.attachChild(thumb);

        updateLayout();
        updateVisuals();
    }

    public void setValueChangeListener(ValueChangeListener listener) {
        this.valueChangeListener = listener;
    }

    private void createBorder() {
        Material borderMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        borderMat.setColor("Color", borderColor);
        borderMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        for (String name : new String[]{"Top", "Bottom", "Left", "Right"}) {
            Geometry geom = new Geometry("Border" + name, new Quad(1, 1));
            geom.setMaterial(borderMat.clone());
            borderNode.attachChild(geom);
        }
    }

    private void updateBorder() {
        ((Quad) ((Geometry) borderNode.getChild("BorderTop")).getMesh()).updateGeometry(width, BORDER_THICKNESS);
        ((Quad) ((Geometry) borderNode.getChild("BorderBottom")).getMesh()).updateGeometry(width, BORDER_THICKNESS);
        ((Quad) ((Geometry) borderNode.getChild("BorderLeft")).getMesh()).updateGeometry(BORDER_THICKNESS, height);
        ((Quad) ((Geometry) borderNode.getChild("BorderRight")).getMesh()).updateGeometry(BORDER_THICKNESS, height);

        borderNode.getChild("BorderTop").setLocalTranslation(0, height - BORDER_THICKNESS, 0.05f);
        borderNode.getChild("BorderBottom").setLocalTranslation(0, 0, 0.05f);
        borderNode.getChild("BorderLeft").setLocalTranslation(0, 0, 0.05f);
        borderNode.getChild("BorderRight").setLocalTranslation(width - BORDER_THICKNESS, 0, 0.05f);
    }

    public void setSize(float width) {
        this.width = width;
        updateLayout();
        updateVisuals();
    }

    private void updateLayout() {
        label.setLocalTranslation(0, height + DEFAULT_FONT_SIZE + 4, 0);
        updateBorder();
        valueLabel.setLocalTranslation(width + 10f, height, 0);
        updateThumbPosition();
    }

    public void setPosition(float x, float y) {
        sliderNode.setLocalTranslation(x, y, 0);
    }

    private void updateVisuals() {
        barFill.setLocalScale(width * displayedValue, 1, 1);
        barFill.setLocalTranslation(BORDER_THICKNESS, BORDER_THICKNESS, 0.1f);
        updateValueLabel();
        updateThumbPosition();
    }

    private void updateValueLabel() {
        int percent = (int) (displayedValue * 100);
        valueLabel.setText(String.valueOf(percent));
    }

    public void setValue(float newValue) {
        if (Float.isNaN(newValue)) return;
        newValue = FastMath.clamp(newValue, 0f, 1f);
        if (Math.abs(newValue - value) > 0.0001f) {
            value = newValue;
            displayedValue = value;
            updateVisuals();
            if (valueChangeListener != null) {
                valueChangeListener.onValueChanged(value);
            }
        }
    }

    private void updateThumbPosition() {
        // thumb по горизонтали в позиции по текущему значению value
        float posX = BORDER_THICKNESS + displayedValue * width;
        // По вертикали — центрируем по высоте слайдера (примерно половина высоты)
        float posY = height / 2f;
        thumb.setLocalTranslation(posX, posY, 0.2f);
    }

    @Override
    public boolean intersects(Vector2f cursorPos) {
        Vector3f worldPos3 = sliderNode.localToWorld(Vector3f.ZERO, null);
        Vector2f worldPos = new Vector2f(worldPos3.x, worldPos3.y);
        return cursorPos.x >= worldPos.x && cursorPos.x <= worldPos.x + width
                && cursorPos.y >= worldPos.y && cursorPos.y <= worldPos.y + height;
    }

    public void handleDrag(Vector2f cursorPos) {
        Vector3f worldPos3 = sliderNode.localToWorld(Vector3f.ZERO, null);
        Vector2f worldPos = new Vector2f(worldPos3.x, worldPos3.y);
        float relativeX = cursorPos.x - worldPos.x;
        setValue(relativeX / width);
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        Vector3f worldPos3 = sliderNode.localToWorld(Vector3f.ZERO, null);
        Vector2f worldPos = new Vector2f(worldPos3.x, worldPos3.y);
        float relativeX = cursor.x - worldPos.x;
        if (relativeX >= 0 && relativeX <= width) {
            setValue(relativeX / width);
        }
    }

    @Override
    public void handleMouseDrag(Vector2f cursor) {
        handleDrag(cursor);
    }

    @Override
    public void handleMouseRelease() {
        // no-op
    }

    @Override
    public float getHeight() {
        return (int) height;
    }

    @Override
    public void setSize(float width, float height) {
        // Игнорируем высоту - она фиксирована
        setSize(width);
    }

    @Override
    public float getWidth() {
        return width;
    }

    public void updateInteraction(Vector2f localCursorPos) {
        // hover handled elsewhere
    }

    @Override
    public Node getNode() {
        return sliderNode;
    }

    @Override
    public void update(float tpf) {
        if (Math.abs(displayedValue - value) > 0.001f) {
            displayedValue = FastMath.interpolateLinear(
                    FastMath.clamp(ANIMATION_SPEED * tpf, 0f, 1f),
                    displayedValue,
                    value);
            updateVisuals();
        }
    }

    @Override
    public void setActive(boolean active) {
        // noop
    }

    @Override
    public void setHovered(boolean hovered) {
        // noop
    }
}
