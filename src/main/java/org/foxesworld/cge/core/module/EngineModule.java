package org.foxesworld.cge.core.module;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.module.ModuleHealthMonitor;
import org.foxesworld.cge.core.module.ModuleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class EngineModule<ModuleConfig> extends BaseAppState {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final CalistaGameEngine gameEngine;
    protected final ConfigService configService;
    protected final TaskScheduler taskScheduler;

    private final String configFile;
    private final Class<ModuleConfig> configClass;
    private volatile ModuleConfig config;

    private ModuleState state = ModuleState.UNLOADED;
    private Runnable onAllModulesLoadedRunnable;
    private static final AtomicInteger modulesLoadingCount = new AtomicInteger(0);

    private static final ExecutorService initExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
    );

    public EngineModule(String configFile, Class<ModuleConfig> configClass, CalistaGameEngine calistaGameEngine) {
        this.configFile = configFile;
        this.configClass = configClass;
        this.gameEngine = calistaGameEngine;
        this.configService = calistaGameEngine.getConfigService();
        this.taskScheduler = calistaGameEngine.getTaskScheduler();

        initialize(calistaGameEngine);
        if (configFile != null && configClass != null) {
            try {
                this.configService.registerConfig(configFile, configClass);
                logger.debug("Registered config '{}' for module {}", configFile, getName());
            } catch (Exception e) {
                logger.error("Config registration failed for '{}': {}", configFile, e.getMessage(), e);
            }
        }
    }

    @Override
    protected void initialize(Application app) {
        logger.info("{} initialize() started", getName());
        transitionTo(ModuleState.LOADING_CONFIG);
        modulesLoadingCount.incrementAndGet();

        CompletableFuture
                .supplyAsync(this::loadConfig, initExecutor)
                .thenCompose(loadedConfig -> {
                    this.config = loadedConfig;
                    transitionTo(ModuleState.INITIALIZING);
                    return CompletableFuture.runAsync(() -> {
                        try {
                            initModule(gameEngine);
                            transitionTo(ModuleState.RUNNING);
                            logger.info("{} initialized successfully", getName());
                        } catch (Throwable t) {
                            handleFailure(t, "initModule");
                        }
                    }, initExecutor);
                })
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        handleFailure(throwable, "initialize");
                    }

                    if (modulesLoadingCount.decrementAndGet() == 0 && onAllModulesLoadedRunnable != null) {
                        onAllModulesLoadedRunnable.run();
                    }
                });
    }

    private ModuleConfig loadConfig() {
        try {
            logger.debug("{} loading config from '{}'", getName(), configFile);
            return configService.getConfig(configFile);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to load config for " + getName(), t);
        }
    }

    @Override
    public void update(float tpf) {
        if (state != ModuleState.RUNNING) return;
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
        transitionTo(ModuleState.SHUTTING_DOWN);
        try {
            cleanupModule(app);
            transitionTo(ModuleState.CLEANED_UP);
        } catch (Throwable t) {
            handleFailure(t, "cleanup");
        }
    }

    public void reloadConfig() {
        if (configFile == null) return;
        taskScheduler.submit(() -> {
            try {
                ModuleConfig newConfig = configService.reloadConfig(configFile);
                this.config = newConfig;
                onConfigReloaded();
            } catch (Throwable t) {
                handleFailure(t, "reloadConfig");
            }
        });
    }

    private void transitionTo(ModuleState newState) {
        logger.debug("{} transitioning: {} -> {}", getName(), state, newState);
        state = newState;
        ModuleHealthMonitor.getInstance().reportState(getName(), state);
    }

    private void handleFailure(Throwable t, String phase) {
        logger.error("Module {} failure during {}: {}", getName(), phase, t.getMessage(), t);
        transitionTo(ModuleState.PAUSED);
        ModuleHealthMonitor.getInstance().reportError(getName(), t);
    }

    public ModuleConfig getConfig() {
        return config;
    }

    public ModuleState getState() {
        return state;
    }

    public void setOnAllModulesLoadedRunnable(Runnable runnable) {
        this.onAllModulesLoadedRunnable = runnable;
    }

    protected String getName() {
        return getClass().getSimpleName();
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

    protected abstract void onConfigReloaded() throws Exception;
    protected abstract void initModule(CalistaGameEngine app) throws Exception;
    protected abstract void updateModule(float tpf) throws Exception;
    protected abstract void cleanupModule(Application app) throws Exception;
}
