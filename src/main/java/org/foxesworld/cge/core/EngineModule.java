package org.foxesworld.cge.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for engine modules with optional JSON configuration support.
 * @param <T> configuration type
 */
public abstract class EngineModule<T> extends BaseAppState {
    protected final Logger logger = LogManager.getLogger(getClass());
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    protected T config;
    private final String configFileName;
    private final Class<T> configClass;

    public EngineModule(String configFileName, Class<T> configClass) {
        this.configFileName = configFileName;
        this.configClass = configClass;
        if (configFileName != null && !configFileName.isEmpty()) {
            this.config = loadConfig();
        } else {
            this.config = null; // no config for this module
        }
    }

    /**
     * Loads module configuration from JSON file or creates default.
     */
    private T loadConfig() {
        Path configDir = Paths.get("config");
        Path configPath = configDir.resolve(configFileName);
        try {
            // Ensure config directory exists
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                logger.info("Created configuration directory: {}", configDir);
            }
            // If file does not exist, initialize default and save
            if (!Files.exists(configPath)) {
                logger.warn("Configuration file '{}' not found. Generating default.", configFileName);
                T defaultConfig = createDefaultConfig();
                saveConfig(defaultConfig);
                return defaultConfig;
            }
            // Load existing config
            try (FileReader reader = new FileReader(configPath.toFile())) {
                T loaded = gson.fromJson(reader, (Type) configClass);
                logger.info("Configuration '{}' loaded successfully for module {}.", configFileName, getClass().getSimpleName());
                return loaded;
            }
        } catch (IOException e) {
            logger.error("Error handling configuration file '{}' for {}: {}", configFileName, getClass().getSimpleName(), e.getMessage());
            // Fallback to default config
            T defaultConfig = createDefaultConfig();
            saveConfig(defaultConfig);
            return defaultConfig;
        }
    }

    /**
     * Creates a default configuration instance via reflection.
     */
    private T createDefaultConfig() {
        try {
            T instance = configClass.getDeclaredConstructor().newInstance();
            logger.info("Default configuration instance created for module {}.", getClass().getSimpleName());
            return instance;
        } catch (Exception ex) {
            logger.error("Failed to instantiate default config for {}: {}", getClass().getSimpleName(), ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    /**
     * Saves current configuration to JSON.
     */
    protected void saveConfig(T config) {
        if (configFileName == null || configFileName.isEmpty()) return;
        Path configDir = Paths.get("config");
        Path configPath = configDir.resolve(configFileName);
        try {
            // Ensure directory
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                gson.toJson(config, writer);
                logger.info("Configuration '{}' saved successfully for module {}.", configFileName, getClass().getSimpleName());
            }
        } catch (IOException e) {
            logger.error("Failed to save configuration '{}' for {}: {}", configFileName, getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Reloads configuration from JSON and triggers callback.
     */
    public void reloadConfig() {
        if (configFileName == null || configFileName.isEmpty()) return;
        this.config = loadConfig();
        logger.info("Configuration '{}' reloaded for module {}.", configFileName, getClass().getSimpleName());
        onConfigReloaded();
    }

    /**
     * Called after configuration is reloaded.
     */
    protected abstract void onConfigReloaded();

    /**
     * Initialize module (called once).
     */
    protected abstract void initModule(Application app);

    /**
     * Update module each frame.
     */
    protected abstract void updateModule(float tpf);

    /**
     * Clean up resources on shutdown.
     */
    protected abstract void cleanupModule(Application app);

    @Override
    protected void initialize(Application app) {
        logger.info("Initializing module {}...", getClass().getSimpleName());
        initModule(app);
        logger.info("Module {} initialized.", getClass().getSimpleName());
    }

    @Override
    public void update(float tpf) {
        updateModule(tpf);
    }

    @Override
    protected void cleanup(Application app) {
        logger.info("Cleaning up module {}...", getClass().getSimpleName());
        cleanupModule(app);
        logger.info("Module {} cleaned up.", getClass().getSimpleName());
    }

    public T getConfig() {
        return config;
    }

    public String getConfigFileName() {
        return configFileName;
    }
}
