package org.foxesworld.cge.core.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Monitors the health, state transitions, and error statistics of engine modules.
 * Provides querying, listener support, and aggregated statistics for module health data.
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
    // State change counts per module
    private final ConcurrentHashMap<String, Integer> stateChangeCounts = new ConcurrentHashMap<>();
    // Listeners to receive updates
    private final CopyOnWriteArrayList<HealthListener> listeners = new CopyOnWriteArrayList<>();

    private ModuleHealthMonitor() {}

    public static ModuleHealthMonitor getInstance() {
        return INSTANCE;
    }

    /**
     * Report a state transition for a module.
     */
    public void reportState(String moduleName, ModuleState newState) {
        ModuleState oldState = moduleStates.put(moduleName, newState);
        stateChangeCounts.merge(moduleName, 1, Integer::sum);
        logger.debug("Module '{}' state {} -> {} (transitions={})",
                moduleName,
                oldState == null ? "<none>" : oldState,
                newState,
                stateChangeCounts.get(moduleName));

        // Notify listeners of state change and updated stats
        listeners.forEach(l -> {
            l.onStateChange(moduleName, newState);
            l.onStatsUpdate(moduleName, getStats(moduleName));
        });
    }

    /**
     * Report an error occurred in a module.
     */
    public void reportError(String moduleName, Throwable error) {
        moduleErrors.put(moduleName, error);
        errorCounts.merge(moduleName, 1, Integer::sum);
        logger.error("Module '{}' reported error (count={}): {}",
                moduleName,
                errorCounts.get(moduleName),
                error.getMessage(),
                error);

        // Notify listeners of error and updated stats
        listeners.forEach(l -> {
            l.onError(moduleName, error);
            l.onStatsUpdate(moduleName, getStats(moduleName));
        });
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
     * Get total state transition count for a module.
     */
    public int getStateChangeCount(String moduleName) {
        return stateChangeCounts.getOrDefault(moduleName, 0);
    }

    /**
     * Get aggregated statistics for a module.
     */
    public ModuleStats getStats(String moduleName) {
        return new ModuleStats(
                getModuleState(moduleName),
                getErrorCount(moduleName),
                getStateChangeCount(moduleName)
        );
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
     * Get an unmodifiable view of all module statistics.
     */
    public Map<String, ModuleStats> getAllStats() {
        Map<String, ModuleStats> stats = new ConcurrentHashMap<>();
        moduleStates.keySet().forEach(name -> stats.put(name, getStats(name)));
        return Collections.unmodifiableMap(stats);
    }

    /**
     * Clear error and count for a module.
     */
    public void clearError(String moduleName) {
        moduleErrors.remove(moduleName);
        errorCounts.remove(moduleName);
        listeners.forEach(l -> l.onStatsUpdate(moduleName, getStats(moduleName)));
    }

    /**
     * Add a listener to receive health updates.
     */
    public void addListener(HealthListener listener) {
        listeners.add(listener);
        // Send initial stats for all modules
        getAllStats().forEach((module, stats) -> listener.onStatsUpdate(module, stats));
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
        /** Called when aggregated stats update. */
        void onStatsUpdate(String moduleName, ModuleStats stats);
    }

    /**
     * Aggregated statistics for a module.
     */
    public static class ModuleStats {
        private final ModuleState currentState;
        private final int errorCount;
        private final int stateChangeCount;

        public ModuleStats(ModuleState currentState, int errorCount, int stateChangeCount) {
            this.currentState = currentState;
            this.errorCount = errorCount;
            this.stateChangeCount = stateChangeCount;
        }

        public ModuleState getCurrentState() {
            return currentState;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public int getStateChangeCount() {
            return stateChangeCount;
        }

        @Override
        public String toString() {
            return String.format("State=%s, Errors=%d, Transitions=%d",
                    currentState, errorCount, stateChangeCount);
        }
    }
}