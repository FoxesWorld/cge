package org.foxesworld.cge.core.module;

import com.jme3.app.Application;
import com.jme3.app.state.AppStateManager;
import com.jme3.app.state.BaseAppState;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.module.health.ModuleHealthMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An abstract base class for all engine modules.
 * <p>
 * This class provides a robust, thread-safe foundation for creating modules with a managed lifecycle.
 * It handles asynchronous initialization, configuration loading, state management, scheduled updates,
 * and built-in error recovery mechanisms.
 *
 * <h3>Key Features:</h3>
 * <ul>
 *     <li><b>Asynchronous Lifecycle:</b> Initialization and updates are performed off the main render thread
 *     to prevent stalls and keep the application responsive.</li>
 *     <li><b>State Management:</b> Uses a well-defined {@link ModuleState} and thread-safe atomics to track the module's current status.</li>
 *     <li><b>Configuration Handling:</b> Seamlessly loads, reloads, and provides thread-safe access to a module-specific configuration object.</li>
 *     <li><b>Error Handling & Recovery:</b> Automatically catches exceptions, transitions the module to a safe state (PAUSED or FAILED), and provides a `recover()` mechanism.</li>
 *     <li><b>Coordination:</b> A global counter ensures that "all modules loaded" callbacks are fired only after every module has finished its initialization attempt.</li>
 * </ul>
 *
 * @param <ModuleConfig> The type of the module-specific configuration object.
 */
public abstract class EngineModule<ModuleConfig> extends BaseAppState {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // --- State Management ---
    private final AtomicBoolean isLoaded = new AtomicBoolean(false);
    private final AtomicReference<ModuleState> state = new AtomicReference<>(ModuleState.UNLOADED);
    private final AtomicReference<Throwable> lastError = new AtomicReference<>(null);
    private final AtomicBoolean recoveryInProgress = new AtomicBoolean(false);
    private final AtomicBoolean attached = new AtomicBoolean(false);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    // --- Engine Services ---
    protected final CalistaGameEngine gameEngine;
    protected final ConfigService configService;
    protected final TaskScheduler taskScheduler;

    // --- Configuration Management ---
    private final ReentrantReadWriteLock configLock = new ReentrantReadWriteLock();
    private volatile ModuleConfig config;
    private final String configFile;
    private final AtomicBoolean exportsConfig;
    private final Class<ModuleConfig> configClass;

    // --- Lifecycle Coordination ---
    private Future<?> initFuture;
    private volatile Runnable onAllModulesLoadedRunnable;
    private static final AtomicInteger modulesLoadingCount = new AtomicInteger(0);

    // --- Constants ---
    private static final int MAX_RECOVERY_ATTEMPTS = 3;
    private static final int INIT_TIMEOUT_SECONDS = 60;

    public EngineModule(Class<?> moduleClass, Class<ModuleConfig> configClass, CalistaGameEngine calistaGameEngine) {
        this(moduleClass, configClass, calistaGameEngine, false);
    }

    /**
     * Constructs an EngineModule.
     *
     * @param moduleClass       The class of the module, used to derive the config file name (e.g., "MyModule").
     * @param configClass       The class object of the configuration type. Can be null if the module has no config.
     * @param calistaGameEngine The central game engine instance.
     * @param exportsConfig     If true, registers the config with the {@link ConfigService} to make it globally accessible.
     * @throws NullPointerException if calistaGameEngine is null.
     */
    public EngineModule(Class<?> moduleClass, Class<ModuleConfig> configClass, CalistaGameEngine calistaGameEngine, boolean exportsConfig) {
        this.gameEngine = Objects.requireNonNull(calistaGameEngine, "Game engine cannot be null");
        this.configService = Objects.requireNonNull(calistaGameEngine.getConfigService(), "Config service cannot be null");
        this.taskScheduler = Objects.requireNonNull(calistaGameEngine.getTaskScheduler(), "Task scheduler cannot be null");
        this.configFile = moduleClass.getSimpleName();
        this.configClass = configClass;
        this.exportsConfig = new AtomicBoolean(exportsConfig);

        logger.debug("{} constructor: configFile='{}', configClass={}, exportsConfig={}",
                getName(), configFile, configClass != null ? configClass.getSimpleName() : "null", exportsConfig);

        if (configClass != null && configFile != null && !configFile.isEmpty()) {
            try {
                this.configService.registerConfig(configFile, configClass);
                logger.debug("Registered config '{}' for module {}", configFile, getName());
            } catch (Exception e) {
                logger.error("Failed to register config '{}' for {}: {}", configFile, getName(), e.getMessage(), e);
                lastError.set(e);
            }
        }
    }

    /**
     * Initializes the module asynchronously. This method is called by the JME {@link AppStateManager}.
     * It loads configuration and executes the module-specific {@link #initModule(CalistaGameEngine)} logic.
     *
     * @param app The JME application instance.
     */
    @Override
    protected void initialize(Application app) {
        if (isLoaded.get() || state.get() != ModuleState.UNLOADED) {
            return;
        }

        logger.info("{} initialize() start", getName());
        transitionTo(ModuleState.LOADING_CONFIG);
        modulesLoadingCount.incrementAndGet();

        // The main initialization task, executed on a background thread.
        initFuture = taskScheduler.getIoExecutor().submit(() -> {
            try {
                // Step 1: Load configuration
                if (configFile != null && !configFile.isEmpty() && configClass != null) {
                    logger.debug("{} loading config from '{}'", getName(), configFile);
                    ModuleConfig loadedConfig = configService.getConfig(configFile, exportsConfig.get());
                    if (loadedConfig == null) {
                        logger.warn("{} config loaded as null, attempting to create default instance", getName());
                        try {
                            loadedConfig = configClass.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            logger.error("{} failed to create default config instance: {}", getName(), e.getMessage());
                        }
                    }
                    // Safely set the config
                    setConfig(loadedConfig);
                }

                // Step 2: Run module-specific initialization
                transitionTo(ModuleState.INITIALIZING);
                logger.debug("{} calling initModule", getName());
                initModule(gameEngine);

                // Step 3: Transition to running state
                isLoaded.set(true);
                transitionTo(ModuleState.RUNNING);
                logger.info("{} initialized and running successfully", getName());

            } catch (Throwable t) {
                handleFailure(t, "initialize");
            } finally {
                // CRITICAL: Decrement the counter here to ensure it's always called exactly once.
                if (modulesLoadingCount.decrementAndGet() == 0) {
                    // This was the last module to finish loading, notify everyone.
                    logger.info("All modules have completed their initialization process.");
                    notifyAllModulesLoaded();
                }
            }
        });

        // A separate "watchdog" task to handle initialization timeouts.
        taskScheduler.getIoExecutor().execute(() -> {
            try {
                initFuture.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                // The initFuture took too long.
                logger.error("{} initialization timed out after {} seconds!", getName(), INIT_TIMEOUT_SECONDS);
                // CRITICAL: Cancel the original task to stop it from running and prevent resource leaks.
                initFuture.cancel(true);
                handleFailure(new TimeoutException("Module initialization timed out"), "initialize [Timeout]");
            } catch (Exception e) {
                // This catches exceptions if the future was cancelled or failed internally.
                // We don't need to log this as an error, as the original cause is handled elsewhere.
                logger.debug("{} watchdog caught exception: {}", getName(), e.getClass().getSimpleName());
            }
        });
    }

    /**
     * Schedules the per-frame update logic of this module. This method is called by the JME AppStateManager.
     * <p>
     * <b>Warning:</b> The {@link #updateModule(float)} method is executed on a background thread from the
     * {@link TaskScheduler}, NOT on the JME render thread. This is ideal for logic that doesn't need to be
     * synchronized with rendering (e.g., AI, networking). Do NOT modify the scene graph or call OpenGL-dependent
     * methods from {@code updateModule} without enqueuing the task back to the main thread.
     *
     * @param tpf Time per frame in seconds.
     */
    @Override
    public final void update(float tpf) {
        if (state.get() != ModuleState.RUNNING) {
            return;
        }
        try {
            updateModule(tpf);
        } catch (Throwable t) {
            handleFailure(t, "update");
        }
    }

    /**
     * Performs cleanup and shutdown of the module.
     *
     * @param app The JME application instance.
     */
    @Override
    public final void cleanup(Application app) {
        logger.info("{} cleanup() start, current state={}", getName(), state.get());
        transitionTo(ModuleState.SHUTTING_DOWN);

        if (initFuture != null && !initFuture.isDone()) {
            initFuture.cancel(true);
            logger.debug("{} pending initialization was cancelled.", getName());
        }

        try {
            cleanupModule(app);
            transitionTo(ModuleState.CLEANED_UP);
            logger.info("{} cleaned up successfully", getName());
        } catch (Throwable t) {
            handleFailure(t, "cleanup");
            transitionTo(ModuleState.CLEANED_UP); // Mark as cleaned up even if cleanup failed
        } finally {
            isLoaded.set(false);
            attached.set(false);
        }
    }

    // --- Public API ---

    /**
     * Reloads the module's configuration asynchronously.
     * After the config is reloaded, the {@link #onConfigReloaded()} callback is invoked.
     *
     * @return A {@link CompletableFuture} that completes with `true` on success, `false` otherwise.
     */
    public CompletableFuture<Boolean> reloadConfig() {
        logger.info("{} reloadConfig() called", getName());
        if (configFile == null || configFile.isEmpty() || configClass == null) {
            logger.warn("{} has no valid config file/class defined, skipping reload", getName());
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                ModuleConfig newConfig = configService.reloadConfig(configFile);
                if (newConfig != null) {
                    setConfig(newConfig);
                    logger.debug("{} new config applied", getName());
                    onConfigReloaded();
                    return true;
                } else {
                    logger.warn("{} config reload resulted in null config", getName());
                    return false;
                }
            } catch (Throwable t) {
                handleFailure(t, "reloadConfig");
                return false;
            }
        }, taskScheduler.getIoExecutor());
    }

    /**
     * Attempts to recover the module after a failure.
     * Recovery is only possible if the module is in a {@link ModuleState#PAUSED} or {@link ModuleState#FAILED}
     * state and has not exceeded the maximum recovery attempts.
     *
     * @return `true` if recovery was initiated, `false` otherwise.
     */
    public boolean recover() {
        ModuleState currentState = state.get();
        if (currentState != ModuleState.PAUSED && currentState != ModuleState.FAILED) {
            logger.warn("{} cannot recover: not in PAUSED or FAILED state (current: {})", getName(), currentState);
            return false;
        }
        if (failureCount.get() >= MAX_RECOVERY_ATTEMPTS) {
            logger.error("{} max recovery attempts ({}) reached, module cannot be recovered", getName(), MAX_RECOVERY_ATTEMPTS);
            transitionTo(ModuleState.FAILED);
            return false;
        }
        if (!recoveryInProgress.compareAndSet(false, true)) {
            logger.warn("{} recovery already in progress", getName());
            return false;
        }

        logger.info("{} attempting recovery (attempt {})", getName(), failureCount.get() + 1);
        CompletableFuture.runAsync(() -> {
            try {
                transitionTo(ModuleState.RECOVERING);
                initModule(gameEngine); // Re-run initialization logic
                transitionTo(ModuleState.RUNNING);
                lastError.set(null); // Clear last error on success
                logger.info("{} successfully recovered", getName());
            } catch (Throwable t) {
                logger.error("{} recovery failed: {}", getName(), t.getMessage(), t);
                failureCount.incrementAndGet();
                transitionTo(ModuleState.FAILED);
                lastError.set(t);
            } finally {
                recoveryInProgress.set(false);
            }
        }, taskScheduler.getIoExecutor());

        return true;
    }

    // --- Abstract Methods for Subclasses ---

    /**
     * One-time initialization logic executed after configuration loading.
     * Subclasses must implement this to set up their specific functionality.
     *
     * @param app The central game engine instance.
     * @throws Exception if initialization fails.
     */
    protected abstract void initModule(CalistaGameEngine app) throws Exception;

    /**
     * Per-frame update logic. This is executed on a background thread.
     *
     * @param tpf Time per frame in seconds.
     * @throws Exception if update logic fails.
     */
    protected abstract void updateModule(float tpf) throws Exception;

    /**
     * Shutdown and cleanup logic. Subclasses must use this to release all resources.
     *
     * @param app The application context provided by JME.
     * @throws Exception if cleanup fails.
     */
    protected abstract void cleanupModule(Application app) throws Exception;

    /**
     * Callback invoked after a successful configuration reload.
     * Subclasses should override this to react to configuration changes.
     *
     * @throws Exception if handling the reload fails.
     */
    public void onConfigReloaded() throws Exception {
        logger.debug("{} onConfigReloaded() called, but no override is provided.", getName());
    }

    // --- Getters and State Checks ---

    /**
     * Returns the current configuration object in a thread-safe manner.
     *
     * @return The module's configuration object.
     */
    public final ModuleConfig getConfig() {
        configLock.readLock().lock();
        try {
            return config;
        } finally {
            configLock.readLock().unlock();
        }
    }

    /**
     * Returns the current lifecycle state of the module.
     * @return The module's state.
     */
    public final ModuleState getState() { return state.get(); }

    /**
     * Checks if this module has completed its initialization and is in a running state.
     * @return `true` if the module is fully initialized and operational.
     */
    //public final boolean isInitialized() { return isLoaded.get() && state.get() == ModuleState.RUNNING; }

    /**
     * Checks if the module is in a recoverable state.
     * @return `true` if recovery can be attempted.
     */
    public final boolean isRecoverable() { return failureCount.get() < MAX_RECOVERY_ATTEMPTS; }

    /**
     * Gets the last error that occurred in this module.
     * @return The last recorded {@link Throwable}, or null if no errors have occurred.
     */
    public final Throwable getLastError() { return lastError.get(); }

    /** Returns the simple class name of this module, used for logging and identification. */
    protected final String getName() { return getClass().getSimpleName(); }

    /** Returns the shared game engine instance. */
    public final CalistaGameEngine getGameEngine() { return gameEngine; }

    /** Sets a runnable to be invoked only after ALL engine modules have completed initialization. */
    public final void setOnAllModulesLoadedRunnable(Runnable runnable) { this.onAllModulesLoadedRunnable = runnable; }

    /** Checks if this module has completed its loading process (successfully or not). */
    public final boolean isLoaded() { return isLoaded.get(); }

    // --- Internal Helpers ---

    private void transitionTo(ModuleState newState) {
        ModuleState oldState = state.getAndSet(newState);
        if (oldState != newState) {
            logger.debug("{}: {} -> {}", getName(), oldState, newState);
            if (ModuleHealthMonitor.isAvailable()) {
                ModuleHealthMonitor.getInstance().reportState(getName(), newState);
            }
        }
    }

    private void handleFailure(Throwable t, String phase) {
        if (t instanceof InterruptedException) {
            logger.warn("Module {} operation on phase '{}' was interrupted.", getName(), phase);
            Thread.currentThread().interrupt(); // Preserve the interrupted status
            return; // Don't treat interruption as a hard failure
        }
        logger.error("Module {} failed during '{}': {}", getName(), phase, t.getMessage(), t);
        lastError.set(t);

        int failures = failureCount.incrementAndGet();
        transitionTo(failures >= MAX_RECOVERY_ATTEMPTS ? ModuleState.FAILED : ModuleState.PAUSED);

        if (ModuleHealthMonitor.isAvailable()) {
            ModuleHealthMonitor.getInstance().reportError(getName(), t);
        }
    }

    private void notifyAllModulesLoaded() {
        if (onAllModulesLoadedRunnable != null) {
            taskScheduler.getIoExecutor().execute(() -> {
                try {
                    onAllModulesLoadedRunnable.run();
                } catch (Exception e) {
                    logger.error("{} error in onAllModulesLoaded callback: {}", getName(), e.getMessage(), e);
                }
            });
        }
    }

    private void setConfig(ModuleConfig newConfig) {
        configLock.writeLock().lock();
        try {
            this.config = newConfig;
        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Resets the global loading counter. Should be called by the ModuleManager before initiating a new batch load.
     */
    public static void resetLoadingCounter() {
        modulesLoadingCount.set(0);
    }

    protected String dumpBufferHex(ByteBuffer buf) {
        if (buf == null) {
            return "[null]";
        }
        byte[] bytes = new byte[buf.remaining()];

        ByteBuffer duplicate = buf.duplicate();
        duplicate.get(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Custom exception for module initialization timeouts. */
    public static class TimeoutException extends Exception {
        public TimeoutException(String message) { super(message); }
    }
}