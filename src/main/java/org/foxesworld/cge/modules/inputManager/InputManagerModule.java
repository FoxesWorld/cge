package org.foxesworld.cge.modules.inputManager;

import com.jme3.app.Application;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all user input, including key bindings, mouse actions, and their states.
 * <p>
 * This module reads key mappings from {@link InputConfig} and registers them with
 * JMonkeyEngine's {@link InputManager}. It supports keyboard keys, mouse buttons,
 * and mouse axes (including the scroll wheel), providing a fully centralized way
 * to handle player controls and allowing for runtime reconfiguration.
 * </p>
 * <p>
 * Other game systems can query the state of a digital action (e.g., "is 'jump' pressed?")
 * by using the {@link #isActionActive(String)} method. Analog actions (like mouse movement)
 * must be listened to by a class implementing {@link com.jme3.input.controls.AnalogListener}.
 * </p>
 */
public final class InputManagerModule extends EngineModule<InputConfig> {

    private static final Logger LOGGER = LogManager.getLogger(InputManagerModule.class);

    private final InputManager inputManager;
    private final Map<String, Boolean> digitalActionStates = new ConcurrentHashMap<>();

    private final ActionListener digitalActionListener = (actionName, isPressed, tpf) -> {
        digitalActionStates.put(actionName, isPressed);
        LOGGER.trace("Digital Action '{}' state changed to: {}", actionName, isPressed ? "ACTIVE" : "INACTIVE");
    };

    /**
     * Constructs the InputManagerModule.
     * @param app The CalistaGameEngine instance.
     */
    public InputManagerModule(CalistaGameEngine app) {
        super(InputManagerModule.class, InputConfig.class, app, true);
        this.inputManager = app.getInputManager();
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        if (getConfig() == null) {
            throw new IllegalStateException("InputConfig not loaded. Cannot initialize InputManagerModule.");
        }
        LOGGER.info("Initializing InputManagerModule...");
        applyKeyBindings();
    }

    @Override
    public void onConfigReloaded() {
        LOGGER.info("Input configuration reloaded. Re-applying all bindings...");
        applyKeyBindings();
    }

    /**
     * Checks if a specific digital action is currently active (i.e., its key or mouse button is pressed).
     *
     * @param actionName The name of the action to check (e.g., "jump", "fire").
     * @return {@code true} if the action is active, {@code false} otherwise.
     */
    public boolean isActionActive(String actionName) {
        return digitalActionStates.getOrDefault(actionName, false);
    }

    /**
     * Clears old mappings and applies new ones from the current configuration.
     * This is the core logic for setting up and refreshing controls.
     */
    private void applyKeyBindings() {
        LOGGER.info("Clearing existing bindings and applying new configuration...");
        inputManager.clearMappings();
        digitalActionStates.clear();

        Map<String, String> keyMappings = getConfig().getKeyMappings(); //Getting here
        if (keyMappings == null || keyMappings.isEmpty()) {
            LOGGER.warn("No key mappings found in the configuration. The map is empty or null.");
            return;
        }

        LOGGER.info("Found {} total mappings to apply.", keyMappings.size());
        keyMappings.forEach(this::parseAndRegisterBinding);
        LOGGER.info("InputManagerModule successfully applied all valid bindings.");
    }

    /**
     * Parses a trigger name from the config and registers the corresponding binding.
     * This acts as a dispatcher to the correct registration method.
     */
    private void parseAndRegisterBinding(String actionName, String triggerName) {
        if (triggerName.startsWith("KEY_")) {
            parseKeyCode(triggerName).ifPresent(keyCode -> registerKeyAction(actionName, keyCode));
        } else if (triggerName.startsWith("MOUSE_AXIS_")) {
            parseMouseAxisTrigger(triggerName).ifPresent(trigger -> registerMouseAxisAction(actionName, trigger));
        } else if (triggerName.startsWith("BUTTON_")) {
            parseMouseButtonCode(triggerName).ifPresent(buttonCode -> registerMouseButtonAction(actionName, buttonCode));
        } else {
            LOGGER.warn("Unsupported trigger type for '{}'. Only KEY_, MOUSE_AXIS_, and BUTTON_ are supported.", triggerName);
        }
    }

    private void registerKeyAction(String actionName, int keyCode) {
        inputManager.addMapping(actionName, new KeyTrigger(keyCode));
        inputManager.addListener(digitalActionListener, actionName);
        LOGGER.debug("Mapped digital action '{}' to key code '{}'", actionName, keyCode);
    }

    private void registerMouseButtonAction(String actionName, int buttonCode) {
        inputManager.addMapping(actionName, new MouseButtonTrigger(buttonCode));
        inputManager.addListener(digitalActionListener, actionName);
        LOGGER.debug("Mapped digital action '{}' to mouse button code '{}'", actionName, buttonCode);
    }

    private void registerMouseAxisAction(String actionName, MouseAxisTrigger trigger) {
        inputManager.addMapping(actionName, trigger);
        // Analog listeners are added by consumer classes (e.g., PlayerCameraControl), not here.
        LOGGER.debug("Mapped analog action '{}' to mouse axis trigger.", actionName);
    }

    private OptionalInt parseKeyCode(String keyName) {
        try {
            return OptionalInt.of(KeyInput.class.getField(keyName).getInt(null));
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Invalid key name '{}' in configuration. It will be ignored.", keyName);
            return OptionalInt.empty();
        }
    }

    private Optional<MouseAxisTrigger> parseMouseAxisTrigger(String triggerName) {
        String[] parts = triggerName.split("_");
        if (parts.length < 3) return Optional.empty();

        int axis;
        switch (parts[2].substring(0, 1).toUpperCase()) {
            case "X": axis = MouseInput.AXIS_X; break;
            case "Y": axis = MouseInput.AXIS_Y; break;
            case "W": axis = MouseInput.AXIS_WHEEL; break;
            default:
                LOGGER.error("Invalid mouse axis specified in '{}'", triggerName);
                return Optional.empty();
        }
        boolean negative = triggerName.endsWith("-");
        return Optional.of(new MouseAxisTrigger(axis, negative));
    }

    private OptionalInt parseMouseButtonCode(String buttonName) {
        String name = buttonName.substring("BUTTON_".length()).toUpperCase();
        switch (name) {
            case "LEFT": return OptionalInt.of(MouseInput.BUTTON_LEFT);
            case "RIGHT": return OptionalInt.of(MouseInput.BUTTON_RIGHT);
            case "MIDDLE": return OptionalInt.of(MouseInput.BUTTON_MIDDLE);
            default:
                try {
                    // For BUTTON_0, BUTTON_1 etc.
                    return OptionalInt.of(Integer.parseInt(name));
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid mouse button name '{}'. Use LEFT, RIGHT, MIDDLE, or a number.", name);
                    return OptionalInt.empty();
                }
        }
    }

    @Override
    protected void cleanupModule(Application app) {
        LOGGER.info("Cleaning up InputManagerModule...");
        if (inputManager != null) {
            inputManager.clearMappings();
            inputManager.removeListener(digitalActionListener);
        }
        digitalActionStates.clear();
        LOGGER.info("InputManagerModule has been cleaned up.");
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}

    @Override
    protected void updateModule(float tpf) {}
}