package org.foxesworld.cge.core.module;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for engine modules providing asynchronous configuration loading,
 * state management, scheduled updates, and centralized configuration service access.
 * Enhanced with detailed logging and failure handling.
 *
 * @param <ModuleConfig> the type of the module-specific configuration object
 */
public abstract class EngineModule<ModuleConfig> extends BaseAppState {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private AtomicBoolean isLoaded = new AtomicBoolean(false);
    protected final CalistaGameEngine gameEngine;
    protected final ConfigService configService;
    protected final TaskScheduler taskScheduler;
    private volatile ModuleConfig config;
    private final String configFile;
    private ModuleState state = ModuleState.UNLOADED;
    private Future<?> initFuture;
    private Runnable onAllModulesLoadedRunnable;
    private static final AtomicInteger modulesLoadingCount = new AtomicInteger(0);

    /**
     * Constructs an EngineModule instance, registers its configuration if provided,
     * and initializes core dependencies.
     *
     * @param configFile       the path or identifier of this module's configuration file
     * @param configClass      the class object of the configuration type
     * @param calistaGameEngine the central game engine instance
     */
    public EngineModule(String configFile, Class<ModuleConfig> configClass, CalistaGameEngine calistaGameEngine) {
        this.gameEngine = calistaGameEngine;
        this.configService = calistaGameEngine.getConfigService();
        this.taskScheduler = calistaGameEngine.getTaskScheduler();
        this.configFile = configFile;
        logger.debug("{} constructor: configFile='{}', configClass={} ", getName(), configFile, configClass != null ? configClass.getSimpleName() : "null");
        initialize(calistaGameEngine);
        if (configFile != null && configClass != null) {
            try {
                this.configService.registerConfig(configFile, configClass);
                logger.debug("Registered config '{}' for module {}", configFile, getName());
            } catch (Exception e) {
                logger.error("Failed to register config '{}' for {}: {}", configFile, getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Initializes the module by loading configuration, invoking module-specific
     * initialization logic, and scheduling the "all modules loaded" callback when done.
     *
     * @param app the application context provided by JME
     */
    @Override
    protected void initialize(Application app) {
        logger.info("{} initialize() start", getName());
        transitionTo(ModuleState.LOADING_CONFIG);
        modulesLoadingCount.incrementAndGet();
        initFuture = taskScheduler.submit(() -> {
            try {
                if (configFile != null) {
                    logger.debug("{} loading config from '{}'", getName(), configFile);
                    config = configService.getConfig(configFile);
                }
                transitionTo(ModuleState.INITIALIZING);
                logger.debug("{} calling initModule", getName());
                initModule(gameEngine);
                transitionTo(ModuleState.RUNNING);
                logger.info("{} initialized and running", getName());
                if (modulesLoadingCount.decrementAndGet() == 0 && onAllModulesLoadedRunnable != null) {
                    onAllModulesLoadedRunnable.run();
                }
            } catch (Throwable t) {
                handleFailure(t, "initialize");
            }
        });
        isLoaded.set(true);
    }

    /**
     * Schedules the per-frame update logic of this module if running.
     *
     * @param tpf time per frame in seconds
     */
    @Override
    public void update(float tpf) {
        if (state != ModuleState.RUNNING) {
            logger.trace("{} update skipped: state={} (expected RUNNING)", getName(), state);
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
     * Performs cleanup and shutdown of the module, cancelling initialization if pending.
     *
     * @param app the application context provided by JME
     */
    @Override
    protected void cleanup(Application app) {
        logger.info("{} cleanup() start, current state={}", getName(), state);
        transitionTo(ModuleState.SHUTTING_DOWN);
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
        }
    }

    /**
     * Reloads the module's configuration asynchronously and triggers the reload callback.
     */
    public void reloadConfig() {
        logger.info("{} reloadConfig() called", getName());
        if (configFile == null) {
            logger.warn("{} has no configFile, skipping reload", getName());
            return;
        }
        taskScheduler.submit(() -> {
            try {
                ModuleConfig newConfig = configService.reloadConfig(configFile);
                this.config = newConfig;
                logger.debug("{} new config applied", getName());
                onConfigReloaded();
            } catch (Throwable t) {
                handleFailure(t, "reloadConfig");
            }
        });
    }

    /**
     * Transitions the module to a new state and reports health.
     *
     * @param newState the target module state
     */
    private void transitionTo(ModuleState newState) {
        logger.debug("{}: {} -> {}", getName(), state, newState);
        state = newState;
        ModuleHealthMonitor.getInstance().reportState(getName(), state);
    }

    /**
     * Handles any failures during module phases by logging and pausing the module.
     *
     * @param t     the throwable that occurred
     * @param phase the phase during which the error happened
     */
    private void handleFailure(Throwable t, String phase) {
        logger.error("Module {} failed during {}: {}", getName(), phase, t.getMessage(), t);
        transitionTo(ModuleState.PAUSED);
        ModuleHealthMonitor.getInstance().reportError(getName(), t);
    }

    /**
     * Returns the current configuration object.
     *
     * @return the module configuration of type T
     */
    public ModuleConfig getConfig() {
        return config;
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
     *
     * @throws Exception if reload handling fails
     */
    protected abstract void onConfigReloaded() throws Exception;

    /**
     * One-time initialization logic executed after configuration loading.
     *
     * @param app the central game engine instance
     * @throws Exception if initialization fails
     */
    protected abstract void initModule(CalistaGameEngine app) throws Exception;

    /**
     * Per-frame update logic executed when the module is running.
     *
     * @param tpf time per frame in seconds
     * @throws Exception if update logic fails
     */
    protected abstract void updateModule(float tpf) throws Exception;

    /**
     * Shutdown and cleanup logic executed during application teardown.
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
        return state;
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
     * Dumps the remaining bytes of a ByteBuffer as a hexadecimal string.
     *
     * @param buf the ByteBuffer to dump
     * @return hex-formatted string of remaining bytes
     */
    protected String dumpBufferHex(ByteBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.slice().get(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public CalistaGameEngine getGameEngine() {
        return gameEngine;
    }

    /**
     * Sets a runnable to be invoked when all engine modules have completed initialization.
     *
     * @param runnable callback to execute upon all modules loaded
     */
    public void setOnAllModulesLoadedRunnable(Runnable runnable) {
        this.onAllModulesLoadedRunnable = runnable;
    }

    public AtomicBoolean getIsLoaded() {
        return isLoaded;
    }
}