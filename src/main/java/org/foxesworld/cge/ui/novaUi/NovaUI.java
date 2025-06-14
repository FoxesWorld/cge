package org.foxesworld.cge.ui.novaUi;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.novaUi.UIXmlParser.ParseResult;
import org.foxesworld.cge.ui.novaUi.elements.image.ImageElement;
import org.foxesworld.cge.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.ui.novaUi.elements.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class NovaUI extends BaseAppState {

    private static final Logger LOGGER = LoggerFactory.getLogger(NovaUI.class);

    private final CalistaGameEngine engine;
    private final Node guiNode;
    private final String configPath;

    private PanelElement rootPanel;
    private Map<String, UIElement> allElements;

    private final NovaUIUpdater updater = new NovaUIUpdater();
    private boolean globalDirty = false;
    private PanelElement dirtyRoot = null;
    private int lastCamWidth = -1, lastCamHeight = -1;

    public NovaUI(CalistaGameEngine engine, String configPath) {
        this.engine = engine;
        this.guiNode = engine.getGuiNode();
        this.configPath = configPath;
    }

    public void registerEventHandler(Object handler) {
        updater.setEventHandlerTarget(handler);
    }

    @Override
    protected void initialize(Application app) {
        lastCamWidth = engine.getCamera().getWidth();
        lastCamHeight = engine.getCamera().getHeight();

        try {
            loadConfiguration();
            guiNode.attachChild(rootPanel.getNode());
            rootPanel.getNode().setLocalTranslation(0f, 0f, 0f);

            updater.setAllElements(allElements);
            updater.bindAllFields();

            expandAndPositionRootPanel();
        } catch (Exception e) {
            LOGGER.error("Failed to initialize UIPanel", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void cleanup(Application app) {
        if (rootPanel != null) {
            guiNode.detachChild(rootPanel.getNode());
        }
        rootPanel = null;
        allElements = null;
    }

    @Override
    public void update(float tpf) {
        updater.update(tpf);

        if (globalDirty && dirtyRoot != null) {
            dirtyRoot.recalcAndRepositionSelfAndAncestors();
            globalDirty = false;
            dirtyRoot = null;
        }

        int camW = engine.getCamera().getWidth();
        int camH = engine.getCamera().getHeight();
        if (camW != lastCamWidth || camH != lastCamHeight) {
            expandAndPositionRootPanel();
            lastCamWidth = camW;
            lastCamHeight = camH;
        }
    }

    public UIElement addImageElement(Object owner, String imagePath, float relX, float relY, int width, int height) {
        if (rootPanel == null) return null;

        try {
            String id = "image_" + System.nanoTime();
            ImageElement image = new ImageElement(engine, id, rootPanel);

            image.setProperty("imagePath", imagePath);
            image.setProperty("width", String.valueOf(width));
            image.setProperty("height", String.valueOf(height));

            int cameraW = engine.getCamera().getWidth();
            int cameraH = engine.getCamera().getHeight();
            int absX = Math.round(relX * cameraW - width / 2f);
            int absY = Math.round(relY * cameraH - height / 2f);

            image.setProperty("posX", String.valueOf(absX));
            image.setProperty("posY", String.valueOf(absY));

            rootPanel.addChild(image);
            allElements.put(id, image);

            updater.fixOverlaps(rootPanel);
            updater.bindAllFields();

            return image;
        } catch (Exception e) {
            LOGGER.error("Failed to add image element", e);
            return null;
        }
    }

    public void setProperty(String elementId, String propKey, String propValue) {
        UIElement e = allElements.get(elementId);
        if (e == null) return;

        e.setProperty(propKey, propValue);
        PanelElement parent = e.getParentPanel();
        if (parent != null) {
            dirtyRoot = getTopmostRoot(parent);
            globalDirty = true;
        }
    }

    private PanelElement getTopmostRoot(PanelElement panel) {
        while (panel.getParentPanel() != null) {
            panel = panel.getParentPanel();
        }
        return panel;
    }

    public UIElement getElement(String id) {
        return allElements.get(id);
    }

    private void loadConfiguration() throws Exception {
        UIXmlParser parser = new UIXmlParser(engine, configPath);
        ParseResult result = parser.parse();
        rootPanel = result.rootPanel;
        allElements = result.allElements;
    }

    private void expandAndPositionRootPanel() {
        if (rootPanel == null) return;

        boolean resized = resizeRootPanelIfNeeded();
        if (resized) {
            rootPanel.recomputeSizeAndRepositionChildren();
        }

        int camW = engine.getCamera().getWidth();
        int camH = engine.getCamera().getHeight();
        rootPanel.repositionRecursively(camW, camH);

        updater.fixOverlaps(rootPanel);
    }

    private boolean resizeRootPanelIfNeeded() {
        rootPanel.recomputeSizeAndRepositionChildren();
        float neededW = rootPanel.getCurrentWidth();
        float neededH = rootPanel.getCurrentHeight();

        boolean changed = false;
        if (rootPanel.isAutoWidth() || rootPanel.getFixedWidth() < neededW) {
            rootPanel.setFixedWidth(neededW);
            changed = true;
        }
        if (rootPanel.isAutoHeight() || rootPanel.getFixedHeight() < neededH) {
            rootPanel.setFixedHeight(neededH);
            changed = true;
        }
        return changed;
    }

    public void reloadUI() {
        try {
            if (rootPanel != null) {
                guiNode.detachChild(rootPanel.getNode());
            }

            loadConfiguration();

            guiNode.attachChild(rootPanel.getNode());
            rootPanel.getNode().setLocalTranslation(0f, 0f, 0f);

            updater.setAllElements(allElements);
            updater.bindAllFields();

            expandAndPositionRootPanel();
        } catch (Exception e) {
            LOGGER.error("Failed to reload UI", e);
        }
    }

    @Override
    protected void onEnable() {
        LOGGER.debug("UIPanel enabled.");
    }

    @Override
    protected void onDisable() {
        LOGGER.debug("UIPanel disabled.");
    }
}
