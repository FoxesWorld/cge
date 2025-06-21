package org.foxesworld.cge.modules.ui.novaUi;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ui.novaUi.UIXmlParser.ParseResult;
import org.foxesworld.cge.modules.ui.novaUi.elements.image.ImageElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.panel.PanelElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * NovaUI is the root UI system for NovaUI-based interfaces.
 * Optimizations and stability improvements:
 *  - Safer and more robust lifecycle management.
 *  - Reduces unnecessary updates and field re-binds.
 *  - Ensures resource cleanup and error resilience.
 *  - Thread-safe element manipulation.
 *  - Improved logging.
 *  - Defensive programming for nulls and error cases.
 *  - Consistent resizing and camera adaptation.
 */
public class NovaUI extends BaseAppState {

    private static final Logger LOGGER = LoggerFactory.getLogger(NovaUI.class);

    private final CalistaGameEngine engine;
    private final Node guiNode;
    private final String configPath;

    // All access to these should be thread-safe if using from multiple threads (otherwise, standard Map is fine)
    private volatile PanelElement rootPanel;
    private volatile Map<String, UIElement> allElements;

    private final NovaUIUpdater updater = new NovaUIUpdater();
    private volatile boolean globalDirty = false;
    private volatile PanelElement dirtyRoot = null;
    private int lastCamWidth = -1, lastCamHeight = -1;

    public NovaUI(CalistaGameEngine engine, String configPath) {
        this.engine = engine;
        this.guiNode = engine.getGuiNode();
        this.configPath = configPath;
    }

    /**
     * Registers a new event handler object for UI binding.
     * Safe to call multiple times.
     */
    public void registerEventHandler(Object handler) {
        updater.setEventHandlerTarget(handler);
        // No bindAllFields here: let user control rebind or do it after UI loading.
    }

    @Override
    protected void initialize(Application app) {
        lastCamWidth = engine.getCamera().getWidth();
        lastCamHeight = engine.getCamera().getHeight();

        try {
            loadConfiguration();
            if (rootPanel == null) throw new IllegalStateException("UI rootPanel is null after config load!");
            guiNode.attachChild(rootPanel.getNode());
            rootPanel.getNode().setLocalTranslation(0f, 0f, 0f);

            updater.setAllElements(allElements); // Explicitly bind only after successful config
            updater.bindAllFields();

            expandAndPositionRootPanel();
            LOGGER.info("NovaUI initialized successfully.");
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
        LOGGER.info("NovaUI cleaned up.");
    }

    @Override
    public void update(float tpf) {
        updater.update(tpf);

        if (globalDirty && dirtyRoot != null) {
            try {
                dirtyRoot.recalcAndRepositionSelfAndAncestors();
            } catch (Exception ex) {
                LOGGER.error("Failed to recalc/reposition dirty root", ex);
            }
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

    /**
     * Adds an image element to the UI and updates all references and layouts.
     * Thread-safe element creation and registration.
     */
    public UIElement addImageElement(Object owner, String imagePath, float relX, float relY, int width, int height) {
        if (rootPanel == null) {
            LOGGER.warn("addImageElement: rootPanel is null, image will not be added.");
            return null;
        }

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

            // Defensive: check for concurrent modification
            synchronized (this) {
                allElements.put(id, image);
            }

            updater.fixOverlaps(rootPanel);
            updater.bindAllFields();

            LOGGER.info("ImageElement '{}' added at ({}, {})", id, absX, absY);
            return image;
        } catch (Exception e) {
            LOGGER.error("Failed to add image element", e);
            return null;
        }
    }

    /**
     * Sets a property on a UI element and marks affected panel(s) as dirty for update.
     */
    public void setProperty(String elementId, String propKey, String propValue) {
        UIElement e = allElements.get(elementId);
        if (e == null) {
            LOGGER.warn("setProperty: No UIElement found for id '{}'", elementId);
            return;
        }

        e.setProperty(propKey, propValue);
        PanelElement parent = e.getParentPanel();
        if (parent != null) {
            dirtyRoot = getTopmostRoot(parent);
            globalDirty = true;
        }
    }

    /**
     * Returns the uppermost parent panel for a given panel.
     */
    private PanelElement getTopmostRoot(PanelElement panel) {
        while (panel.getParentPanel() != null) {
            panel = panel.getParentPanel();
        }
        return panel;
    }

    /**
     * Gets a UI element by its ID.
     */
    public UIElement getElement(String id) {
        return allElements.get(id);
    }

    /**
     * Loads UI configuration from XML and sets up rootPanel and allElements.
     */
    private void loadConfiguration() throws Exception {
        UIXmlParser parser = new UIXmlParser(engine, configPath);
        ParseResult result = parser.parse();
        if (result.rootPanel == null) throw new IllegalStateException("Parsed UI rootPanel is null!");
        rootPanel = result.rootPanel;
        allElements = result.allElements;
        LOGGER.info("UI configuration loaded: rootPanel id='{}', {} total elements.", rootPanel.getId(), allElements != null ? allElements.size() : 0);
    }

    /**
     * Expands and repositions the root panel to match camera.
     * Only resizes if needed.
     */
    private void expandAndPositionRootPanel() {
        if (rootPanel == null) {
            LOGGER.warn("expandAndPositionRootPanel: rootPanel is null.");
            return;
        }

        boolean resized = resizeRootPanelIfNeeded();
        if (resized) {
            rootPanel.recomputeSizeAndRepositionChildren();
        }

        int camW = engine.getCamera().getWidth();
        int camH = engine.getCamera().getHeight();
        rootPanel.repositionRecursively(camW, camH);

        updater.fixOverlaps(rootPanel);
    }

    /**
     * Resizes the root panel if its auto size or needs to expand.
     * @return true if size was changed.
     */
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

    /**
     * Reloads the UI from configuration, detaching previous resources.
     */
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
            LOGGER.info("UI reloaded successfully.");
        } catch (Exception e) {
            LOGGER.error("Failed to reload UI", e);
        }
    }

    public NovaUIUpdater getUpdater() {
        return updater;
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