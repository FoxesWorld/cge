package org.foxesworld.cge.core.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Monitors the health, state transitions, and error statistics of engine modules.
 * Provides querying, listener support, and aggregated statistics for module health data.
 *
 * <p>You can either use the built‑in singleton via {@link #getInstance()},
 * or instantiate your own for testing or multiple engines.</p>
 */
public class ModuleHealthMonitor {
    private static final Logger logger = LoggerFactory.getLogger(ModuleHealthMonitor.class);

    // Singleton for normal usage
    private static final ModuleHealthMonitor DEFAULT_INSTANCE = new ModuleHealthMonitor();

    /**
     * Obtain the global monitor instance.
     * @return default ModuleHealthMonitor
     */
    public static ModuleHealthMonitor getInstance() {
        return DEFAULT_INSTANCE;
    }

    // Concurrency‑safe state holders
    private final ConcurrentHashMap<String, ModuleState>     moduleStates       = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Throwable>       moduleErrors       = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer>         errorCounts        = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer>         stateChangeCounts  = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<HealthListener>       listeners          = new CopyOnWriteArrayList<>();

    /**
     * Public no‑arg constructor to allow testing with fresh instances.
     */
    public ModuleHealthMonitor() {}

    // ────────────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Report a state transition for a module.
     */
    public void reportState(String moduleName, ModuleState newState) {
        ModuleState old = moduleStates.put(moduleName, newState);
        stateChangeCounts.merge(moduleName, 1, Integer::sum);
        logger.debug("Module '{}' state {} -> {} (transitions={})",
                moduleName,
                old == null ? "<none>" : old,
                newState,
                stateChangeCounts.get(moduleName));

        // Broadcast to listeners
        broadcast(listener -> {
            listener.onStateChange(moduleName, newState);
            listener.onStatsUpdate(moduleName, getStats(moduleName));
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

        broadcast(listener -> {
            listener.onError(moduleName, error);
            listener.onStatsUpdate(moduleName, getStats(moduleName));
        });
    }

    /**
     * Get current state of a module, or UNLOADED if unknown.
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
     * Get an unmodifiable snapshot of all module states.
     */
    public Map<String, ModuleState> getAllModuleStates() {
        return snapshotView(moduleStates);
    }

    /**
     * Get an unmodifiable snapshot of all module errors.
     */
    public Map<String, Throwable> getAllModuleErrors() {
        return snapshotView(moduleErrors);
    }

    /**
     * Get an unmodifiable snapshot of aggregated stats for all modules.
     */
    public Map<String, ModuleStats> getAllStats() {
        // Build a fresh map so stats reflect up‑to‑date values
        ConcurrentHashMap<String, ModuleStats> stats = new ConcurrentHashMap<>();
        moduleStates.keySet()
                .forEach(name -> stats.put(name, getStats(name)));
        return snapshotView(stats);
    }

    /**
     * Clear error and count for a module, then notify listeners of updated stats.
     */
    public void clearError(String moduleName) {
        moduleErrors.remove(moduleName);
        errorCounts.remove(moduleName);
        broadcast(listener -> listener.onStatsUpdate(moduleName, getStats(moduleName)));
    }

    /**
     * Register a listener. Immediately receives current stats for all modules.
     */
    public void addListener(HealthListener listener) {
        listeners.add(listener);
        getAllStats().forEach(listener::onStatsUpdate);
    }

    /**
     * Unregister a listener.
     */
    public void removeListener(HealthListener listener) {
        listeners.remove(listener);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Listener interface and stats DTO
    // ────────────────────────────────────────────────────────────────────────────

    public interface HealthListener {
        /** Called on any state transition. */
        void onStateChange(String moduleName, ModuleState newState);

        /** Called when a module reports an error. */
        void onError(String moduleName, Throwable error);

        /** Called when aggregated stats update. */
        void onStatsUpdate(String moduleName, ModuleStats stats);
    }

    public static class ModuleStats {
        private final ModuleState currentState;
        private final int errorCount;
        private final int stateChangeCount;

        public ModuleStats(ModuleState currentState, int errorCount, int stateChangeCount) {
            this.currentState     = currentState;
            this.errorCount       = errorCount;
            this.stateChangeCount = stateChangeCount;
        }

        public ModuleState getCurrentState() { return currentState; }
        public int         getErrorCount()    { return errorCount; }
        public int         getStateChangeCount() { return stateChangeCount; }

        @Override
        public String toString() {
            return String.format("State=%s, Errors=%d, Transitions=%d",
                    currentState, errorCount, stateChangeCount);
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Private helpers to reduce duplication
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Take a concurrent map and return an unmodifiable snapshot.
     */
    private <K, V> Map<K, V> snapshotView(Map<K, V> source) {
        // Copy into new CHM to avoid concurrent mutation during wrap
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(source));
    }

    /**
     * Broadcast an update to all registered listeners.
     */
    private void broadcast(Consumer<HealthListener> notification) {
        listeners.forEach(notification);
    }
}
