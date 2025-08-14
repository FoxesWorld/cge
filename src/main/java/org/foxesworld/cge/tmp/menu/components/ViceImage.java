package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.ui.Picture;
import com.jme3.texture.Texture2D;
import org.foxesworld.cge.core.utils.ColorUtils;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.RoundedQuad;

public final class ViceImage extends UIComponent implements InteractiveComponent {

    public enum ScaleMode { FIT, COVER, STRETCH }

    private final AssetManager assetManager;
    private Picture picture;
    private Geometry background;
    private String imagePath;
    private Texture2D texture;

    private boolean isActive = true;
    private boolean isHovered = false;

    private float imageIntrinsicW = 1f;
    private float imageIntrinsicH = 1f;

    private ScaleMode scaleMode = ScaleMode.FIT;
    private final Style style;

    private boolean sizeExplicit = false;

    public static final class Style {
        private String backgroundColor = "#00000000";
        private String tintColor = "#FFFFFFFF";
        private float cornerRadius = 0f;
        private float backgroundZ = -1f;
        private boolean showBackground = false;

        public Style setBackgroundColor(String hex) { this.backgroundColor = hex; return this; }
        public Style setTintColor(String hex) { this.tintColor = hex; return this; }
        public Style setCornerRadius(float r) { this.cornerRadius = r; return this; }
        public Style setShowBackground(boolean show) { this.showBackground = show; return this; }
    }

    public ViceImage(String id, AssetManager assetManager, String imagePath, float width, float height) {
        this(id, assetManager, imagePath, width, height, new Style());
    }

    public ViceImage(String id, AssetManager assetManager, String imagePath, float width, float height, Style style) {
        super(id);
        this.assetManager = assetManager;
        this.style = style != null ? style : new Style();
        this.sizeExplicit = (width > 0f && height > 0f);
        this.width = Math.max(1f, width);
        this.height = Math.max(1f, height);

        background = new Geometry("ViceImageBackground");
        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        bgMat.setColor("Color", ColorUtils.fromHexString(this.style.backgroundColor));
        background.setMaterial(bgMat);
        this.attachChild(background);

        picture = new Picture("ViceImagePicture");
        this.attachChild(picture);

        if (imagePath != null) {
            setImagePath(imagePath);
        } else {
            updateGeometry();
        }
    }

    public void setImagePath(String path) {
        if (path == null) return;
        this.imagePath = path;
        try {
            texture = (Texture2D) assetManager.loadTexture(path);
            var img = texture.getImage();
            if (img != null) {
                imageIntrinsicW = Math.max(1f, img.getWidth());
                imageIntrinsicH = Math.max(1f, img.getHeight());
            }
            picture.setTexture(assetManager, texture, true);
            Material mat = new Material(assetManager, "Common/MatDefs/Gui/Gui.j3md");
            mat.setTexture("Texture", texture);
            mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            mat.setColor("Color", ColorRGBA.White);
            picture.setMaterial(mat);

            if (!sizeExplicit) {
                this.width = imageIntrinsicW;
                this.height = imageIntrinsicH;
                picture.setWidth(imageIntrinsicW);
                picture.setHeight(imageIntrinsicH);
            }
        } catch (Exception e) {
            imageIntrinsicW = 1f;
            imageIntrinsicH = 1f;
            texture = null;
        }

        applyTint();
        updateGeometry();
    }

    public void setSize(float width, float height) {
        this.sizeExplicit = true;
        this.width = Math.max(1f, width);
        this.height = Math.max(1f, height);
        updateGeometry();
    }

    public void setScaleMode(ScaleMode mode) {
        this.scaleMode = mode == null ? ScaleMode.FIT : mode;
        updateGeometry();
    }

    public void setTintColor(String hex) {
        if (hex == null) return;
        style.setTintColor(hex);
        applyTint();
    }

    public void setShowBackground(boolean show) {
        style.setShowBackground(show);
        updateGeometry();
    }

    public void setCornerRadius(float r) {
        style.setCornerRadius(r);
        updateGeometry();
    }

    private void applyTint() {
        if (picture != null && picture.getMaterial() != null) {
            try {
                picture.getMaterial().setColor("Color", ColorUtils.fromHexString(style.tintColor));
            } catch (Exception ignored) {}
        }
    }

    private void updateGeometry() {
        if (style.showBackground) {
            background.setMesh(new RoundedQuad(width, height, style.cornerRadius, 16));
            background.setLocalTranslation(0, 0, style.backgroundZ);
            try {
                background.getMaterial().setColor("Color", ColorUtils.fromHexString(style.backgroundColor));
            } catch (Exception ignored) {}
            if (background.getParent() == null) this.attachChild(background);
        } else {
            if (background.getParent() != null) background.removeFromParent();
        }

        float imgAspect = imageIntrinsicW / imageIntrinsicH;
        float areaAspect = width / Math.max(1f, height);
        float dispW = width, dispH = height;

        switch (scaleMode) {
            case STRETCH:
                break;
            case FIT:
                if (imgAspect >= areaAspect) {
                    dispW = width;
                    dispH = width / imgAspect;
                } else {
                    dispH = height;
                    dispW = height * imgAspect;
                }
                break;
            case COVER:
                if (imgAspect >= areaAspect) {
                    dispH = height;
                    dispW = height * imgAspect;
                } else {
                    dispW = width;
                    dispH = width / imgAspect;
                }
                break;
        }

        picture.setWidth(Math.max(1f, dispW));
        picture.setHeight(Math.max(1f, dispH));
        picture.setLocalTranslation(0, 0, 0f);

        applyTint();
    }

    @Override
    public void update(float tpf) { }

    @Override
    public boolean intersects(Vector2f pos) {
        return isActive &&
                pos.x >= getWorldTranslation().x &&
                pos.x <= getWorldTranslation().x + width &&
                pos.y >= getWorldTranslation().y &&
                pos.y <= getWorldTranslation().y + height;
    }

    @Override
    public void setHovered(boolean hovered) { this.isHovered = hovered; }

    @Override
    public void handleMousePress(Vector2f c) { }

    @Override
    public void handleMouseDrag(Vector2f c) { }

    @Override
    public void handleMouseRelease() { }

    @Override
    public void setActive(boolean active) {
        this.isActive = active;
        if (!active) isHovered = false;
    }
}
