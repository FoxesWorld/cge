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
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.ui.Picture;

import static com.jme3.math.Vector3f.UNIT_XYZ;

public final class ViceCheckbox implements InteractiveComponent, MenuComponent {

    private static final float ANIM_SPEED = 12f;
    private static final float LABEL_GAP_PCT = 0.5f;

    private static final ColorRGBA COLOR_FRAME = new ColorRGBA(0.8f, 0.8f, 0.8f, 0.9f);
    private static final ColorRGBA COLOR_HOVER = new ColorRGBA(1f, 1f, 1f, 1f);
    private static final ColorRGBA COLOR_CHECK = new ColorRGBA(0.2f, 0.8f, 0.2f, 1f);
    private static final ColorRGBA COLOR_DISABLED_FRAME = new ColorRGBA(0.5f, 0.5f, 0.5f, 0.5f);

    private static final String ICON_FRAME_PATH = "assets/Interface/Icons/checkbox-frame.png";
    private static final String ICON_CHECK_PATH = "assets/Interface/Icons/check-mark.png";

    private final Node node = new Node("ViceCheckbox");
    private final Picture frame = new Picture("frame");
    private final Picture check = new Picture("check");
    private final BitmapText label;
    private final String bind;

    private float size;
    private float baseLabelSize;
    private boolean checked;
    private boolean active = true;
    private boolean hovered;

    private final ColorRGBA frameColor = COLOR_FRAME.clone();
    private float checkScale = 0f;
    private float checkAlpha = 0f;
    private float labelScale = 1f;

    public ViceCheckbox(AssetManager assets, String text, String fontPath, boolean initialChecked, String bind) {
        this.checked = initialChecked;
        this.bind = bind;

        frame.setImage(assets, ICON_FRAME_PATH, true);
        frame.getMaterial().getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        check.setImage(assets, ICON_CHECK_PATH, true);
        check.getMaterial().getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        BitmapFont font = assets.loadFont(fontPath);
        label = new BitmapText(font);
        label.setBox(new Rectangle(0, 0, 200, font.getCharSet().getRenderedSize()));
        label.setAlignment(BitmapFont.Align.Left);
        label.setText(text);
        label.setColor(ColorRGBA.White);

        node.attachChild(frame);
        node.attachChild(check);
        node.attachChild(label);
    }

    public void setSize(float size) {
        this.size = size;
        this.baseLabelSize = size * 0.7f;

        frame.setWidth(size);
        frame.setHeight(size);

        check.setWidth(size);
        check.setHeight(size);

        label.setSize(baseLabelSize);

        float labelOffset = size + LABEL_GAP_PCT * size;
        float labelY = size / 2f + baseLabelSize / 2f;
        label.setLocalTranslation(labelOffset, labelY, 0);
    }

    public void setPosition(float x, float y) {
        node.setLocalTranslation(x, y, 0);
    }

    public Node getNode() {
        return node;
    }

    public boolean isChecked() {
        return checked;
    }

    public String getBind() {
        return bind;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
        if (!active) hovered = false;
    }

    @Override
    public void setHovered(boolean hovered) {
        if (active) this.hovered = hovered;
    }

    @Override
    public boolean intersects(Vector2f cursor) {
        Vector2f pos = new Vector2f(node.getWorldTranslation().x, node.getWorldTranslation().y);
        float totalWidth = size + label.getLineWidth() * labelScale + LABEL_GAP_PCT * size;

        return cursor.x >= pos.x && cursor.x <= pos.x + totalWidth &&
                cursor.y >= pos.y && cursor.y <= pos.y + size;
    }

    @Override public void handleMousePress(Vector2f cursor) {}
    @Override public void handleMouseDrag(Vector2f cursor) {}
    @Override public void handleMouseRelease() {}

    public void toggle() {
        if (active) checked = !checked;
    }

    @Override
    public void update(float tpf) {
        float lerp = FastMath.clamp(tpf * ANIM_SPEED, 0f, 1f);

        // Цвет рамки (учёт наведения и деактивации)
        ColorRGBA targetFrameColor = active ? (hovered ? COLOR_HOVER : COLOR_FRAME) : COLOR_DISABLED_FRAME;
        frameColor.interpolateLocal(targetFrameColor, lerp);
        frame.getMaterial().setColor("Color", frameColor);

        // Цели для анимации чек-марка
        float targetScale = (active && checked) ? 1.15f : 0f;
        float targetAlpha = (active && checked) ? 1f : 0f;

        // Анимация масштаба с "пружинкой"
        checkScale = FastMath.interpolateLinear(lerp, checkScale, targetScale);
        if (checked && Math.abs(checkScale - 1.15f) < 0.02f) {
            targetScale = 1f; // Возврат к нормальному размеру
        }

        // Применяем масштаб и вращение
        check.setLocalScale(checkScale);
        float rotation = checked ? FastMath.interpolateLinear(lerp, 30f * FastMath.DEG_TO_RAD, 0f) : 0f;
        check.setLocalRotation(new com.jme3.math.Quaternion().fromAngleAxis(rotation, new Vector3f(UNIT_XYZ.x, UNIT_XYZ.y, UNIT_XYZ.z)));

        // Прозрачность чек-марка
        checkAlpha = FastMath.interpolateLinear(lerp, checkAlpha, targetAlpha);
        check.getMaterial().setColor("Color", new ColorRGBA(
                COLOR_CHECK.r, COLOR_CHECK.g, COLOR_CHECK.b, checkAlpha
        ));

        // Анимация метки
        float targetLabelScale = hovered ? 1.05f : 1f;
        labelScale = FastMath.interpolateLinear(lerp, labelScale, targetLabelScale);
        label.setSize(baseLabelSize * labelScale);

        // Цвет текста (приглушённый при неактивном состоянии)
        label.setColor(active ? ColorRGBA.White : new ColorRGBA(0.6f, 0.6f, 0.6f, 0.7f));
    }

}
