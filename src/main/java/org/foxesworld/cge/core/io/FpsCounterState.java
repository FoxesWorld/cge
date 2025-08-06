package org.foxesworld.cge.core.io;

import com.atr.jme.font.shape.TrueTypeContainer;
import com.atr.jme.font.util.Style;
import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.utils.ColorUtils;

public class FpsCounterState extends AbstractAppState implements ActionListener {

    private SimpleApplication app;
    private TTFrenderer fpsTtf;
    private TrueTypeContainer ttc;
    private Geometry backgroundGeom;
    private Node containerNode;

    private float secondCounter = 0.0f;
    private int frameCounter = 0;
    private final float updateInterval = 0.1f;

    private boolean isShown = true;
    private static final String TOGGLE_FPS_ACTION = "ToggleFPS";

    private int lastWidth = 0;
    private int lastHeight = 0;
    private final float baseFontSize = 18f;

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);
        this.app = (CalistaGameEngine) app;
        Camera cam = this.app.getCamera();

        lastWidth = cam.getWidth();
        lastHeight = cam.getHeight();

        containerNode = new Node("FPS Counter Node");
        setupBackground();
        setupText();
        setupInput();
        updatePosition();
        updateTextAndBackground("FPS: ...");

        this.app.getGuiNode().attachChild(containerNode);
    }

    private void setupBackground() {
        Material backgroundMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        backgroundMat.setColor("Color", new ColorRGBA(0, 0, 0, 0.5f));
        backgroundMat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

        backgroundGeom = new Geometry("FPS_Background");
        backgroundGeom.setMaterial(backgroundMat);
        backgroundGeom.setLocalTranslation(0, 0, -1);
        containerNode.attachChild(backgroundGeom);
    }

    private void setupText() {
        fpsTtf = new TTFrenderer(this.app.getAssetManager());
        float fontSize = calculateFontSize();
        fpsTtf.generateFont("assets/Interface/fonts/Docker One.ttf", Style.Plain, (int) fontSize);
        fpsTtf.generateText(ColorUtils.fromHexString("#ffffff"), "text");
        ttc = fpsTtf.getTextGeometry();
        containerNode.attachChild(ttc);
    }

    private float calculateFontSize() {
        Camera cam = app.getCamera();
        float baseDPI = 96f;
        float currentDPI = Math.max(cam.getWidth(), cam.getHeight()) / 10.8f;
        return Math.max(14f, baseFontSize * (currentDPI / baseDPI));
    }

    private void setupInput() {
        app.getInputManager().addMapping(TOGGLE_FPS_ACTION, new KeyTrigger(KeyInput.KEY_F5));
        app.getInputManager().addListener(this, TOGGLE_FPS_ACTION);
    }

    /**
     * Обновление позиции счетчика в левом верхнем углу
     */
    private void updatePosition() {
        if (app == null) return;

        Camera cam = app.getCamera();
        int width = cam.getWidth();
        int height = cam.getHeight();

        float paddingX = width * 0.015f;
        float paddingY = height * 0.015f;

        // Левый верхний угол: x = paddingX, y = height - paddingY
        containerNode.setLocalTranslation(
                paddingX,
                height - paddingY,
                0
        );
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (TOGGLE_FPS_ACTION.equals(name) && isPressed) {
            isShown = !isShown;
            containerNode.setCullHint(isShown ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        }
    }

    @Override
    public void update(float tpf) {
        if (!isShown) return;

        Camera cam = app.getCamera();
        if (cam.getWidth() != lastWidth || cam.getHeight() != lastHeight) {
            lastWidth = cam.getWidth();
            lastHeight = cam.getHeight();
            updatePosition();
        }

        secondCounter += tpf;
        frameCounter++;

        if (secondCounter >= updateInterval) {
            float averageFps = frameCounter / secondCounter;
            updateTextAndBackground(String.format("FPS: %.1f", averageFps));
            secondCounter = 0.0f;
            frameCounter = 0;
        }
    }

    private void updateTextAndBackground(String text) {
        fpsTtf.setText(text);

        float padding = calculatePadding();
        float bgWidth = ttc.getTextWidth() + padding * 2;
        float bgHeight = ttc.getTextHeight() + padding;

        backgroundGeom.setMesh(new Quad(bgWidth, bgHeight));
        backgroundGeom.setLocalTranslation(0, -ttc.getHeight(), -1);

        updatePosition();
    }

    private float calculatePadding() {
        return Math.max(4f, baseFontSize * 0.2f);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        this.app.getGuiNode().detachChild(containerNode);
        if (app.getInputManager() != null) {
            app.getInputManager().deleteMapping(TOGGLE_FPS_ACTION);
            app.getInputManager().removeListener(this);
        }
    }
}