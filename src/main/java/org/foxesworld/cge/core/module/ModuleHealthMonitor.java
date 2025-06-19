package org.foxesworld.cge.core.module;

import org.foxesworld.cge.core.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Comprehensive health monitoring system for engine modules that tracks state transitions,
 * errors, performance metrics, and historical data. Provides real-time alerts and
 * detailed reporting capabilities for AAA game production environments.
 * <p>
 * Features:
 * <ul>
 *   <li>Real-time state tracking with timestamps</li>
 *   <li>Detailed error categorization and historical tracking</li>
 *   <li>Performance metrics collection and analysis</li>
 *   <li>Extensive listener system with filtered events</li>
 *   <li>Historical state transition recording</li>
 *   <li>Thread-safe implementation for high-concurrency environments</li>
 * </ul>
 *
 * @author Calista Game Engine Team
 * @version 2.0
 */
@ThreadSafe
public class ModuleHealthMonitor {
    private static final Logger logger = LoggerFactory.getLogger(ModuleHealthMonitor.class);

    // Singleton for normal usage
    private static final ModuleHealthMonitor DEFAULT_INSTANCE = new ModuleHealthMonitor();

    /**
     * Maximum number of transitions to keep in history
     */
    private static final int DEFAULT_HISTORY_SIZE = 50;

    /**
     * Maximum number of errors to track per module
     */
    private static final int DEFAULT_ERROR_HISTORY_SIZE = 20;

    /**
     * Obtain the global monitor instance.
     * @return default ModuleHealthMonitor
     */
    public static ModuleHealthMonitor getInstance() {
        return DEFAULT_INSTANCE;
    }

    // Concurrency-safe state holders
    private final ConcurrentHashMap<String, ModuleState> moduleStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Throwable> lastErrors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> errorCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> stateChangeCounts = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<HealthListener> listeners = new CopyOnWriteArrayList<>();

    // Enhanced statistics
    private final ConcurrentHashMap<String, Instant> registrationTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastStateChangeTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<StateTransition>> stateHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ErrorEvent>> errorHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PerformanceMetrics> performanceMetrics = new ConcurrentHashMap<>();

    // Size limits for histories
    private int historySize = DEFAULT_HISTORY_SIZE;
    private int errorHistorySize = DEFAULT_ERROR_HISTORY_SIZE;

    /**
     * Public no-arg constructor to allow testing with fresh instances.
     */
    public ModuleHealthMonitor() {}

    /**
     * Register a module with the health monitoring system.
     * This should be called when a module is created.
     *
     * @param moduleName the name of the module to register
     * @return this ModuleHealthMonitor instance for method chaining
     */
    public synchronized ModuleHealthMonitor registerModule(String moduleName) {
        if (!registrationTimes.containsKey(moduleName)) {
            Instant now = Instant.now();
            registrationTimes.put(moduleName, now);
            moduleStates.put(moduleName, ModuleState.UNLOADED);
            stateHistory.put(moduleName, new ArrayList<>());
            errorHistory.put(moduleName, new ArrayList<>());
            performanceMetrics.put(moduleName, new PerformanceMetrics());

            // Record initial state without incrementing transition count
            stateHistory.get(moduleName).add(new StateTransition(ModuleState.UNLOADED, now));

            logger.debug("Module '{}' registered with health monitor at {}", moduleName, now);
        } else {
            logger.warn("Module '{}' already registered, ignoring duplicate registration", moduleName);
        }
        return this;
    }

    /**
     * Report a state transition for a module.
     *
     * @param moduleName the name of the module
     * @param newState the new state of the module
     */
    public void reportState(String moduleName, ModuleState newState) {
        ensureRegistered(moduleName);

        Instant timestamp = Instant.now();
        ModuleState oldState = moduleStates.put(moduleName, newState);
        lastStateChangeTimes.put(moduleName, timestamp);
        stateChangeCounts.merge(moduleName, 1, Integer::sum);

        // Record transition in history
        List<StateTransition> history = stateHistory.get(moduleName);
        synchronized (history) {
            while (history.size() >= historySize) {
                history.remove(0);
            }
            history.add(new StateTransition(newState, timestamp));
        }

        // Update performance metrics
        if (newState == ModuleState.RUNNING && oldState == ModuleState.INITIALIZING) {
            performanceMetrics.get(moduleName).recordInitComplete(timestamp);
        }

        logger.debug("Module '{}' state {} -> {} (transitions={}) at {}",
                moduleName,
                oldState == null ? "<none>" : oldState,
                newState,
                stateChangeCounts.get(moduleName),
                timestamp);

        // Broadcast to listeners
        broadcast(listener -> {
            listener.onStateChange(moduleName, oldState, newState, timestamp);
            listener.onStatsUpdate(moduleName, getStats(moduleName));
        });
    }

    /**
     * Report an error that occurred in a module.
     *
     * @param moduleName the name of the module
     * @param error the error that occurred
     */
    public void reportError(String moduleName, Throwable error) {
        ensureRegistered(moduleName);

        Instant timestamp = Instant.now();
        lastErrors.put(moduleName, error);
        errorCounts.merge(moduleName, 1, Integer::sum);

        // Categorize the error
        ErrorCategory category = categorizeError(error);
        ErrorEvent errorEvent = new ErrorEvent(error, timestamp, category);

        // Add to history
        List<ErrorEvent> errors = errorHistory.get(moduleName);
        synchronized (errors) {
            while (errors.size() >= errorHistorySize) {
                errors.remove(0);
            }
            errors.add(errorEvent);
        }

        // Update performance metrics
        performanceMetrics.get(moduleName).recordError();

        logger.error("Module '{}' reported {} error (count={}): {}",
                moduleName,
                category,
                errorCounts.get(moduleName),
                error.getMessage(),
                error);

        broadcast(listener -> {
            listener.onError(moduleName, error, category, timestamp);
            listener.onStatsUpdate(moduleName, getStats(moduleName));
        });
    }

    /**
     * Report performance metrics for a module.
     *
     * @param moduleName the name of the module
     * @param operationName the operation being measured
     * @param durationMs the duration in milliseconds
     */
    public void reportPerformance(String moduleName, String operationName, long durationMs) {
        ensureRegistered(moduleName);

        performanceMetrics.get(moduleName).recordOperation(operationName, durationMs);

        // Only notify listeners for significant performance events
        if (durationMs > 100) { // Arbitrary threshold for "slow" operations
            broadcast(listener -> listener.onPerformanceAlert(moduleName, operationName, durationMs));
        }
    }

    /**
     * Get current state of a module, or UNLOADED if unknown.
     *
     * @param moduleName the name of the module
     * @return the current state of the module
     */
    public ModuleState getModuleState(String moduleName) {
        return moduleStates.getOrDefault(moduleName, ModuleState.UNLOADED);
    }

    /**
     * Get last reported error of a module, or null if none.
     *
     * @param moduleName the name of the module
     * @return the last error reported by the module, or null if none
     */
    public Throwable getModuleError(String moduleName) {
        return lastErrors.get(moduleName);
    }

    /**
     * Get total error count for a module.
     *
     * @param moduleName the name of the module
     * @return the number of errors reported by the module
     */
    public int getErrorCount(String moduleName) {
        return errorCounts.getOrDefault(moduleName, 0);
    }

    /**
     * Get total state transition count for a module.
     *
     * @param moduleName the name of the module
     * @return the number of state transitions for the module
     */
    public int getStateChangeCount(String moduleName) {
        return stateChangeCounts.getOrDefault(moduleName, 0);
    }

    /**
     * Get the registration time for a module.
     *
     * @param moduleName the name of the module
     * @return the time the module was registered, or null if not registered
     */
    public Instant getRegistrationTime(String moduleName) {
        return registrationTimes.get(moduleName);
    }

    /**
     * Get the time of the last state change for a module.
     *
     * @param moduleName the name of the module
     * @return the time of the last state change, or null if no state changes
     */
    public Instant getLastStateChangeTime(String moduleName) {
        return lastStateChangeTimes.get(moduleName);
    }

    /**
     * Get the history of state transitions for a module.
     *
     * @param moduleName the name of the module
     * @return an unmodifiable list of state transitions
     */
    public List<StateTransition> getStateHistory(String moduleName) {
        List<StateTransition> history = stateHistory.get(moduleName);
        if (history == null) {
            return Collections.emptyList();
        }
        synchronized (history) {
            return Collections.unmodifiableList(new ArrayList<>(history));
        }
    }

    /**
     * Get the history of errors for a module.
     *
     * @param moduleName the name of the module
     * @return an unmodifiable list of error events
     */
    public List<ErrorEvent> getErrorHistory(String moduleName) {
        List<ErrorEvent> errors = errorHistory.get(moduleName);
        if (errors == null) {
            return Collections.emptyList();
        }
        synchronized (errors) {
            return Collections.unmodifiableList(new ArrayList<>(errors));
        }
    }

    /**
     * Get performance metrics for a module.
     *
     * @param moduleName the name of the module
     * @return the performance metrics for the module
     */
    public PerformanceMetrics getPerformanceMetrics(String moduleName) {
        return performanceMetrics.getOrDefault(moduleName, new PerformanceMetrics());
    }

    /**
     * Get aggregated statistics for a module.
     *
     * @param moduleName the name of the module
     * @return comprehensive statistics for the module
     */
    public ModuleStats getStats(String moduleName) {
        return new ModuleStats(
                moduleName,
                getModuleState(moduleName),
                getErrorCount(moduleName),
                getStateChangeCount(moduleName),
                getRegistrationTime(moduleName),
                getLastStateChangeTime(moduleName),
                getLastStateChangeTime(moduleName) != null ?
                        System.currentTimeMillis() - getLastStateChangeTime(moduleName).toEpochMilli() : 0,
                getPerformanceMetrics(moduleName)
        );
    }

    /**
     * Get an unmodifiable snapshot of all module states.
     *
     * @return map of module names to their current states
     */
    public Map<String, ModuleState> getAllModuleStates() {
        return snapshotView(moduleStates);
    }

    /**
     * Get an unmodifiable snapshot of all module errors.
     *
     * @return map of module names to their last errors
     */
    public Map<String, Throwable> getAllModuleErrors() {
        return snapshotView(lastErrors);
    }

    /**
     * Get an unmodifiable snapshot of aggregated stats for all modules.
     *
     * @return map of module names to their comprehensive statistics
     */
    public Map<String, ModuleStats> getAllStats() {
        // Build a fresh map so stats reflect up-to-date values
        ConcurrentHashMap<String, ModuleStats> stats = new ConcurrentHashMap<>();
        moduleStates.keySet()
                .forEach(name -> stats.put(name, getStats(name)));
        return snapshotView(stats);
    }

    /**
     * Get a list of all registered modules.
     *
     * @return unmodifiable list of all registered module names
     */
    public List<String> getAllModuleNames() {
        return Collections.unmodifiableList(
                new ArrayList<>(registrationTimes.keySet())
        );
    }

    /**
     * Get a list of modules in a specific state.
     *
     * @param state the state to filter by
     * @return unmodifiable list of module names in the specified state
     */
    public List<String> getModulesInState(ModuleState state) {
        return moduleStates.entrySet().stream()
                .filter(entry -> entry.getValue() == state)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Get a list of modules with errors.
     *
     * @return unmodifiable list of module names that have reported errors
     */
    public List<String> getModulesWithErrors() {
        return Collections.unmodifiableList(
                new ArrayList<>(lastErrors.keySet())
        );
    }

    /**
     * Clear error and count for a module, then notify listeners of updated stats.
     *
     * @param moduleName the name of the module
     */
    public void clearError(String moduleName) {
        lastErrors.remove(moduleName);
        errorCounts.remove(moduleName);
        List<ErrorEvent> errors = errorHistory.get(moduleName);
        if (errors != null) {
            synchronized (errors) {
                errors.clear();
            }
        }
        broadcast(listener -> listener.onStatsUpdate(moduleName, getStats(moduleName)));
    }

    /**
     * Clear all errors for all modules.
     */
    public void clearAllErrors() {
        lastErrors.clear();
        errorCounts.clear();
        errorHistory.values().forEach(list -> {
            synchronized (list) {
                list.clear();
            }
        });
        broadcast(listener -> {
            for (String moduleName : registrationTimes.keySet()) {
                listener.onStatsUpdate(moduleName, getStats(moduleName));
            }
        });
    }

    /**
     * Reset all statistics for a module, keeping only registration info.
     *
     * @param moduleName the name of the module
     */
    public void resetModule(String moduleName) {
        if (registrationTimes.containsKey(moduleName)) {
            Instant regTime = registrationTimes.get(moduleName);

            lastErrors.remove(moduleName);
            errorCounts.remove(moduleName);
            stateChangeCounts.remove(moduleName);
            lastStateChangeTimes.remove(moduleName);

            List<ErrorEvent> errors = errorHistory.get(moduleName);
            if (errors != null) {
                synchronized (errors) {
                    errors.clear();
                }
            }

            List<StateTransition> history = stateHistory.get(moduleName);
            if (history != null) {
                synchronized (history) {
                    history.clear();
                    // Keep initial state
                    history.add(new StateTransition(ModuleState.UNLOADED, regTime));
                }
            }

            performanceMetrics.put(moduleName, new PerformanceMetrics());

            broadcast(listener -> listener.onStatsUpdate(moduleName, getStats(moduleName)));
        }
    }

    /**
     * Reset all statistics for all modules.
     */
    public void resetAll() {
        for (String moduleName : registrationTimes.keySet()) {
            resetModule(moduleName);
        }
    }

    /**
     * Register a listener. Immediately receives current stats for all modules.
     *
     * @param listener the listener to add
     */
    public void addListener(HealthListener listener) {
        listeners.add(listener);
        getAllStats().forEach(listener::onStatsUpdate);
    }

    /**
     * Unregister a listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(HealthListener listener) {
        listeners.remove(listener);
    }

    /**
     * Set the maximum size of state history to maintain per module.
     *
     * @param size the maximum number of state transitions to keep
     * @return this ModuleHealthMonitor instance for method chaining
     */
    public ModuleHealthMonitor setHistorySize(int size) {
        this.historySize = Math.max(1, size);
        return this;
    }

    /**
     * Set the maximum size of error history to maintain per module.
     *
     * @param size the maximum number of errors to keep
     * @return this ModuleHealthMonitor instance for method chaining
     */
    public ModuleHealthMonitor setErrorHistorySize(int size) {
        this.errorHistorySize = Math.max(1, size);
        return this;
    }

    /**
     * Return a detailed health report for all modules.
     *
     * @return a formatted string with health information for all modules
     */
    public String generateHealthReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== MODULE HEALTH REPORT ===\n");
        report.append("Generated: ").append(Instant.now()).append("\n");
        report.append("Total modules: ").append(registrationTimes.size()).append("\n\n");

        List<String> moduleNames = new ArrayList<>(registrationTimes.keySet());
        Collections.sort(moduleNames);

        for (String name : moduleNames) {
            ModuleStats stats = getStats(name);
            report.append("Module: ").append(name).append("\n");
            report.append("  State: ").append(stats.getCurrentState()).append("\n");
            report.append("  Registered: ").append(stats.getRegistrationTime()).append("\n");
            report.append("  Last state change: ").append(stats.getLastStateChangeTime()).append("\n");
            report.append("  State transitions: ").append(stats.getStateChangeCount()).append("\n");
            report.append("  Errors: ").append(stats.getErrorCount()).append("\n");

            // Add recent state transitions
            List<StateTransition> transitions = getStateHistory(name);
            report.append("  Recent transitions:\n");
            for (int i = Math.max(0, transitions.size() - 5); i < transitions.size(); i++) {
                StateTransition t = transitions.get(i);
                report.append("    ").append(t.getTimestamp())
                        .append(" -> ").append(t.getState()).append("\n");
            }

            // Add recent errors
            List<ErrorEvent> errors = getErrorHistory(name);
            if (!errors.isEmpty()) {
                report.append("  Recent errors:\n");
                for (int i = Math.max(0, errors.size() - 3); i < errors.size(); i++) {
                    ErrorEvent e = errors.get(i);
                    report.append("    ").append(e.getTimestamp())
                            .append(" [").append(e.getCategory()).append("] ")
                            .append(e.getError().getClass().getSimpleName())
                            .append(": ").append(e.getError().getMessage()).append("\n");
                }
            }

            // Add performance metrics
            PerformanceMetrics metrics = getPerformanceMetrics(name);
            report.append("  Performance:\n");
            report.append("    Init time: ").append(metrics.getInitTimeMs()).append(" ms\n");
            report.append("    Error rate: ").append(metrics.getErrorRate()).append(" errors/min\n");
            report.append("\n");
        }

        return report.toString();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Ensure a module is registered before recording data for it.
     */
    private void ensureRegistered(String moduleName) {
        if (!registrationTimes.containsKey(moduleName)) {
            registerModule(moduleName);
        }
    }

    /**
     * Categorize an error based on its type and message.
     */
    private ErrorCategory categorizeError(Throwable error) {
        if (error == null) {
            return ErrorCategory.UNKNOWN;
        }

        String className = error.getClass().getName().toLowerCase();
        String message = error.getMessage() != null ? error.getMessage().toLowerCase() : "";

        if (className.contains("timeout") || message.contains("timeout") ||
                message.contains("timed out")) {
            return ErrorCategory.TIMEOUT;
        }

        if (className.contains("io") || className.contains("file") ||
                message.contains("file") || message.contains("io") ||
                message.contains("read") || message.contains("write")) {
            return ErrorCategory.IO;
        }

        if (className.contains("null") || message.contains("null")) {
            return ErrorCategory.NULL_POINTER;
        }

        if (className.contains("config") || message.contains("config") ||
                message.contains("setting") || message.contains("property")) {
            return ErrorCategory.CONFIGURATION;
        }

        if (className.contains("thread") || className.contains("concurrent") ||
                message.contains("concurrent") || message.contains("thread") ||
                message.contains("deadlock") || message.contains("race")) {
            return ErrorCategory.CONCURRENCY;
        }

        if (className.contains("memory") || message.contains("memory") ||
                className.contains("outofmemory") || message.contains("heap")) {
            return ErrorCategory.MEMORY;
        }

        if (className.contains("network") || message.contains("network") ||
                message.contains("connect") || message.contains("socket")) {
            return ErrorCategory.NETWORK;
        }

        if (className.contains("runtime") || className.contains("illegal")) {
            return ErrorCategory.RUNTIME;
        }

        return ErrorCategory.OTHER;
    }

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

    // ────────────────────────────────────────────────────────────────────────────
    // Inner classes for data structures
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Enhanced listener interface with additional event types.
     */
    public interface HealthListener {
        /**
         * Called on any state transition.
         *
         * @param moduleName the name of the module
         * @param oldState the previous state, or null if this is the initial state
         * @param newState the new state
         * @param timestamp when the transition occurred
         */
        default void onStateChange(String moduleName, ModuleState oldState,
                                   ModuleState newState, Instant timestamp) {}

        /**
         * Called when a module reports an error.
         *
         * @param moduleName the name of the module
         * @param error the error that occurred
         * @param category the category of the error
         * @param timestamp when the error occurred
         */
        default void onError(String moduleName, Throwable error,
                             ErrorCategory category, Instant timestamp) {}

        /**
         * Called when aggregated stats update.
         *
         * @param moduleName the name of the module
         * @param stats the updated statistics
         */
        default void onStatsUpdate(String moduleName, ModuleStats stats) {}

        /**
         * Called when a performance alert is triggered.
         *
         * @param moduleName the name of the module
         * @param operationName the operation being measured
         * @param durationMs the duration in milliseconds
         */
        default void onPerformanceAlert(String moduleName, String operationName, long durationMs) {}

        /**
         * Legacy method for backward compatibility.
         *
         * @deprecated Use {@link #onStateChange(String, ModuleState, ModuleState, Instant)} instead
         */
        @Deprecated
        default void onStateChange(String moduleName, ModuleState newState) {
            onStateChange(moduleName, null, newState, Instant.now());
        }

        /**
         * Legacy method for backward compatibility.
         *
         * @deprecated Use {@link #onError(String, Throwable, ErrorCategory, Instant)} instead
         */
        @Deprecated
        default void onError(String moduleName, Throwable error) {
            onError(moduleName, error, categorizeErrorStatic(error), Instant.now());
        }
    }

    /**
     * Categorizes an error statically for the deprecated listener method.
     */
    private static ErrorCategory categorizeErrorStatic(Throwable error) {
        if (error == null) return ErrorCategory.UNKNOWN;
        String cn = error.getClass().getName().toLowerCase();
        if (cn.contains("timeout")) return ErrorCategory.TIMEOUT;
        if (cn.contains("io")) return ErrorCategory.IO;
        if (cn.contains("null")) return ErrorCategory.NULL_POINTER;
        return ErrorCategory.OTHER;
    }

    /**
     * Comprehensive statistics for a module.
     */
    public static class ModuleStats {
        private final String moduleName;
        private final ModuleState currentState;
        private final int errorCount;
        private final int stateChangeCount;
        private final Instant registrationTime;
        private final Instant lastStateChangeTime;
        private final long timeInCurrentStateMs;
        private final PerformanceMetrics performanceMetrics;

        public ModuleStats(String moduleName, ModuleState currentState, int errorCount,
                           int stateChangeCount, Instant registrationTime,
                           Instant lastStateChangeTime, long timeInCurrentStateMs,
                           PerformanceMetrics performanceMetrics) {
            this.moduleName = moduleName;
            this.currentState = currentState;
            this.errorCount = errorCount;
            this.stateChangeCount = stateChangeCount;
            this.registrationTime = registrationTime;
            this.lastStateChangeTime = lastStateChangeTime;
            this.timeInCurrentStateMs = timeInCurrentStateMs;
            this.performanceMetrics = performanceMetrics;
        }

        public String getModuleName() { return moduleName; }
        public ModuleState getCurrentState() { return currentState; }
        public int getErrorCount() { return errorCount; }
        public int getStateChangeCount() { return stateChangeCount; }
        public Instant getRegistrationTime() { return registrationTime; }
        public Instant getLastStateChangeTime() { return lastStateChangeTime; }
        public long getTimeInCurrentStateMs() { return timeInCurrentStateMs; }
        public PerformanceMetrics getPerformanceMetrics() { return performanceMetrics; }

        @Override
        public String toString() {
            return String.format("Module=%s, State=%s, Errors=%d, Transitions=%d, TimeInState=%d ms",
                    moduleName, currentState, errorCount, stateChangeCount, timeInCurrentStateMs);
        }
    }

    /**
     * Record of a state transition.
     */
    public static class StateTransition {
        private final ModuleState state;
        private final Instant timestamp;

        public StateTransition(ModuleState state, Instant timestamp) {
            this.state = state;
            this.timestamp = timestamp;
        }

        public ModuleState getState() { return state; }
        public Instant getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return timestamp + " -> " + state;
        }
    }

    /**
     * Record of an error event.
     */
    public static class ErrorEvent {
        private final Throwable error;
        private final Instant timestamp;
        private final ErrorCategory category;

        public ErrorEvent(Throwable error, Instant timestamp, ErrorCategory category) {
            this.error = error;
            this.timestamp = timestamp;
            this.category = category;
        }

        public Throwable getError() { return error; }
        public Instant getTimestamp() { return timestamp; }
        public ErrorCategory getCategory() { return category; }

        @Override
        public String toString() {
            return timestamp + " [" + category + "] " + error.getClass().getSimpleName() +
                    ": " + error.getMessage();
        }
    }

    /**
     * Categories of errors for better reporting and analysis.
     */
    public enum ErrorCategory {
        TIMEOUT,
        IO,
        NULL_POINTER,
        CONFIGURATION,
        CONCURRENCY,
        MEMORY,
        NETWORK,
        RUNTIME,
        OTHER,
        UNKNOWN
    }

    /**
     * Performance metrics collection for a module.
     */
    public static class PerformanceMetrics {
        private Instant firstInitTime = null;
        private Instant lastInitTime = null;
        private long initTimeMs = -1;
        private final Map<String, OperationMetrics> operations = new ConcurrentHashMap<>();
        private final long creationTimeMs = System.currentTimeMillis();
        private long lastErrorTimeMs = 0;
        private int errorCount = 0;

        public void recordInitComplete(Instant timestamp) {
            if (firstInitTime == null) {
                firstInitTime = timestamp;
            }
            lastInitTime = timestamp;

            if (firstInitTime != null) {
                initTimeMs = timestamp.toEpochMilli() - firstInitTime.toEpochMilli();
            }
        }

        public void recordOperation(String name, long durationMs) {
            operations.computeIfAbsent(name, k -> new OperationMetrics())
                    .recordExecution(durationMs);
        }

        public void recordError() {
            lastErrorTimeMs = System.currentTimeMillis();
            errorCount++;
        }

        public long getInitTimeMs() { return initTimeMs; }
        public Instant getFirstInitTime() { return firstInitTime; }
        public Instant getLastInitTime() { return lastInitTime; }

        public double getErrorRate() {
            long uptime = System.currentTimeMillis() - creationTimeMs;
            double minutes = uptime / 60000.0;
            return minutes > 0 ? errorCount / minutes : 0;
        }

        public Map<String, OperationMetrics> getOperations() {
            return Collections.unmodifiableMap(operations);
        }

        public long getLastErrorTimeMs() { return lastErrorTimeMs; }
        public int getErrorCount() { return errorCount; }

        @Override
        public String toString() {
            return String.format("InitTime=%d ms, ErrorRate=%.2f errors/min",
                    initTimeMs, getErrorRate());
        }
    }

    /**
     * Performance metrics for a specific operation.
     */
    public static class OperationMetrics {
        private long minMs = Long.MAX_VALUE;
        private long maxMs = 0;
        private long totalMs = 0;
        private int count = 0;

        public synchronized void recordExecution(long durationMs) {
            minMs = Math.min(minMs, durationMs);
            maxMs = Math.max(maxMs, durationMs);
            totalMs += durationMs;
            count++;
        }

        public long getMinMs() { return minMs == Long.MAX_VALUE ? 0 : minMs; }
        public long getMaxMs() { return maxMs; }
        public long getTotalMs() { return totalMs; }
        public int getCount() { return count; }
        public double getAvgMs() { return count > 0 ? (double)totalMs / count : 0; }

        @Override
        public String toString() {
            return String.format("Count=%d, Avg=%.2f ms, Min=%d ms, Max=%d ms",
                    count, getAvgMs(), getMinMs(), getMaxMs());
        }
    }
}