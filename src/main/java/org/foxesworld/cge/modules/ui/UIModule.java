package org.foxesworld.cge.modules.ui;

import com.jme3.app.Application;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.ui.novaUi.NovaUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of multiple, named UI instances (NovaUI AppStates).
 * This module acts as a factory and registry for different UI screens like
 * "mainMenu", "hud", "settings", etc.
 *
 * <h3>Core Responsibilities:</h3>
 * <ul>
 *   <li>Create and configure NovaUI instances using the {@link NovaUI.Builder}.</li>
 *   <li>Manage the attachment and detachment of NovaUI AppStates from the engine.</li>
 *   <li>Provide access to specific UI instances by a unique ID.</li>
 * </ul>
 *
 * @version 2.0
 * @author CalistaF0X & Gemini
 */
public class UIModule extends EngineModule<UIConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIModule.class);

    private final CalistaGameEngine calistaGameEngine;
    private final Map<String, NovaUI> uiInstances = new ConcurrentHashMap<>();

    public UIModule(CalistaGameEngine app) {
        super(UIModule.class, UIConfig.class, app, false);
        this.calistaGameEngine = app;
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        LOGGER.info("UIModule initialized.");
        // Here you could automatically load UIs defined in the UIConfig file if needed.
    }

    /**
     * Creates, configures, and attaches a new UI screen from an XML layout file.
     *
     * @param uiId         A unique identifier for this UI screen (e.g., "mainMenu").
     * @param xmlPath      The path to the UI layout XML file.
     * @param eventHandler The controller object that will handle UI events for this screen.
     * @return The newly created and attached NovaUI instance, or null if an error occurred.
     */
    public NovaUI createUi(String uiId, String xmlPath, Object eventHandler) {
        if (!isLoaded()) {
            LOGGER.warn("UIModule is not loaded, cannot create UI '{}'.", uiId);
            return null;
        }
        if (uiInstances.containsKey(uiId)) {
            LOGGER.warn("A UI with the ID '{}' already exists. Creation aborted.", uiId);
            return uiInstances.get(uiId);
        }

        try {
            LOGGER.info("Creating UI screen '{}' from layout '{}'.", uiId, xmlPath);
            NovaUI newUi = new NovaUI.Builder(calistaGameEngine)
                    .withLayout(xmlPath).withEventHandler(eventHandler).build();

            uiInstances.put(uiId, newUi);
            calistaGameEngine.getStateManager().attach(newUi);
            return newUi;
        } catch (Exception e) {
            LOGGER.error("Failed to create and attach UI with ID '{}'", uiId, e);
            return null;
        }
    }

    /**
     * Retrieves a managed UI screen by its unique ID.
     *
     * @param uiId The ID of the UI to retrieve (e.g., "hud").
     * @return The NovaUI instance, or null if not found.
     */
    public NovaUI getUi(String uiId) {
        NovaUI ui = uiInstances.get(uiId);
        if (ui == null) {
            LOGGER.warn("Request for non-existent UI with ID: '{}'", uiId);
        }
        return ui;
    }

    /**
     * Retrieves a managed UI screen by its unique ID, wrapped in an Optional.
     *
     * @param uiId The ID of the UI to retrieve.
     * @return An Optional containing the NovaUI instance if it exists.
     */
    public Optional<NovaUI> getUiOptional(String uiId) {
        return Optional.ofNullable(uiInstances.get(uiId));
    }

    /**
     * Detaches and removes a UI screen by its ID.
     *
     * @param uiId The ID of the UI screen to remove.
     */
    public void removeUi(String uiId) {
        NovaUI ui = uiInstances.remove(uiId);
        if (ui != null) {
            calistaGameEngine.enqueue(() -> {
                if (ui.isInitialized()) {
                    calistaGameEngine.getStateManager().detach(ui);
                }
                LOGGER.info("UI screen '{}' has been removed.", uiId);
            });
        }
    }

    @Override
    protected void updateModule(float tpf) {
        // NovaUI instances update themselves as AppStates.
    }

    @Override
    protected void cleanupModule(Application app) {
        LOGGER.info("Cleaning up UIModule: detaching all UI screens...");
        // Use a copy of keys to avoid ConcurrentModificationException if removeUi is called elsewhere
        for (String uiId : uiInstances.keySet()) {
            removeUi(uiId);
        }
        uiInstances.clear();
    }

    @Override
    protected void onEnable() {
        uiInstances.values().forEach(ui -> ui.setEnabled(true));
        LOGGER.debug("UIModule enabled, all screens are now active.");
    }



    @Override
    protected void onDisable() {
        uiInstances.values().forEach(ui -> ui.setEnabled(false));
        LOGGER.debug("UIModule disabled, all screens are now inactive.");
    }

    @Override
    public void onConfigReloaded() {
        LOGGER.info("UIModule config reloaded. Consider implementing logic to reload UIs from config.");
        // Example logic:
        // 1. Parse new UIConfig
        // 2. Compare with current uiInstances
        // 3. Remove UIs that are no longer in the config
        // 4. Reload or add UIs that are in the config
    }
}