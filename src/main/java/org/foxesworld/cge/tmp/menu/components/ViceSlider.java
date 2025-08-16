package org.foxesworld.cge.tmp.menu.components;

import com.atr.jme.font.shape.TrueTypeContainer;
import com.atr.jme.font.util.Style;
import com.jme3.asset.AssetManager;
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
import org.foxesworld.cge.core.io.TTFrenderer;
import org.foxesworld.cge.core.utils.ColorUtils;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.SoundComponent;
import org.foxesworld.cge.tmp.menu.xml.SliderXml;

/**
 * ViceSlider с hover + drag эффектами:
 * - hover: мягкое увеличение thumb + подсветка
 * - drag: дополнительное увеличение, подъём thumb, усиление подсветки
 * - плавные интерполяции
 */
public final class ViceSlider extends UIComponent implements InteractiveComponent, SoundComponent {

    private static final float DEFAULT_HEIGHT = 12f;
    private static final float DEFAULT_FONT_SIZE = 24f;
    private static final float ANIMATION_SPEED = 10f;
    private static final float BORDER_THICKNESS = 2f;
    private static final ColorRGBA DEFAULT_FILL_COLOR = new ColorRGBA(0f, 1f, 0.3f, 1f);
    private static final ColorRGBA DEFAULT_BORDER_COLOR = new ColorRGBA(1f, 1f, 1f, 0.5f);
    private static final float THUMB_RADIUS = 10f;

    // hover tuning
    private static final float HOVER_ANIM_SPEED = 10f;
    private static final float HOVER_SCALE = 1.18f;
    private static final float HOVER_BRIGHTNESS = 0.2f;

    // drag tuning
    private static final float DRAG_ANIM_SPEED = 16f;
    private static final float DRAG_SCALE = 1.35f;
    private static final float DRAG_RAISE = 6f;
    private static final float DRAG_BRIGHTNESS = 0.45f;

    private final Geometry barFill;
    private final Node borderNode;
    private final TTFrenderer ttFrenderer;
    private TrueTypeContainer ttc;

    private final AssetManager assetManager;

    private float value;
    private float displayedValue;

    private final ColorRGBA fillColor;
    private final ColorRGBA borderColor;

    // runtime colors (interpolated)
    private final ColorRGBA currentFillColor;
    private final ColorRGBA currentBorderColor;

    // materials
    private final Material fillMat;
    private final Material thumbMaterial;

    // thumb geometry
    private final Geometry thumb;

    private ValueChangeListener valueChangeListener;
    private boolean autoSize = true;

    // hover/drag state
    private boolean hovered = false;
    private float hoverAnim = 0f; // 0..1
    private boolean dragging = false;
    private float dragAnim = 0f; // 0..1

    private float timeAccumulator = 0f;

    public interface ValueChangeListener {
        void onValueChanged(float newValue);
    }

    public ViceSlider(AssetManager assetManager, SliderXml sliderXml) {
        super(sliderXml.id);
        this.assetManager = assetManager;
        this.bind = sliderXml.bind;
        this.displayedValue = this.value;
        this.fillColor = sliderXml.fillColor.isEmpty() ? DEFAULT_FILL_COLOR.clone() : ColorUtils.fromHexString(sliderXml.fillColor);
        this.borderColor = DEFAULT_BORDER_COLOR.clone();
        this.currentFillColor = this.fillColor.clone();
        this.currentBorderColor = this.borderColor.clone();
        setHeight(DEFAULT_HEIGHT);

        ttFrenderer = new TTFrenderer(assetManager);
        this.borderNode = new Node("SliderBorder");
        createBorder();

        // fill material (keep as field to change color)
        this.fillMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        fillMat.setColor("Color", currentFillColor);
        fillMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        this.barFill = new Geometry("SliderFill", new Quad(1, getHeight() - BORDER_THICKNESS * 2));
        barFill.setMaterial(fillMat);

        ttFrenderer.generateFont("assets/Interface/fonts/FSElliotPro.ttf", Style.Plain, (int) DEFAULT_FONT_SIZE);
        ttFrenderer.generateText(ColorUtils.fromHexString(sliderXml.color), sliderXml.text);
        ttc = ttFrenderer.getTextGeometry();

        // thumb — сферка
        Sphere sphere = new Sphere(16, 16, THUMB_RADIUS);
        thumb = new Geometry("SliderThumb", sphere);
        thumbMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        thumbMaterial.setColor("Color", currentFillColor);
        thumbMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        thumb.setMaterial(thumbMaterial);
        updateThumbPosition();

        this.attachChild(ttc);
        this.attachChild(borderNode);
        this.attachChild(barFill);
        this.attachChild(thumb);

        updateAdaptiveSize();
        updateVisuals();
    }

    public void setValueChangeListener(ValueChangeListener listener) {
        this.valueChangeListener = listener;
    }

    private void createBorder() {
        Material borderMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        borderMat.setColor("Color", currentBorderColor);
        borderMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        for (String name : new String[]{"Top", "Bottom", "Left", "Right"}) {
            Geometry geom = new Geometry("Border" + name, new Quad(1, 1));
            geom.setMaterial(borderMat.clone());
            borderNode.attachChild(geom);
        }
    }

    private void updateBorder() {
        ((Quad) ((Geometry) borderNode.getChild("BorderTop")).getMesh()).updateGeometry(getWidth(), BORDER_THICKNESS);
        ((Quad) ((Geometry) borderNode.getChild("BorderBottom")).getMesh()).updateGeometry(getWidth(), BORDER_THICKNESS);
        ((Quad) ((Geometry) borderNode.getChild("BorderLeft")).getMesh()).updateGeometry(BORDER_THICKNESS, getHeight());
        ((Quad) ((Geometry) borderNode.getChild("BorderRight")).getMesh()).updateGeometry(BORDER_THICKNESS, getHeight());

        borderNode.getChild("BorderTop").setLocalTranslation(0, getHeight() - BORDER_THICKNESS, 0.05f);
        borderNode.getChild("BorderBottom").setLocalTranslation(0, 0, 0.05f);
        borderNode.getChild("BorderLeft").setLocalTranslation(0, 0, 0.05f);
        borderNode.getChild("BorderRight").setLocalTranslation(getWidth() - BORDER_THICKNESS, 0, 0.05f);

        // apply interpolated border color to all border parts
        for (int i = 0; i < borderNode.getQuantity(); i++) {
            Geometry g = (Geometry) borderNode.getChild(i);
            Material m = g.getMaterial();
            m.setColor("Color", currentBorderColor);
        }
    }

    private void updateAdaptiveSize() {
        float textWidth = ttc.getWidth();
        float minBarWidth = 100f;
        setWidth(Math.max(minBarWidth, textWidth + THUMB_RADIUS * 2 + 20f));
        updateLayout();
    }

    public void setSize(float width) {
        setWidth(width);
        autoSize = false;
        updateLayout();
        updateVisuals();
    }

    private void updateLayout() {
        // text position: placed to the right of the bar
        ttc.setLocalTranslation(getWidth() + 10f, getHeight(), 0);
        updateBorder();
        updateThumbPosition();
    }

    public void setPosition(float x, float y) {
        this.setLocalTranslation(x, y, 0);
    }

    private void updateVisuals() {
        // interpolate fill width by displayedValue
        barFill.setLocalScale(getWidth() * displayedValue, 1, 1);
        barFill.setLocalTranslation(BORDER_THICKNESS, BORDER_THICKNESS, 0.1f);
        // color & thumb scale handled in update()
        updateValueLabel();
        updateThumbPosition();
    }

    private void updateValueLabel() {
        int percent = (int) (displayedValue * 100);
        ttc.setText(String.valueOf(percent));
        if (autoSize) updateAdaptiveSize();
    }

    public void setValue(float newValue) {
        if (Float.isNaN(newValue)) return;
        newValue = FastMath.clamp(newValue, 0f, 1f);
        if (Math.abs(newValue - value) > 0.0001f) {
            value = newValue;
            displayedValue = value;
            updateValueLabel();
            updateVisuals();
            if (valueChangeListener != null) {
                valueChangeListener.onValueChanged(value);
            }
        }
    }

    private void updateThumbPosition() {
        float posX = BORDER_THICKNESS + displayedValue * getWidth();
        float posY = getHeight() / 2f;
        // if dragging, raise by dragAnim (applied in update())
        thumb.setLocalTranslation(posX, posY, 0.2f);
    }

    @Override
    public boolean intersects(Vector2f cursorPos) {
        Vector3f worldPos3 = this.localToWorld(Vector3f.ZERO, null);
        Vector2f worldPos = new Vector2f(worldPos3.x, worldPos3.y);
        return cursorPos.x >= worldPos.x && cursorPos.x <= worldPos.x + getWidth()
                && cursorPos.y >= worldPos.y && cursorPos.y <= worldPos.y + getHeight();
    }

    public void handleDrag(Vector2f cursorPos) {
        Vector3f worldPos3 = this.localToWorld(Vector3f.ZERO, null);
        Vector2f worldPos = new Vector2f(worldPos3.x, worldPos3.y);
        float relativeX = cursorPos.x - worldPos.x;
        setValue(relativeX / getWidth());
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        Vector3f worldPos3 = this.localToWorld(Vector3f.ZERO, null);
        Vector2f worldPos = new Vector2f(worldPos3.x, worldPos3.y);
        float relativeX = cursor.x - worldPos.x;
        if (relativeX >= 0 && relativeX <= getWidth()) {
            setValue(relativeX / getWidth());
            // start dragging immediately
            dragging = true;
        }
    }

    @Override
    public void handleMouseDrag(Vector2f cursor) {
        if (!dragging) {
            // be robust: if drag started outside press, start dragging when moving inside
            dragging = true;
        }
        handleDrag(cursor);
    }

    @Override
    public void handleMouseRelease() {
        // stop dragging
        dragging = false;
    }

    @Override
    public void setSize(float width, float height) {
        setSize(width);
    }

    @Override
    public void update(float tpf) {
        timeAccumulator += tpf;
        // animate displayed value towards value
        if (Math.abs(displayedValue - value) > 0.001f) {
            displayedValue = FastMath.interpolateLinear(
                    FastMath.clamp(ANIMATION_SPEED * tpf, 0f, 1f),
                    displayedValue,
                    value);
            updateVisuals();
        }

        // hover animation
        float hoverTarget = hovered ? 1f : 0f;
        if (Math.abs(hoverAnim - hoverTarget) > 0.001f) {
            hoverAnim = FastMath.interpolateLinear(
                    FastMath.clamp(HOVER_ANIM_SPEED * tpf, 0f, 1f),
                    hoverAnim,
                    hoverTarget);
        }

        // drag animation (priority over hover for some visuals)
        float dragTarget = dragging ? 1f : 0f;
        if (Math.abs(dragAnim - dragTarget) > 0.001f) {
            dragAnim = FastMath.interpolateLinear(
                    FastMath.clamp(DRAG_ANIM_SPEED * tpf, 0f, 1f),
                    dragAnim,
                    dragTarget);
        }

        // combine scales: base(1) -> hover -> drag
        float hoverScalePart = (HOVER_SCALE - 1f) * hoverAnim;
        float dragScalePart = (DRAG_SCALE - 1f) * dragAnim;
        float combinedScale = 1f + hoverScalePart + dragScalePart;
        thumb.setLocalScale(combinedScale);

        // vertical raise while dragging (smooth)
        float raise = DRAG_RAISE * dragAnim;
        float posX = BORDER_THICKNESS + displayedValue * getWidth();
        float posY = getHeight() / 2f + raise;
        thumb.setLocalTranslation(posX, posY, 0.25f + 0.01f * dragAnim);

        // update colors: fill and border get brighter on hover/drag
        float brightnessFactor = (HOVER_BRIGHTNESS * hoverAnim) + (DRAG_BRIGHTNESS * dragAnim);
        ColorRGBA targetFill = blendTowardWhite(fillColor, brightnessFactor);
        currentFillColor.interpolateLocal(targetFill, 1f);
        try { fillMat.setColor("Color", currentFillColor); } catch (Exception ignored) {}

        ColorRGBA targetBorder = blendTowardWhite(borderColor, Math.min(0.6f, 0.4f * hoverAnim + 0.8f * dragAnim));
        currentBorderColor.interpolateLocal(targetBorder, 1f);
        for (int i = 0; i < borderNode.getQuantity(); i++) {
            Geometry g = (Geometry) borderNode.getChild(i);
            try {
                g.getMaterial().setColor("Color", currentBorderColor);
            } catch (Exception ignored) {}
        }

        // thumb material color sync
        try { thumbMaterial.setColor("Color", currentFillColor); } catch (Exception ignored) {}

        // keep visuals in sync
        updateVisuals();
    }

    @Override
    public void setActive(boolean active) {
    }

    @Override
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    /**
     * Blend color slightly toward white by factor [0..1].
     */
    private static ColorRGBA blendTowardWhite(ColorRGBA src, float factor) {
        factor = FastMath.clamp(factor, 0f, 1f);
        return new ColorRGBA(
                src.r + (1f - src.r) * factor,
                src.g + (1f - src.g) * factor,
                src.b + (1f - src.b) * factor,
                src.a
        );
    }

    @Override
    public String getHoverSound(){
        return "ui.hover";
    }

    @Override
    public String getClickSound() {
        return "ui.press";
    }

    public float getValue() {
        return value;
    }
}
