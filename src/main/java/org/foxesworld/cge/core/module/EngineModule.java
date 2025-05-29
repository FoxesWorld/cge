package org.foxesworld.cge.core.module;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * Abstract base class for engine modules with async config, state-machine,
 * job-based update scheduling and centralized ConfigService.
 * Enhanced with detailed logging and NPE safeguards.
 * @param <T> configuration type
 */
public abstract class EngineModule<T> extends BaseAppState {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final CalistaGameEngine gameEngine;
    protected final ConfigService configService;
    protected final TaskScheduler taskScheduler;

    protected volatile T config;
    private final String configFile;
    private ModuleState state = ModuleState.UNLOADED;
    private Future<?> initFuture;

    public EngineModule(String configFile, Class<T> configClass, CalistaGameEngine calistaGameEngine) {
        this.gameEngine = calistaGameEngine;
        this.configService = calistaGameEngine.getConfigService();
        this.taskScheduler = calistaGameEngine.getTaskScheduler();
        this.configFile = configFile;

        logger.debug("{} constructor: configFile='{}', configClass={}", getName(), configFile, configClass != null ? configClass.getSimpleName() : "null");

        // Only register non-null config
        if (configFile != null && configClass != null) {
            try {
                this.configService.registerConfig(configFile, configClass);
                logger.debug("Registered config '{}' for module {}", configFile, getName());
            } catch (Exception e) {
                logger.error("Failed to register config '{}' for {}: {}", configFile, getName(), e.getMessage(), e);
            }
        } else {
            logger.debug("No config to register for module {}", getName());
        }
    }

    @Override
    protected void initialize(Application app) {
        logger.info("{} initialize() start", getName());
        transitionTo(ModuleState.LOADING_CONFIG);

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
            } catch (Throwable t) {
                handleFailure(t, "initialize");
            }
        });
        logger.info("Module initialized with config {}",config);
    }

    @Override
    public void update(float tpf) {
        if (state != ModuleState.RUNNING) {
            logger.trace("{} update skipped: current state={} (expected RUNNING)", getName(), state);
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
     * Hot-reload configuration and notify.
     */
    public void reloadConfig() {
        logger.info("{} reloadConfig() called", getName());
        if (configFile == null) {
            logger.warn("{} has no configFile, skipping reload", getName());
            return;
        }
        taskScheduler.submit(() -> {
            try {
                T newConfig = configService.reloadConfig(configFile);
                this.config = newConfig;
                logger.debug("{} new config applied", getName());
                onConfigReloaded();
            } catch (Throwable t) {
                handleFailure(t, "reloadConfig");
            }
        });
    }

    private void transitionTo(ModuleState newState) {
        logger.debug("{}: {} -> {}", getName(), state, newState);
        state = newState;
        ModuleHealthMonitor.getInstance().reportState(getName(), state);
    }

    private void handleFailure(Throwable t, String phase) {
        logger.error("Module {} failed during {}: {}", getName(), phase, t.getMessage(), t);
        transitionTo(ModuleState.PAUSED);
        ModuleHealthMonitor.getInstance().reportError(getName(), t);
    }

    public T getConfig() {
        return config;
    }

    protected String getName() {
        return getClass().getSimpleName();
    }

    /** Called after config hot-reload. */
    protected abstract void onConfigReloaded() throws Exception;
    /** One-time init logic after config loaded. */
    protected abstract void initModule(CalistaGameEngine app) throws Exception;
    /** Per-frame update logic */
    protected abstract void updateModule(float tpf) throws Exception;
    /** Shutdown/cleanup logic */
    protected abstract void cleanupModule(Application app) throws Exception;

    public ModuleState getState() { return state; }
    public ConfigService getConfigService() { return configService; }
    public TaskScheduler getTaskScheduler() { return taskScheduler; }
}