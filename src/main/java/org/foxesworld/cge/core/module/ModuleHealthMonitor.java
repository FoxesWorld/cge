package org.foxesworld.cge.core.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Monitors the health, state transitions, and error statistics of engine modules.
 * Provides querying and listener support for module health data.
 */
public class ModuleHealthMonitor {
    private static final Logger logger = LoggerFactory.getLogger(ModuleHealthMonitor.class);
    private static final ModuleHealthMonitor INSTANCE = new ModuleHealthMonitor();

    // Current state of each module
    private final ConcurrentHashMap<String, ModuleState> moduleStates = new ConcurrentHashMap<>();
    // Recent error per module
    private final ConcurrentHashMap<String, Throwable> moduleErrors = new ConcurrentHashMap<>();
    // Error counts per module
    private final ConcurrentHashMap<String, Integer> errorCounts = new ConcurrentHashMap<>();
    // Listeners to receive updates
    private final CopyOnWriteArrayList<HealthListener> listeners = new CopyOnWriteArrayList<>();

    private ModuleHealthMonitor() {
    }

    public static ModuleHealthMonitor getInstance() {
        return INSTANCE;
    }

    /**
     * Report a state transition for a module.
     */
    public void reportState(String moduleName, ModuleState newState) {
        ModuleState oldState = moduleStates.put(moduleName, newState);
        logger.debug("Module '{}' state {} -> {}", moduleName,
                oldState == null ? "<none>" : oldState, newState);
        listeners.forEach(l -> l.onStateChange(moduleName, newState));
    }

    /**
     * Report an error occurred in a module.
     */
    public void reportError(String moduleName, Throwable error) {
        moduleErrors.put(moduleName, error);
        errorCounts.merge(moduleName, 1, Integer::sum);
        logger.error("Module '{}' reported error (count={}): {}", moduleName,
                errorCounts.get(moduleName), error.getMessage(), error);
        listeners.forEach(l -> l.onError(moduleName, error));
    }

    /**
     * Get current state of a module.
     */
    public ModuleState getModuleState(String moduleName) {
        return moduleStates.getOrDefault(moduleName, ModuleState.UNLOADED);
    }

    /**
     * Get last reported error of a module, or null if none.
     */
    public Throwable getModuleError(String moduleName) {
        return moduleErrors.get(moduleName);
    }

    /**
     * Get total error count for a module.
     */
    public int getErrorCount(String moduleName) {
        return errorCounts.getOrDefault(moduleName, 0);
    }

    /**
     * Get an unmodifiable view of all module states.
     */
    public Map<String, ModuleState> getAllModuleStates() {
        return Collections.unmodifiableMap(moduleStates);
    }

    /**
     * Get an unmodifiable view of all module errors.
     */
    public Map<String, Throwable> getAllModuleErrors() {
        return Collections.unmodifiableMap(moduleErrors);
    }

    /**
     * Clear error and count for a module.
     */
    public void clearError(String moduleName) {
        moduleErrors.remove(moduleName);
        errorCounts.remove(moduleName);
    }

    /**
     * Add a listener to receive health updates.
     */
    public void addListener(HealthListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(HealthListener listener) {
        listeners.remove(listener);
    }

    /**
     * Listener interface for module health events.
     */
    public interface HealthListener {
        /** Called on any state transition. */
        void onStateChange(String moduleName, ModuleState newState);
        /** Called when a module reports an error. */
        void onError(String moduleName, Throwable error);
    }
}
