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
 * Abstract base class for engine modules providing asynchronous configuration loading,
 * state management, scheduled updates, and centralized configuration service access.
 * Enhanced with detailed logging, failure handling, and recovery mechanisms.
 *
 * @param <ModuleConfig> the type of the module-specific configuration object
 */
public abstract class EngineModule<ModuleConfig> extends BaseAppState {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // State management with proper thread safety
    private final AtomicBoolean isLoaded = new AtomicBoolean(false);
    private final AtomicReference<ModuleState> state = new AtomicReference<>(ModuleState.UNLOADED);
    private final AtomicReference<Throwable> lastError = new AtomicReference<>(null);
    private final AtomicBoolean recoveryInProgress = new AtomicBoolean(false);
    private final AtomicBoolean attached = new AtomicBoolean(false);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    // Engine services
    protected final CalistaGameEngine gameEngine;
    protected final ConfigService configService;
    protected final TaskScheduler taskScheduler;

    // Config management
    private final ReentrantReadWriteLock configLock = new ReentrantReadWriteLock();
    private volatile ModuleConfig config;
    private final String configFile;
    private final AtomicBoolean exportsConfig;
    private final Class<ModuleConfig> configClass;

    // Module loading and coordination
    private Future<?> initFuture;
    private volatile Runnable onAllModulesLoadedRunnable;
    private static final AtomicInteger modulesLoadingCount = new AtomicInteger(0);

    // Constants
    private static final int MAX_RECOVERY_ATTEMPTS = 3;
    private static final int INIT_TIMEOUT_SECONDS = 60;

    /**
     * Constructs an EngineModule with default behavior (config is registered).
     *
     * @param configFile        the path or identifier of this module's configuration file
     * @param configClass       the class object of the configuration type
     * @param calistaGameEngine the central game engine instance
     * @throws NullPointerException if calistaGameEngine is null
     */
    public EngineModule(String configFile, Class<ModuleConfig> configClass, CalistaGameEngine calistaGameEngine) {
        this(configFile, configClass, calistaGameEngine, true);
    }

    /**
     * Constructs an EngineModule with optional config registration.
     *
     * @param configFile        the path or identifier of this module's configuration file
     * @param configClass       the class object of the configuration type
     * @param calistaGameEngine the central game engine instance
     * @param exportsConfig     if true, registers the config with the ConfigService
     * @throws NullPointerException if calistaGameEngine is null
     */
    public EngineModule(String configFile, Class<ModuleConfig> configClass, CalistaGameEngine calistaGameEngine, boolean exportsConfig) {
        this.gameEngine = Objects.requireNonNull(calistaGameEngine, "Game engine cannot be null");
        this.configService = Objects.requireNonNull(calistaGameEngine.getConfigService(), "Config service cannot be null");
        this.taskScheduler = Objects.requireNonNull(calistaGameEngine.getTaskScheduler(), "Task scheduler cannot be null");
        this.configFile = configFile;
        this.configClass = configClass;
        this.exportsConfig = new AtomicBoolean(exportsConfig);

        logger.debug("{} constructor: configFile='{}', configClass={}, exportsConfig={}",
                getName(), configFile, configClass != null ? configClass.getSimpleName() : "null", exportsConfig);

        // Register configuration if needed
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
     * Initializes the module by loading configuration, invoking module-specific
     * initialization logic, and scheduling the "all modules loaded" callback when done.
     * This method is called by the JME AppStateManager when the state is attached.
     *
     * @param app the application context provided by JME
     */
    @Override
    protected void initialize(Application app) {
        if (isLoaded.get()) {
            logger.warn("{} initialize() called again while already loaded", getName());
            return;
        }

        logger.info("{} initialize() start", getName());
        transitionTo(ModuleState.LOADING_CONFIG);
        modulesLoadingCount.incrementAndGet();

        initFuture = taskScheduler.submit(() -> {
            try {
                // Load configuration if specified
                if (configFile != null && !configFile.isEmpty()) {
                    logger.debug("{} loading config from '{}'", getName(), configFile);

                    try {
                        configLock.writeLock().lock();
                        config = configService.getConfig(configFile, exportsConfig.get());
                    } finally {
                        configLock.writeLock().unlock();
                    }

                    if (config == null && configClass != null) {
                        logger.warn("{} config loaded as null, attempting to create default instance", getName());
                        try {
                            config = configClass.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            logger.error("{} failed to create default config instance: {}", getName(), e.getMessage(), e);
                        }
                    }
                }

                // Initialize the module
                transitionTo(ModuleState.INITIALIZING);
                logger.debug("{} calling initModule", getName());
                initModule(gameEngine);

                // Transition to running state
                transitionTo(ModuleState.RUNNING);

                // Mark as loaded and check if all modules are loaded
                isLoaded.set(true);
                logger.info("{} initialized and running", getName());

                if (modulesLoadingCount.decrementAndGet() == 0) {
                    notifyAllModulesLoaded();
                }

            } catch (Throwable t) {
                handleFailure(t, "initialize");
                modulesLoadingCount.decrementAndGet();
            }
        });

        // Add timeout handling for initialization
        CompletableFuture.runAsync(() -> {
            try {
                if (!initFuture.isDone()) {
                    boolean completed = initFuture.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS) != null;
                    if (!completed && state.get() != ModuleState.RUNNING) {
                        logger.error("{} initialization timed out after {} seconds", getName(), INIT_TIMEOUT_SECONDS);
                        handleFailure(new TimeoutException("Module initialization timed out"), "initialize");
                        modulesLoadingCount.decrementAndGet();
                    }
                }
            } catch (Exception e) {
                // The future handling itself failed
                logger.error("{} error while waiting for initialization completion: {}", getName(), e.getMessage(), e);
            }
        }, taskScheduler.getExecutor());
    }

    /**
     * Schedules the per-frame update logic of this module if running.
     * This method is called by the JME AppStateManager each frame.
     *
     * @param tpf time per frame in seconds
     */
    @Override
    public void update(float tpf) {
        if (state.get() != ModuleState.RUNNING) {
            return;
        }

        taskScheduler.submit(() -> {
            try {
                updateModule(tpf);
            } catch (Throwable t) {
                handleFailure(t, "update");
            }
        });
    }

    /**
     * Called when the AppState is attached to the AppStateManager.
     * Tracks the attachment status.
     */
    @Override
    public void stateAttached(AppStateManager stateManager) {
        super.stateAttached(stateManager);
        attached.set(true);
        logger.debug("{} attached to AppStateManager", getName());
    }

    /**
     * Called when the AppState is detached from the AppStateManager.
     * Updates the attachment status.
     */
    @Override
    public void stateDetached(AppStateManager stateManager) {
        super.stateDetached(stateManager);
        attached.set(false);
        logger.debug("{} detached from AppStateManager", getName());
    }

    /**
     * Performs cleanup and shutdown of the module, cancelling initialization if pending.
     * This method is called by the JME AppStateManager when the state is detached.
     *
     * @param app the application context provided by JME
     */
    @Override
    protected void cleanup(Application app) {
        logger.info("{} cleanup() start, current state={}", getName(), state.get());
        transitionTo(ModuleState.SHUTTING_DOWN);

        // Cancel initialization if in progress
        if (initFuture != null && !initFuture.isDone()) {
            initFuture.cancel(true);
            logger.debug("{} initFuture cancelled", getName());
        }

        try {
            cleanupModule(app);
            transitionTo(ModuleState.CLEANED_UP);
            logger.info("{} cleaned up successfully", getName());
        } catch (Throwable t) {
            handleFailure(t, "cleanup");
            // Still mark as cleaned up even if there was an error
            transitionTo(ModuleState.CLEANED_UP);
        } finally {
            isLoaded.set(false);
        }
    }

    /**
     * Reloads the module's configuration asynchronously and triggers the reload callback.
     *
     * @return A CompletableFuture that completes when the reload is finished
     */
    public CompletableFuture<Boolean> reloadConfig() {
        logger.info("{} reloadConfig() called", getName());

        if (configFile == null || configFile.isEmpty()) {
            logger.warn("{} has no configFile, skipping reload", getName());
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                ModuleConfig newConfig = configService.reloadConfig(configFile);

                if (newConfig != null) {
                    try {
                        configLock.writeLock().lock();
                        this.config = newConfig;
                    } finally {
                        configLock.writeLock().unlock();
                    }

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
        }, taskScheduler.getExecutor());
    }

    /**
     * Attempts to recover the module after failure.
     * This will reinitialize the module if possible.
     *
     * @return true if recovery was initiated, false if recovery is not possible
     */
    public boolean recover() {
        ModuleState currentState = state.get();

        if (currentState != ModuleState.PAUSED && currentState != ModuleState.FAILED) {
            logger.warn("{} cannot recover: not in PAUSED or FAILED state (current: {})", getName(), currentState);
            return false;
        }

        if (failureCount.get() >= MAX_RECOVERY_ATTEMPTS) {
            logger.error("{} max recovery attempts ({}) reached, module cannot be recovered",
                    getName(), MAX_RECOVERY_ATTEMPTS);
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
                // Reset state for recovery
                transitionTo(ModuleState.RECOVERING);

                // Reload configuration first
                if (configFile != null && !configFile.isEmpty()) {
                    try {
                        configLock.writeLock().lock();
                        config = configService.reloadConfig(configFile);
                        logger.debug("{} config reloaded for recovery", getName());
                    } catch (Exception e) {
                        logger.warn("{} failed to reload config during recovery: {}", getName(), e.getMessage());
                    } finally {
                        configLock.writeLock().unlock();
                    }
                }

                // Reinitialize module
                logger.debug("{} reinitializing module for recovery", getName());
                initModule(gameEngine);

                // Transition to running state if successful
                transitionTo(ModuleState.RUNNING);
                logger.info("{} successfully recovered", getName());

                // Reset failure tracking
                lastError.set(null);

            } catch (Throwable t) {
                logger.error("{} recovery failed: {}", getName(), t.getMessage(), t);
                failureCount.incrementAndGet();
                transitionTo(ModuleState.FAILED);
                lastError.set(t);
            } finally {
                recoveryInProgress.set(false);
            }
        }, taskScheduler.getExecutor());

        return true;
    }

    /**
     * Transitions the module to a new state and reports health.
     *
     * @param newState the target module state
     */
    private void transitionTo(ModuleState newState) {
        ModuleState oldState = state.getAndSet(newState);
        logger.debug("{}: {} -> {}", getName(), oldState, newState);

        // Report state change to health monitor if available
        try {
            if (ModuleHealthMonitor.isAvailable()) {
                ModuleHealthMonitor.getInstance().reportState(getName(), newState);
            }
        } catch (Exception e) {
            logger.warn("{} failed to report state to health monitor: {}", getName(), e.getMessage());
        }
    }

    /**
     * Handles any failures during module phases by logging, storing the exception,
     * and transitioning to appropriate failure state.
     *
     * @param t     the throwable that occurred
     * @param phase the phase during which the error happened
     */
    private void handleFailure(Throwable t, String phase) {
        logger.error("Module {} failed during {}: {}", getName(), phase, t.getMessage(), t);
        lastError.set(t);

        int failures = failureCount.incrementAndGet();
        if (failures >= MAX_RECOVERY_ATTEMPTS) {
            transitionTo(ModuleState.FAILED);
            logger.error("{} has failed permanently after {} failures", getName(), failures);
        } else {
            transitionTo(ModuleState.PAUSED);
        }

        // Report error to health monitor if available
        try {
            if (ModuleHealthMonitor.isAvailable()) {
                ModuleHealthMonitor.getInstance().reportError(getName(), t);
            }
        } catch (Exception e) {
            logger.warn("{} failed to report error to health monitor: {}", getName(), e.getMessage());
        }
    }

    /**
     * Executes the onAllModulesLoaded callback if defined.
     */
    private void notifyAllModulesLoaded() {
        if (onAllModulesLoadedRunnable != null) {
            try {
                onAllModulesLoadedRunnable.run();
                logger.debug("{} executed onAllModulesLoaded callback", getName());
            } catch (Exception e) {
                logger.error("{} error in onAllModulesLoaded callback: {}", getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Returns the current configuration object thread-safely.
     *
     * @return the module configuration of type ModuleConfig
     */
    public ModuleConfig getConfig() {
        try {
            configLock.readLock().lock();
            return config;
        } finally {
            configLock.readLock().unlock();
        }
    }

    /**
     * Updates the configuration object thread-safely.
     *
     * @param newConfig the new configuration to apply
     */
    public void setConfig(ModuleConfig newConfig) {
        try {
            configLock.writeLock().lock();
            this.config = newConfig;
        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Returns the simple class name of this module.
     *
     * @return the module's name
     */
    protected String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Callback invoked after a successful configuration reload.
     * Subclasses should override this to handle configuration changes.
     *
     * @throws Exception if reload handling fails
     */
    public abstract void onConfigReloaded() throws Exception;

    /**
     * One-time initialization logic executed after configuration loading.
     * Subclasses must implement this to set up their specific functionality.
     *
     * @param app the central game engine instance
     * @throws Exception if initialization fails
     */
    protected abstract void initModule(CalistaGameEngine app) throws Exception;

    /**
     * Per-frame update logic executed when the module is running.
     * Subclasses should implement this for their regular processing.
     *
     * @param tpf time per frame in seconds
     * @throws Exception if update logic fails
     */
    protected abstract void updateModule(float tpf) throws Exception;

    /**
     * Shutdown and cleanup logic executed during application teardown.
     * Subclasses must use this to release resources.
     *
     * @param app the application context provided by JME
     * @throws Exception if cleanup fails
     */
    protected abstract void cleanupModule(Application app) throws Exception;

    /**
     * Returns the current lifecycle state of the module.
     *
     * @return the module's state
     */
    public ModuleState getState() {
        return state.get();
    }

    /**
     * Checks if this module is attached to the AppStateManager.
     *
     * @return true if attached, false otherwise
     */
    public boolean isAttached() {
        return attached.get();
    }

    /**
     * Checks if this module has completed initialization successfully.
     *
     * @return true if loaded, false otherwise
     */
    //public boolean isInitialized() {
    //    return isLoaded.get() && state.get() == ModuleState.RUNNING;
    //}

    /**
     * Checks if this module is in a failed state.
     *
     * @return true if failed, false otherwise
     */
    public boolean isFailed() {
        ModuleState currentState = state.get();
        return currentState == ModuleState.FAILED || currentState == ModuleState.PAUSED;
    }

    /**
     * Checks if this module can be recovered after failure.
     *
     * @return true if recoverable, false if permanently failed or not in a failure state
     */
    public boolean isRecoverable() {
        if (state.get() != ModuleState.PAUSED && state.get() != ModuleState.FAILED) {
            return false;
        }
        return failureCount.get() < MAX_RECOVERY_ATTEMPTS;
    }

    /**
     * Gets the last error that occurred in this module.
     *
     * @return the last error or null if no errors occurred
     */
    public Throwable getLastError() {
        return lastError.get();
    }

    /**
     * Provides access to the shared configuration service.
     *
     * @return the ConfigService instance
     */
    public ConfigService getConfigService() {
        return configService;
    }

    /**
     * Provides access to the shared task scheduler.
     *
     * @return the TaskScheduler instance
     */
    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    /**
     * Gets the core game engine.
     *
     * @return the game engine instance
     */
    public CalistaGameEngine getGameEngine() {
        return gameEngine;
    }

    /**
     * Dumps the remaining bytes of a ByteBuffer as a hexadecimal string.
     * This is a utility function for debugging binary data.
     *
     * @param buf the ByteBuffer to dump
     * @return hex-formatted string of remaining bytes
     */
    protected String dumpBufferHex(ByteBuffer buf) {
        if (buf == null) {
            return "[null]";
        }

        byte[] bytes = new byte[buf.remaining()];
        // Create a duplicate so we don't affect the buffer's position
        ByteBuffer duplicate = buf.duplicate();
        duplicate.get(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Sets a runnable to be invoked when all engine modules have completed initialization.
     *
     * @param runnable callback to execute upon all modules loaded
     */
    public void setOnAllModulesLoadedRunnable(Runnable runnable) {
        this.onAllModulesLoadedRunnable = runnable;
    }

    /**
     * Returns whether this module has completed loading.
     *
     * @return true if the module is loaded
     */
    public boolean isLoaded() {
        return isLoaded.get();
    }

    /**
     * Exception class for module initialization timeout.
     */
    public static class TimeoutException extends Exception {
        public TimeoutException(String message) {
            super(message);
        }
    }
}