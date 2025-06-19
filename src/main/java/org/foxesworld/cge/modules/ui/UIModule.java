package org.foxesworld.cge.modules.ui;

import com.jme3.app.Application;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.ui.novaUi.NovaUI;

import java.util.ArrayList;
import java.util.List;

/**
 * UIModule: a wrapper for UIPanel as an EngineModule.
 * Loads the UI configuration, creates and registers UIPanel as an AppState,
 * and manages its lifecycle and response to configuration reloads.
 */
public class UIModule extends EngineModule<UIConfig> {
    private final CalistaGameEngine calistaGameEngine;
    private static final Logger logger = LogManager.getLogger(UIModule.class);
    private static final String CONFIG_FILE = "ui_config";
    private final List<NovaUI> novaUis = new ArrayList<>();

    public UIModule(CalistaGameEngine app) {
        super(CONFIG_FILE, UIConfig.class, app, false);
        this.calistaGameEngine = app;
        logger.info("UIModule created (config = {})", CONFIG_FILE);
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        logger.info("UIModule initializing...");
    }

    public void addPanel(Object handler, String xmlFile) {
        if (getIsLoaded().get()) {
            NovaUI novaUi = new NovaUI(calistaGameEngine, xmlFile);
            calistaGameEngine.getStateManager().attach(novaUi);
            novaUi.registerEventHandler(handler);
            novaUis.add(novaUi);
            logger.info("Panel added from file: {}", xmlFile);
        } else {
            logger.warn("UIModule not loaded, cannot add panel!");
        }
    }

    /**
     * Adds an image to the center or specified position of the UI.
     *
     * @param handler   the owner of this UI element
     * @param imagePath path to the image file
     * @param relX      relative X position (0.5 = center of screen)
     * @param relY      relative Y position (0.5 = center of screen)
     * @param width     width of the image in pixels
     * @param height    height of the image in pixels
     * @return an identifier object for the image (can be used to remove it later)
     */
    public Object addImage(Object handler, String imagePath, float relX, float relY, int width, int height) {
        if (novaUis.isEmpty()) {
            logger.warn("No active UI panels to display the image.");
            return null;
        }

        NovaUI novaUi = novaUis.get(0);
        Object imageElement = novaUi.addImageElement(handler, imagePath, relX, relY, width, height);
        logger.info("Image '{}' added at position ({}, {})", imagePath, relX, relY);
        return imageElement;
    }

    @Override
    protected void updateModule(float tpf) {
        // NovaUI updates itself as an AppState
    }

    @Override
    protected void cleanupModule(Application app) {
        logger.info("Cleaning UIModule: detaching UI panels...");
        for (NovaUI novaUi : novaUis) {
            app.getStateManager().detach(novaUi);
            logger.info("UIPanel {} detached.", novaUi.getId());
        }
        novaUis.clear();
    }

    @Override
    protected void onEnable() {
        for (NovaUI panel : novaUis) {
            panel.setEnabled(true);
            logger.debug("UIPanel enabled.");
        }
    }

    @Override
    protected void onDisable() {
        for (NovaUI panel : novaUis) {
            panel.setEnabled(false);
            logger.debug("UIPanel disabled.");
        }
    }

    @Override
    protected void onConfigReloaded() {
        // TODO: implement config reload logic if needed
    }
}
