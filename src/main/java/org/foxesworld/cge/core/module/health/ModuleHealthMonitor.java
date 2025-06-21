package org.foxesworld.cge.core.module.health;

import org.foxesworld.cge.core.annotations.ThreadSafe;
import org.foxesworld.cge.core.module.ModuleState;
import org.foxesworld.cge.core.module.OperationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
 *   <li>Memory-efficient error tracking with adaptive sampling</li>
 *   <li>Automated health analysis with anomaly detection</li>
 * </ul>
 *
 * @author Calista Game Engine Team
 * @version 2.1
 */
@ThreadSafe
public class ModuleHealthMonitor {
    private static final Logger logger = LoggerFactory.getLogger(ModuleHealthMonitor.class);

    // Singleton for normal usage with lazy initialization and thread safety
    private static final AtomicReference<ModuleHealthMonitor> INSTANCE = new AtomicReference<>();

    // Initialization flag to check if the monitor is available
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Maximum number of transitions to keep in history
     */
    private static final int DEFAULT_HISTORY_SIZE = 50;

    /**
     * Maximum number of errors to track per module
     */
    private static final int DEFAULT_ERROR_HISTORY_SIZE = 20;

    /**
     * Default error sampling rate (store every n-th error when under pressure)
     */
    private static final int DEFAULT_ERROR_SAMPLING_RATE = 10;

    /**
     * Obtain the global monitor instance.
     * @return default ModuleHealthMonitor
     */
    public static ModuleHealthMonitor getInstance() {
        ModuleHealthMonitor instance = INSTANCE.get();
        if (instance == null) {
            synchronized (INSTANCE) {
                instance = INSTANCE.get();
                if (instance == null) {
                    instance = new ModuleHealthMonitor();
                    INSTANCE.set(instance);
                    initialized.set(true);
                }
            }
        }
        return instance;
    }

    /**
     * Checks if the health monitor is available.
     *
     * @return true if the monitor has been initialized
     */
    public static boolean isAvailable() {
        return initialized.get();
    }

    // Concurrency-safe state holders
    private final ConcurrentHashMap<String, ModuleState> moduleStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ErrorInfo> lastErrors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> errorCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> stateChangeCounts = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<HealthListener> listeners = new CopyOnWriteArrayList<>();

    // Enhanced statistics
    private final ConcurrentHashMap<String, Instant> registrationTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastStateChangeTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<StateTransition>> stateHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ErrorEvent>> errorHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PerformanceMetrics> performanceMetrics = new ConcurrentHashMap<>();

    // Size limits for histories
    private final AtomicInteger historySize = new AtomicInteger(DEFAULT_HISTORY_SIZE);
    private final AtomicInteger errorHistorySize = new AtomicInteger(DEFAULT_ERROR_HISTORY_SIZE);
    private final AtomicInteger errorSamplingRate = new AtomicInteger(DEFAULT_ERROR_SAMPLING_RATE);

    // Memory pressure detection
    private final AtomicBoolean underMemoryPressure = new AtomicBoolean(false);
    private final ScheduledExecutorService memoryMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ModuleHealthMemMonitor");
        t.setDaemon(true);
        return t;
    });

    /**
     * Public no-arg constructor to allow testing with fresh instances.
     */
    public ModuleHealthMonitor() {
        // Start memory pressure monitoring
        memoryMonitor.scheduleAtFixedRate(this::checkMemoryPressure, 30, 30, TimeUnit.SECONDS);

        // Runtime shutdown hook to clean up resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                memoryMonitor.shutdownNow();
            } catch (Exception e) {
                // Ignore exceptions during shutdown
            }
        }));

        logger.debug("ModuleHealthMonitor initialized at {}", Instant.now());
    }

    /**
     * Checks if the system is under memory pressure and adjusts tracking accordingly.
     */
    private void checkMemoryPressure() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double memoryUsageRatio = (double) usedMemory / maxMemory;

        boolean wasUnderPressure = underMemoryPressure.get();
        boolean isUnderPressure = memoryUsageRatio > 0.85; // 85% threshold

        if (isUnderPressure != wasUnderPressure) {
            underMemoryPressure.set(isUnderPressure);
            if (isUnderPressure) {
                logger.warn("System under memory pressure ({}% used). Reducing error tracking detail.",
                        Math.round(memoryUsageRatio * 100));
                // Reduce history sizes when under pressure
                pruneHistories();
            } else {
                logger.info("Memory pressure relieved ({}% used). Resuming normal operation.",
                        Math.round(memoryUsageRatio * 100));
            }
        }
    }

    /**
     * Prunes histories to reduce memory usage under pressure.
     */
    private void pruneHistories() {
        for (Map.Entry<String, CopyOnWriteArrayList<StateTransition>> entry : stateHistory.entrySet()) {
            CopyOnWriteArrayList<StateTransition> history = entry.getValue();
            int targetSize = historySize.get() / 2; // Reduce to half size
            while (history.size() > targetSize) {
                history.remove(0);
            }
        }

        for (Map.Entry<String, CopyOnWriteArrayList<ErrorEvent>> entry : errorHistory.entrySet()) {
            CopyOnWriteArrayList<ErrorEvent> history = entry.getValue();
            int targetSize = errorHistorySize.get() / 2; // Reduce to half size
            while (history.size() > targetSize) {
                history.remove(0);
            }
        }
    }

    /**
     * Register a module with the health monitoring system.
     * This should be called when a module is created.
     *
     * @param moduleName the name of the module to register
     * @return this ModuleHealthMonitor instance for method chaining
     * @throws NullPointerException if moduleName is null
     */
    public ModuleHealthMonitor registerModule(String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");

        Instant now = Instant.now();
        registrationTimes.putIfAbsent(moduleName, now);
        moduleStates.putIfAbsent(moduleName, ModuleState.UNLOADED);
        errorCounts.putIfAbsent(moduleName, new AtomicInteger(0));
        stateChangeCounts.putIfAbsent(moduleName, new AtomicInteger(0));

        // Use thread-safe collections
        stateHistory.computeIfAbsent(moduleName, k -> new CopyOnWriteArrayList<>())
                .add(new StateTransition(ModuleState.UNLOADED, now));

        errorHistory.putIfAbsent(moduleName, new CopyOnWriteArrayList<>());
        performanceMetrics.putIfAbsent(moduleName, new PerformanceMetrics());

        logger.debug("Module '{}' registered with health monitor at {}", moduleName, now);
        return this;
    }

    /**
     * Report a state transition for a module.
     *
     * @param moduleName the name of the module
     * @param newState the new state of the module
     * @throws NullPointerException if moduleName or newState is null
     */
    public void reportState(String moduleName, ModuleState newState) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(newState, "Module state cannot be null");

        ensureRegistered(moduleName);

        Instant timestamp = Instant.now();
        ModuleState oldState = moduleStates.put(moduleName, newState);
        lastStateChangeTimes.put(moduleName, timestamp);
        stateChangeCounts.computeIfAbsent(moduleName, k -> new AtomicInteger(0)).incrementAndGet();

        // Record transition in history
        CopyOnWriteArrayList<StateTransition> history = stateHistory.get(moduleName);
        history.add(new StateTransition(newState, timestamp));

        // Trim history if needed
        while (history.size() > historySize.get()) {
            try {
                history.remove(0);
            } catch (IndexOutOfBoundsException e) {
                // Ignore concurrent modification issues
                break;
            }
        }

        // Update performance metrics
        if (newState == ModuleState.RUNNING &&
                (oldState == ModuleState.INITIALIZING || oldState == ModuleState.RECOVERING)) {
            performanceMetrics.get(moduleName).recordInitComplete(timestamp);
        }

        logger.debug("Module '{}' state {} -> {} (transitions={}) at {}",
                moduleName,
                oldState == null ? "<none>" : oldState,
                newState,
                stateChangeCounts.get(moduleName).get(),
                timestamp);

        // Broadcast to listeners - Copy to avoid concurrent modification issues
        broadcastStateChange(moduleName, oldState, newState, timestamp);
    }

    /**
     * Report an error that occurred in a module.
     *
     * @param moduleName the name of the module
     * @param error the error that occurred
     * @throws NullPointerException if moduleName or error is null
     */
    public void reportError(String moduleName, Throwable error) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(error, "Error cannot be null");

        ensureRegistered(moduleName);

        Instant timestamp = Instant.now();

        // Store error info instead of full error object to avoid memory leaks
        String errorMessage = error.getMessage() != null ? error.getMessage() : "";
        String errorClass = error.getClass().getName();
        StackTraceElement[] stackTrace = error.getStackTrace();
        String location = stackTrace != null && stackTrace.length > 0 ?
                stackTrace[0].toString() : "Unknown location";

        ErrorInfo errorInfo = new ErrorInfo(errorClass, errorMessage, location, timestamp);
        lastErrors.put(moduleName, errorInfo);

        // Update error count
        int currentErrorCount = errorCounts.computeIfAbsent(moduleName, k -> new AtomicInteger(0))
                .incrementAndGet();

        // Categorize the error
        ErrorCategory category = categorizeError(error);

        // Create a memory-efficient error event
        ErrorEvent errorEvent = new ErrorEvent(errorInfo, timestamp, category);

        // Add to history based on sampling rate when under pressure
        boolean shouldRecord = true;
        if (underMemoryPressure.get()) {
            shouldRecord = currentErrorCount % errorSamplingRate.get() == 0;
        }

        CopyOnWriteArrayList<ErrorEvent> errors = errorHistory.get(moduleName);
        if (shouldRecord && errors != null) {
            errors.add(errorEvent);

            // Trim history if needed
            while (errors.size() > errorHistorySize.get()) {
                try {
                    errors.remove(0);
                } catch (IndexOutOfBoundsException e) {
                    // Ignore concurrent modification issues
                    break;
                }
            }
        }

        // Update performance metrics
        performanceMetrics.get(moduleName).recordError();

        logger.error("Module '{}' reported {} error (count={}): {}",
                moduleName,
                category,
                currentErrorCount,
                error.getMessage());

        // Broadcast to listeners
        broadcastError(moduleName, error, category, timestamp);
    }

    /**
     * Broadcasts a state change event to all listeners.
     */
    private void broadcastStateChange(String moduleName, ModuleState oldState,
                                      ModuleState newState, Instant timestamp) {
        ModuleStats stats = getStats(moduleName);
        for (HealthListener listener : listeners) {
            try {
                listener.onStateChange(moduleName, oldState, newState, timestamp);
                listener.onStatsUpdate(moduleName, stats);
            } catch (Exception e) {
                logger.warn("Error in listener while broadcasting state change: {}", e.getMessage());
            }
        }
    }

    /**
     * Broadcasts an error event to all listeners.
     */
    private void broadcastError(String moduleName, Throwable error,
                                ErrorCategory category, Instant timestamp) {
        ModuleStats stats = getStats(moduleName);
        for (HealthListener listener : listeners) {
            try {
                listener.onError(moduleName, error, category, timestamp);
                listener.onStatsUpdate(moduleName, stats);
            } catch (Exception e) {
                logger.warn("Error in listener while broadcasting error: {}", e.getMessage());
            }
        }
    }

    /**
     * Report performance metrics for a module.
     *
     * @param moduleName the name of the module
     * @param operationName the operation being measured
     * @param durationMs the duration in milliseconds
     * @throws NullPointerException if moduleName or operationName is null
     */
    public void reportPerformance(String moduleName, String operationName, long durationMs) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");
        Objects.requireNonNull(operationName, "Operation name cannot be null");

        ensureRegistered(moduleName);

        PerformanceMetrics metrics = performanceMetrics.get(moduleName);
        metrics.recordOperation(operationName, durationMs);

        // Only notify listeners for significant performance events
        if (durationMs > 100) { // Arbitrary threshold for "slow" operations
            for (HealthListener listener : listeners) {
                try {
                    listener.onPerformanceAlert(moduleName, operationName, durationMs);
                } catch (Exception e) {
                    logger.warn("Error in listener while broadcasting performance alert: {}", e.getMessage());
                }
            }
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
     * Get last reported error info of a module, or null if none.
     *
     * @param moduleName the name of the module
     * @return the last error info reported by the module, or null if none
     */
    public ErrorInfo getModuleErrorInfo(String moduleName) {
        return lastErrors.get(moduleName);
    }

    /**
     * Get total error count for a module.
     *
     * @param moduleName the name of the module
     * @return the number of errors reported by the module
     */
    public int getErrorCount(String moduleName) {
        AtomicInteger count = errorCounts.get(moduleName);
        return count != null ? count.get() : 0;
    }

    /**
     * Get total state transition count for a module.
     *
     * @param moduleName the name of the module
     * @return the number of state transitions for the module
     */
    public int getStateChangeCount(String moduleName) {
        AtomicInteger count = stateChangeCounts.get(moduleName);
        return count != null ? count.get() : 0;
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
        CopyOnWriteArrayList<StateTransition> history = stateHistory.get(moduleName);
        if (history == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    /**
     * Get the history of errors for a module.
     *
     * @param moduleName the name of the module
     * @return an unmodifiable list of error events
     */
    public List<ErrorEvent> getErrorHistory(String moduleName) {
        CopyOnWriteArrayList<ErrorEvent> errors = errorHistory.get(moduleName);
        if (errors == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(errors));
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
        Instant lastStateChangeTime = getLastStateChangeTime(moduleName);
        long timeInState = lastStateChangeTime != null ?
                ChronoUnit.MILLIS.between(lastStateChangeTime, Instant.now()) : 0;

        return new ModuleStats(
                moduleName,
                getModuleState(moduleName),
                getErrorCount(moduleName),
                getStateChangeCount(moduleName),
                getRegistrationTime(moduleName),
                lastStateChangeTime,
                timeInState,
                getPerformanceMetrics(moduleName)
        );
    }

    /**
     * Get an unmodifiable snapshot of all module states.
     *
     * @return map of module names to their current states
     */
    public Map<String, ModuleState> getAllModuleStates() {
        return new HashMap<>(moduleStates);
    }

    /**
     * Get an unmodifiable snapshot of all module errors.
     *
     * @return map of module names to their last error info
     */
    public Map<String, ErrorInfo> getAllModuleErrors() {
        return new HashMap<>(lastErrors);
    }

    /**
     * Get an unmodifiable snapshot of aggregated stats for all modules.
     *
     * @return map of module names to their comprehensive statistics
     */
    public Map<String, ModuleStats> getAllStats() {
        // Build a fresh map so stats reflect up-to-date values
        Map<String, ModuleStats> stats = new HashMap<>();
        Set<String> moduleNames = new HashSet<>(moduleStates.keySet());

        for (String name : moduleNames) {
            stats.put(name, getStats(name));
        }

        return Collections.unmodifiableMap(stats);
    }

    /**
     * Get a list of all registered modules.
     *
     * @return unmodifiable list of all registered module names
     */
    public List<String> getAllModuleNames() {
        return Collections.unmodifiableList(new ArrayList<>(registrationTimes.keySet()));
    }

    /**
     * Get a list of modules in a specific state.
     *
     * @param state the state to filter by
     * @return unmodifiable list of module names in the specified state
     */
    public List<String> getModulesInState(ModuleState state) {
        Objects.requireNonNull(state, "State cannot be null");

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
        return Collections.unmodifiableList(new ArrayList<>(lastErrors.keySet()));
    }

    /**
     * Get a list of modules in a failed or paused state.
     *
     * @return unmodifiable list of module names that are in a problematic state
     */
    public List<String> getFailedModules() {
        return moduleStates.entrySet().stream()
                .filter(e -> e.getValue() == ModuleState.FAILED || e.getValue() == ModuleState.PAUSED)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Clear error and count for a module, then notify listeners of updated stats.
     *
     * @param moduleName the name of the module
     */
    public void clearError(String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");

        lastErrors.remove(moduleName);
        AtomicInteger count = errorCounts.get(moduleName);
        if (count != null) {
            count.set(0);
        }

        CopyOnWriteArrayList<ErrorEvent> errors = errorHistory.get(moduleName);
        if (errors != null) {
            errors.clear();
        }

        // Update listeners with new stats
        ModuleStats stats = getStats(moduleName);
        for (HealthListener listener : listeners) {
            try {
                listener.onStatsUpdate(moduleName, stats);
            } catch (Exception e) {
                logger.warn("Error in listener during clearError: {}", e.getMessage());
            }
        }
    }

    /**
     * Clear all errors for all modules.
     */
    public void clearAllErrors() {
        lastErrors.clear();

        for (AtomicInteger count : errorCounts.values()) {
            count.set(0);
        }

        for (CopyOnWriteArrayList<ErrorEvent> errors : errorHistory.values()) {
            errors.clear();
        }

        // Update listeners
        Map<String, ModuleStats> allStats = getAllStats();
        for (HealthListener listener : listeners) {
            for (Map.Entry<String, ModuleStats> entry : allStats.entrySet()) {
                try {
                    listener.onStatsUpdate(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    logger.warn("Error in listener during clearAllErrors: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Reset all statistics for a module, keeping only registration info.
     *
     * @param moduleName the name of the module
     */
    public void resetModule(String moduleName) {
        Objects.requireNonNull(moduleName, "Module name cannot be null");

        if (registrationTimes.containsKey(moduleName)) {
            Instant regTime = registrationTimes.get(moduleName);

            // Clear error tracking
            lastErrors.remove(moduleName);
            AtomicInteger errorCount = errorCounts.get(moduleName);
            if (errorCount != null) {
                errorCount.set(0);
            }

            // Clear state tracking
            AtomicInteger stateCount = stateChangeCounts.get(moduleName);
            if (stateCount != null) {
                stateCount.set(0);
            }
            lastStateChangeTimes.remove(moduleName);

            // Clear histories
            CopyOnWriteArrayList<ErrorEvent> errors = errorHistory.get(moduleName);
            if (errors != null) {
                errors.clear();
            }

            CopyOnWriteArrayList<StateTransition> history = stateHistory.get(moduleName);
            if (history != null) {
                history.clear();
                // Keep initial state
                history.add(new StateTransition(ModuleState.UNLOADED, regTime));
            }

            // Reset performance metrics
            performanceMetrics.put(moduleName, new PerformanceMetrics());

            // Update listeners
            ModuleStats stats = getStats(moduleName);
            for (HealthListener listener : listeners) {
                try {
                    listener.onStatsUpdate(moduleName, stats);
                } catch (Exception e) {
                    logger.warn("Error in listener during resetModule: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Reset all statistics for all modules.
     */
    public void resetAll() {
        ArrayList<String> modules = new ArrayList<>(registrationTimes.keySet());
        for (String moduleName : modules) {
            resetModule(moduleName);
        }
    }

    /**
     * Register a listener. Immediately receives current stats for all modules.
     *
     * @param listener the listener to add
     * @throws NullPointerException if listener is null
     */
    public void addListener(HealthListener listener) {
        Objects.requireNonNull(listener, "Listener cannot be null");

        listeners.addIfAbsent(listener);

        // Send current stats to new listener
        Map<String, ModuleStats> stats = getAllStats();
        for (Map.Entry<String, ModuleStats> entry : stats.entrySet()) {
            try {
                listener.onStatsUpdate(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                logger.warn("Error sending initial stats to listener: {}", e.getMessage());
            }
        }
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
        this.historySize.set(Math.max(1, size));
        return this;
    }

    /**
     * Set the maximum size of error history to maintain per module.
     *
     * @param size the maximum number of errors to keep
     * @return this ModuleHealthMonitor instance for method chaining
     */
    public ModuleHealthMonitor setErrorHistorySize(int size) {
        this.errorHistorySize.set(Math.max(1, size));
        return this;
    }

    /**
     * Set the error sampling rate (when under memory pressure).
     * A value of 10 means store every 10th error.
     *
     * @param rate the sampling rate
     * @return this ModuleHealthMonitor instance for method chaining
     */
    public ModuleHealthMonitor setErrorSamplingRate(int rate) {
        this.errorSamplingRate.set(Math.max(1, rate));
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

        List<String> moduleNames = getAllModuleNames();
        report.append("Total modules: ").append(moduleNames.size()).append("\n");

        // Count modules in each state
        Map<ModuleState, Long> stateStats = moduleStates.values().stream()
                .collect(Collectors.groupingBy(state -> state, Collectors.counting()));

        report.append("Module states: ");
        stateStats.forEach((state, count) ->
                report.append(state).append("=").append(count).append(" ")
        );
        report.append("\n\n");

        // Sort modules by name
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
            if (!transitions.isEmpty()) {
                report.append("  Recent transitions:\n");
                int start = Math.max(0, transitions.size() - 5);
                for (int i = start; i < transitions.size(); i++) {
                    StateTransition t = transitions.get(i);
                    report.append("    ").append(t.getTimestamp())
                            .append(" -> ").append(t.getState()).append("\n");
                }
            }

            // Add recent errors
            List<ErrorEvent> errors = getErrorHistory(name);
            if (!errors.isEmpty()) {
                report.append("  Recent errors:\n");
                int start = Math.max(0, errors.size() - 3);
                for (int i = start; i < errors.size(); i++) {
                    ErrorEvent e = errors.get(i);
                    report.append("    ").append(e.getTimestamp())
                            .append(" [").append(e.getCategory()).append("] ")
                            .append(e.getErrorInfo().getErrorClass())
                            .append(": ").append(e.getErrorInfo().getMessage()).append("\n");
                }
            }

            // Add performance metrics
            PerformanceMetrics metrics = getPerformanceMetrics(name);
            report.append("  Performance:\n");
            report.append("    Init time: ").append(metrics.getInitTimeMs()).append(" ms\n");
            report.append("    Error rate: ").append(String.format("%.2f", metrics.getErrorRate())).append(" errors/min\n");

            // Add operation metrics if present
            Map<String, OperationMetrics> operations = metrics.getOperations();
            if (!operations.isEmpty()) {
                report.append("    Operations:\n");
                operations.forEach((opName, opMetrics) -> {
                    if (opMetrics.getCount() > 0) {
                        report.append("      ").append(opName).append(": avg=")
                                .append(String.format("%.2f", opMetrics.getAvgMs()))
                                .append("ms, count=").append(opMetrics.getCount())
                                .append("\n");
                    }
                });
            }

            report.append("\n");
        }

        return report.toString();
    }

    /**
     * Performs an automated health analysis for rapid problem detection.
     *
     * @return a map of module names to detected issues
     */
    public Map<String, List<String>> analyzeHealth() {
        Map<String, List<String>> issues = new HashMap<>();

        for (String moduleName : getAllModuleNames()) {
            List<String> moduleIssues = new ArrayList<>();
            ModuleStats stats = getStats(moduleName);

            // Check for problematic states
            ModuleState state = stats.getCurrentState();
            if (state == ModuleState.FAILED) {
                moduleIssues.add("Module is in FAILED state");
            } else if (state == ModuleState.PAUSED) {
                moduleIssues.add("Module is in PAUSED state");
            }

            // Check for high error rate
            PerformanceMetrics metrics = stats.getPerformanceMetrics();
            if (metrics.getErrorRate() > 10.0) { // More than 10 errors per minute
                moduleIssues.add(String.format("High error rate: %.1f errors/min", metrics.getErrorRate()));
            }

            // Check for excessive state transitions
            if (stats.getStateChangeCount() > 50) {
                moduleIssues.add("Excessive state transitions: " + stats.getStateChangeCount());
            }

            // Check for long time in initializing state
            if (state == ModuleState.INITIALIZING && stats.getTimeInCurrentStateMs() > 30000) {
                moduleIssues.add("Stuck in INITIALIZING state for " + stats.getTimeInCurrentStateMs()/1000 + " seconds");
            }

            // Check for slow operations
            metrics.getOperations().forEach((op, opMetrics) -> {
                if (opMetrics.getAvgMs() > 1000 && opMetrics.getCount() > 5) {
                    moduleIssues.add(String.format("Slow operation %s: %.1f ms avg", op, opMetrics.getAvgMs()));
                }
            });

            if (!moduleIssues.isEmpty()) {
                issues.put(moduleName, moduleIssues);
            }
        }

        return issues;
    }

    /**
     * Shutdown the health monitor and release resources.
     */
    public void shutdown() {
        try {
            memoryMonitor.shutdownNow();
        } catch (Exception e) {
            logger.warn("Error shutting down memory monitor: {}", e.getMessage());
        }

        listeners.clear();
        logger.info("ModuleHealthMonitor shutdown complete");
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

        // Check for timeout conditions
        if (className.contains("timeout") || message.contains("timeout") ||
                message.contains("timed out")) {
            return ErrorCategory.TIMEOUT;
        }

        // Check for IO/file issues
        if (className.contains("io") || className.contains("file") ||
                message.contains("file") || message.contains("io") ||
                message.contains("read") || message.contains("write")) {
            return ErrorCategory.IO;
        }

        // Check for null pointer
        if (className.contains("null") || message.contains("null")) {
            return ErrorCategory.NULL_POINTER;
        }

        // Check for configuration issues
        if (className.contains("config") || message.contains("config") ||
                message.contains("setting") || message.contains("property")) {
            return ErrorCategory.CONFIGURATION;
        }

        // Check for concurrency issues
        if (className.contains("thread") || className.contains("concurrent") ||
                message.contains("concurrent") || message.contains("thread") ||
                message.contains("deadlock") || message.contains("race") ||
                message.contains("synchroniz")) {
            return ErrorCategory.CONCURRENCY;
        }

        // Check for memory issues
        if (className.contains("memory") || message.contains("memory") ||
                className.contains("outofmemory") || message.contains("heap") ||
                message.contains("allocation")) {
            return ErrorCategory.MEMORY;
        }

        // Check for network issues
        if (className.contains("network") || message.contains("network") ||
                message.contains("connect") || message.contains("socket") ||
                message.contains("uri") || message.contains("url") ||
                message.contains("http")) {
            return ErrorCategory.NETWORK;
        }

        // Check for runtime issues
        if (className.contains("runtime") || className.contains("illegal") ||
                className.contains("unsupported")) {
            return ErrorCategory.RUNTIME;
        }

        // Check for initialization issues
        if (className.contains("init") || message.contains("init") ||
                message.contains("construct") || message.contains("instantiate")) {
            return ErrorCategory.INITIALIZATION;
        }

        return ErrorCategory.OTHER;
    }

    /**
     * Broadcast an update to all registered listeners.
     */
    private void broadcast(Consumer<HealthListener> notification) {
        for (HealthListener listener : listeners) {
            try {
                notification.accept(listener);
            } catch (Exception e) {
                logger.warn("Error in health listener: {}", e.getMessage());
            }
        }
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
     * Memory-efficient error information holder.
     */
    public static class ErrorInfo {
        private final String errorClass;
        private final String message;
        private final String location;
        private final Instant timestamp;

        public ErrorInfo(String errorClass, String message, String location, Instant timestamp) {
            this.errorClass = errorClass;
            this.message = message;
            this.location = location;
            this.timestamp = timestamp;
        }

        public String getErrorClass() {
            return errorClass;
        }

        public String getMessage() {
            return message;
        }

        public String getLocation() {
            return location;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return errorClass + ": " + message + " at " + location;
        }
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
            this.moduleName = Objects.requireNonNull(moduleName);
            this.currentState = currentState;
            this.errorCount = errorCount;
            this.stateChangeCount = stateChangeCount;
            this.registrationTime = registrationTime;
            this.lastStateChangeTime = lastStateChangeTime;
            this.timeInCurrentStateMs = timeInCurrentStateMs;
            this.performanceMetrics = Objects.requireNonNull(performanceMetrics);
        }

        public String getModuleName() { return moduleName; }
        public ModuleState getCurrentState() { return currentState; }
        public int getErrorCount() { return errorCount; }
        public int getStateChangeCount() { return stateChangeCount; }
        public Instant getRegistrationTime() { return registrationTime; }
        public Instant getLastStateChangeTime() { return lastStateChangeTime; }
        public long getTimeInCurrentStateMs() { return timeInCurrentStateMs; }
        public PerformanceMetrics getPerformanceMetrics() { return performanceMetrics; }

        /**
         * Checks if this module is in a healthy state.
         *
         * @return true if the module appears to be healthy
         */
        public boolean isHealthy() {
            return (currentState == ModuleState.RUNNING || currentState == ModuleState.INITIALIZING) &&
                    errorCount == 0 &&
                    performanceMetrics.getErrorRate() < 0.5; // Less than 1 error every 2 minutes
        }

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
            this.state = Objects.requireNonNull(state);
            this.timestamp = Objects.requireNonNull(timestamp);
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
        private final ErrorInfo errorInfo;
        private final Instant timestamp;
        private final ErrorCategory category;

        public ErrorEvent(ErrorInfo errorInfo, Instant timestamp, ErrorCategory category) {
            this.errorInfo = Objects.requireNonNull(errorInfo);
            this.timestamp = Objects.requireNonNull(timestamp);
            this.category = Objects.requireNonNull(category);
        }

        public ErrorInfo getErrorInfo() { return errorInfo; }
        public Instant getTimestamp() { return timestamp; }
        public ErrorCategory getCategory() { return category; }

        @Override
        public String toString() {
            return timestamp + " [" + category + "] " + errorInfo.getErrorClass() +
                    ": " + errorInfo.getMessage();
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
        INITIALIZATION,
        OTHER,
        UNKNOWN
    }

    /**
     * Performance metrics collection for a module.
     */
    public static class PerformanceMetrics {
        private final AtomicReference<Instant> firstInitTime = new AtomicReference<>();
        private final AtomicReference<Instant> lastInitTime = new AtomicReference<>();
        private final AtomicLong initTimeMs = new AtomicLong(-1);

        private final ConcurrentHashMap<String, OperationMetrics> operations = new ConcurrentHashMap<>();
        private final long creationTimeMs = System.currentTimeMillis();
        private final AtomicLong lastErrorTimeMs = new AtomicLong(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);

        // Time windows for error rate calculation
        private static final long ERROR_WINDOW_MS = 300_000; // 5 minutes
        private final ConcurrentLinkedQueue<Long> recentErrorTimes = new ConcurrentLinkedQueue<>();

        public void recordInitComplete(Instant timestamp) {
            Objects.requireNonNull(timestamp);

            if (firstInitTime.get() == null) {
                firstInitTime.set(timestamp);
            }
            lastInitTime.set(timestamp);

            if (firstInitTime.get() != null) {
                initTimeMs.set(ChronoUnit.MILLIS.between(firstInitTime.get(), timestamp));
            }
        }

        public void recordOperation(String name, long durationMs) {
            Objects.requireNonNull(name);

            operations.computeIfAbsent(name, k -> new OperationMetrics())
                    .recordExecution(durationMs);
        }

        public void recordError() {
            long now = System.currentTimeMillis();
            lastErrorTimeMs.set(now);
            errorCount.incrementAndGet();

            // Add to recent errors queue for rate calculation
            recentErrorTimes.add(now);

            // Clean up old error times
            pruneOldErrorTimes();
        }

        private void pruneOldErrorTimes() {
            long cutoff = System.currentTimeMillis() - ERROR_WINDOW_MS;
            while (!recentErrorTimes.isEmpty() && recentErrorTimes.peek() < cutoff) {
                recentErrorTimes.poll();
            }
        }

        public long getInitTimeMs() {
            return initTimeMs.get();
        }

        public Instant getFirstInitTime() {
            return firstInitTime.get();
        }

        public Instant getLastInitTime() {
            return lastInitTime.get();
        }

        public double getErrorRate() {
            pruneOldErrorTimes();
            int recentErrors = recentErrorTimes.size();

            // Calculate errors per minute based on recent error times
            double minutes = ERROR_WINDOW_MS / 60000.0;
            return minutes > 0 ? recentErrors / minutes : 0;
        }

        public Map<String, OperationMetrics> getOperations() {
            return Collections.unmodifiableMap(operations);
        }

        public long getLastErrorTimeMs() {
            return lastErrorTimeMs.get();
        }

        public int getErrorCount() {
            return errorCount.get();
        }

        @Override
        public String toString() {
            return String.format("InitTime=%d ms, ErrorRate=%.2f errors/min",
                    getInitTimeMs(), getErrorRate());
        }
    }
}