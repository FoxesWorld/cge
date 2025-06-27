package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Torus;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Box;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;

/**
 * Enhanced GTA V style background: gradient + soft overlay + animated geometry.
 */
public class MenuBackground {
    // Base semi-transparent overlay color
    public static final ColorRGBA BG_COLOR = new ColorRGBA(0.06f, 0.10f, 0.15f, 0.82f);
    private static final ColorRGBA FALLBACK_ART_COLOR = new ColorRGBA(0.09f, 0.15f, 0.22f, 1f);
    private static final String ART_TEXTURE_PATH = "Interface/Textures/menu_bg.png";

    private final float menuWidth = 520;
    private final float menuHeight = 256;
    private final float screenW, screenH, menuX, menuY;

    private final Geometry overlay;
    private final Geometry art;
    private final Node geoGroup = new Node("MenuBackgroundGeo");

    private Geometry spinningTorus;
    private Geometry floatingSphere;
    private Geometry backgroundBox;

    public MenuBackground(Application app) {
        screenW = app.getCamera().getWidth();
        screenH = app.getCamera().getHeight();
        menuX = (screenW - menuWidth) / 2f;
        menuY = screenH / 2f - menuHeight / 2f;

        // --- Stylized background image (or fallback gradient color) ---
        Quad artQuad = new Quad(screenW, screenH);
        art = new Geometry("MenuArt", artQuad);
        try {
            Texture artTex = app.getAssetManager().loadTexture(ART_TEXTURE_PATH);
            Material artMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            artMat.setTexture("ColorMap", artTex);
            artMat.setColor("Color", ColorRGBA.White);
            art.setMaterial(artMat);
        } catch (Exception e) {
            // fallback gradient tone
            Material artMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            artMat.setColor("Color", FALLBACK_ART_COLOR);
            art.setMaterial(artMat);
        }
        art.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);

        // --- Overlay for contrast ---
        Quad overlayQuad = new Quad(screenW, screenH);
        overlay = new Geometry("MenuOverlay", overlayQuad);
        Material overlayMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        overlayMat.setColor("Color", BG_COLOR.clone());
        overlayMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        overlay.setMaterial(overlayMat);
        overlay.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);

        // --- Decorative geometry: stylized accents ---
        buildDecorativeGeometry(app);

        geoGroup.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);
    }

    private void buildDecorativeGeometry(Application app) {
        // spinning torus
        spinningTorus = new Geometry("SpinTorus", new Torus(20, 120, 5f, 80f));
        Material torusMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        torusMat.setColor("Color", new ColorRGBA(0.19f, 0.46f, 0.88f, 0.18f));
        spinningTorus.setMaterial(torusMat);
        spinningTorus.setLocalTranslation(screenW * 0.65f, screenH * 0.7f, -10f);
        spinningTorus.setLocalScale(2.5f, 1f, 1f);
        geoGroup.attachChild(spinningTorus);

        // floating sphere
        floatingSphere = new Geometry("FloatSphere", new Sphere(32, 32, 42f));
        Material sphereMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        sphereMat.setColor("Color", new ColorRGBA(0.96f, 0.97f, 1.0f, 0.13f));
        floatingSphere.setMaterial(sphereMat);
        floatingSphere.setLocalTranslation(screenW * 0.8f, screenH * 0.4f, -8f);
        geoGroup.attachChild(floatingSphere);

        // background faint box
        backgroundBox = new Geometry("BackgroundBox", new Box(1f, 1f, 1f));
        Material boxMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        boxMat.setColor("Color", new ColorRGBA(0.10f, 0.19f, 0.31f, 0.10f));
        backgroundBox.setMaterial(boxMat);
        backgroundBox.setLocalScale(380, 90, 1f);
        backgroundBox.setLocalTranslation(screenW * 0.15f, screenH * 0.2f, -9f);
        geoGroup.attachChild(backgroundBox);
    }

    /** Set overlay transparency dynamically */
    public void setAlpha(float a) {
        overlay.getMaterial().setColor("Color", new ColorRGBA(BG_COLOR.r, BG_COLOR.g, BG_COLOR.b, a));
    }

    /** Animate background elements. */
    public void update(float tpf) {
        float t = System.currentTimeMillis() * 0.0002f;
        spinningTorus.rotate(0, tpf * 0.3f, tpf * 0.12f);

        // subtle vertical float with sine
        floatingSphere.setLocalTranslation(
                floatingSphere.getLocalTranslation().x,
                floatingSphere.getLocalTranslation().y + FastMath.sin(t) * 2.0f,
                floatingSphere.getLocalTranslation().z
        );

        // soft rotation on the background box
        backgroundBox.rotate(0, 0, -tpf * 0.04f);

        // gentle pulsating alpha on torus (makes it more "alive")
        ColorRGBA c = (ColorRGBA) spinningTorus.getMaterial().getParam("Color").getValue();
        c.a = 0.18f + 0.05f * FastMath.sin(t * 2f);
        spinningTorus.getMaterial().setColor("Color", c);
    }

    public void detach() {
        overlay.removeFromParent();
        art.removeFromParent();
        geoGroup.removeFromParent();
    }

    // --- Accessors ---
    public Geometry getArt() { return art; }
    public Geometry getGeometry() { return overlay; }
    public Node getGeoGroup() { return geoGroup; }
    public float getMenuX() { return menuX; }
    public float getMenuY() { return menuY; }
    public float getMenuWidth() { return menuWidth; }
    public float getMenuHeight() { return menuHeight; }
}
