package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;

/**
 * A stylized checkbox UI component for settings panels, inspired by the clean,
 * high-contrast aesthetic of modern game menus.
 */
public final class ViceCheckbox extends Quad implements MenuComponent {

    // --- Style Constants ---
    private static final ColorRGBA COLOR_BOX_BG = new ColorRGBA(0.1f, 0.1f, 0.1f, 0.7f);
    private static final ColorRGBA COLOR_CHECK_GLOW = ColorRGBA.White.mult(1.5f); // Bright white for bloom
    private static final ColorRGBA COLOR_TEXT = ColorRGBA.White.clone();
    private static final float LABEL_GAP = 15f;
    private static final float CHECK_PADDING_RATIO = 0.15f;

    private final Node checkboxNode;
    private final BitmapText label;
    private final Geometry box;
    private final Geometry check;
    private final String bind;

    private boolean isChecked;
    private float x, y, size;

    /**
     * Constructs a new ViceCheckbox.
     *
     * @param assetManager The application's AssetManager.
     * @param text         The label text for the checkbox.
     * @param fontPath     Path to the .fnt font file.
     * @param initialState The initial checked state.
     * @param bind         A string identifier for the setting this checkbox controls.
     */
    public ViceCheckbox(AssetManager assetManager, String text, String fontPath, boolean initialState, String bind) {
        this.isChecked = initialState;
        this.bind = bind;
        this.checkboxNode = new Node("ViceCheckbox: " + text);

        // --- Label ---
        this.label = new BitmapText(assetManager.loadFont(fontPath));
        label.setText(text.toUpperCase());
        label.setColor(COLOR_TEXT);

        // --- Box Background ---
        Material boxMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        boxMat.setColor("Color", COLOR_BOX_BG);
        boxMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        this.box = new Geometry("CheckboxBox", this);
        box.setMaterial(boxMat);

        // --- Inner Check Mark/Fill ---
        Material checkMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        checkMat.setColor("Color", COLOR_CHECK_GLOW);
        this.check = new Geometry("CheckboxCheck", this);
        check.setMaterial(checkMat);

        checkboxNode.attachChild(label);
        checkboxNode.attachChild(box);
        checkboxNode.attachChild(check);
        updateVisualState();
    }

    /**
     * Sets the size of the checkbox, which proportionally scales all its elements.
     *
     * @param size The width and height of the clickable box area.
     */
    public void setSize(float size) {
        this.size = size;

        // Configure box
        ((Quad) box.getMesh()).updateGeometry(size, size);
        box.setLocalTranslation(0, 0, 0);

        // Configure inner check mark with padding
        float padding = size * CHECK_PADDING_RATIO;
        float checkSize = size - (padding * 2);
        ((Quad) check.getMesh()).updateGeometry(checkSize, checkSize);
        check.setLocalTranslation(padding, padding, 0.1f);

        // Configure label
        label.setSize(size * 0.8f);
        float labelY = (size - label.getLineHeight()) / 2f;
        label.setLocalTranslation(size + LABEL_GAP, size - labelY, 0);
    }

    /**
     * Sets the top-left position of the checkbox component.
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        checkboxNode.setLocalTranslation(x, y, 0);
    }

    /**
     * Toggles the state of the checkbox and updates its visual appearance.
     */
    public void toggle() {
        this.isChecked = !this.isChecked;
        updateVisualState();
        System.out.println("Setting '" + bind + "' toggled to: " + this.isChecked);
    }

    /**
     * Updates the visibility of the inner check mark based on the current state.
     */
    private void updateVisualState() {
        check.setCullHint(isChecked ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    /**
     * Checks if a 2D point (e.g., a cursor) is within the component's bounds.
     * The clickable area includes both the box and its label.
     */
    public boolean intersects(Vector2f point) {
        float totalWidth = size + LABEL_GAP + label.getLineWidth();
        return point.x >= x && point.x <= x + totalWidth &&
                point.y >= y && point.y <= y + size;
    }

    public Node getNode() {
        return checkboxNode;
    }

    @Override
    public void update(float tpf) {

    }

    public boolean isChecked() {
        return isChecked;
    }

    public String getBind() {
        return bind;
    }
}