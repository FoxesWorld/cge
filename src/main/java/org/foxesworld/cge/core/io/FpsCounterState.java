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

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);
        this.app = (CalistaGameEngine) app;

        containerNode = new Node("FPS Counter Node");

        setupBackground();
        setupText();
        setupInput();

        updateTextAndBackground("FPS: ...");

        Camera cam = this.app.getCamera();
        containerNode.setLocalTranslation(10, cam.getHeight() - ttc.getHeight() - 10, 0);

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
        fpsTtf.genTTF("assets/Interface/fonts/Docker One.ttf", Style.Plain, 18);
        fpsTtf.genTTC(ColorUtils.fromHexString("#ffffff"), "text");
        ttc = fpsTtf.getTtc();
        containerNode.attachChild(ttc);
    }

    private void setupInput() {
        app.getInputManager().addMapping(TOGGLE_FPS_ACTION, new KeyTrigger(KeyInput.KEY_F5));
        app.getInputManager().addListener(this, TOGGLE_FPS_ACTION);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (name.equals(TOGGLE_FPS_ACTION) && isPressed) {
            isShown = !isShown;
            if (isShown) {
                this.app.getGuiNode().attachChild(containerNode);
            } else {
                this.app.getGuiNode().detachChild(containerNode);
            }
        }
    }

    @Override
    public void update(float tpf) {
        if (!isShown) return;

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

        float padding = 8f; // Отступы для фона
        float bgWidth = fpsTtf.getTtc().getTextWidth() + padding * 2;
        float bgHeight = fpsTtf.getTtc().getTextHeight() + padding; // Сверху и снизу

        backgroundGeom.setMesh(new Quad(bgWidth, bgHeight));
        backgroundGeom.setLocalTranslation(-padding, -padding / 2, -1);
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