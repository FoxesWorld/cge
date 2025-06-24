package org.foxesworld.cge.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jme3.math.ColorRGBA;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.utils.json.ColorRGBAAdapter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

/**
 * Provides centralized loading, caching, saving, and optional asynchronous hot-reloading
 * of JSON-based configuration files. Supports auto-creation of default config instances.
 * <p>
 * Configurations must be registered with their corresponding class type prior to access.
 * </p>
 *
 * <p><b>Thread-safe:</b> All cache and registry operations are thread-safe.</p>
 *
 * Usage example:
 * <pre>
 *     configService.registerConfig("graphics.json", GraphicsConfig.class);
 *     GraphicsConfig cfg = configService.getConfig("graphics.json");
 * </pre>
 */
public class ConfigService {

    private static final Path CONFIG_DIR = Paths.get("config");

    private final Gson gson;
    private final ForkJoinPool pool;
    private final CalistaGameEngine calistaGameEngine;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> registry = new ConcurrentHashMap<>();

    /**
     * Constructs a new ConfigService with GSON support for JME's ColorRGBA.
     */
    public ConfigService(CalistaGameEngine calistaGameEngine) {
        this.calistaGameEngine = calistaGameEngine;
        this.gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(ColorRGBA.class, new ColorRGBAAdapter()).create();
        this.pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        ensureConfigDirExists();
    }

    /**
     * Ensures the configuration directory exists. Creates it if missing.
     */
    private void ensureConfigDirExists() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create config directory: " + CONFIG_DIR, e);
        }
    }

    /**
     * Registers a configuration file name with its corresponding configuration class.
     * Must be called before loading or saving the file.
     *
     * @param fileName the file name (e.g., "engine.json")
     * @param clazz    the class representing the configuration structure
     * @param <T>      the type of the config class
     */
    public <T> void registerConfig(String fileName, Class<T> clazz) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(clazz, "clazz must not be null");
        registry.put(fileName, clazz);
    }

    public Set<String> getRegisteredConfigFiles() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /**
     * Retrieves the configuration instance from cache or loads it from disk.
     * If the file doesn't exist, it is created using a new instance of the config class.
     *
     * @param fileName the file name (e.g., "physics.json")
     * @param <T>      the type of the configuration object
     * @return the configuration object
     * @throws IOException if loading fails
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String fileName, boolean exportsConfig) throws IOException {
        return (T) cache.computeIfAbsent(fileName, key -> {
            try {
                return loadConfig(key, exportsConfig);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load config: " + fileName, e);
            }
        });
    }

    /**
     * Reloads the configuration from disk, replacing the cached version.
     *
     * @param fileName the name of the file
     * @param <T>      the config type
     * @return the reloaded configuration object
     * @throws IOException if loading fails
     */
    @SuppressWarnings("unchecked")
    public <T> T reloadConfig(String fileName) throws IOException {
        T cfg = loadConfig(fileName, true);
        cache.put(fileName, cfg);
        return cfg;
    }

    /**
     * Internal method to load a config file or create a default one if not found.
     *
     * @param fileName the config file name
     * @param <T>      the config type
     * @return the loaded or newly created config
     * @throws IOException if deserialization or instantiation fails
     */
    private <T> T loadConfig(String fileName, boolean exportsConfig) throws IOException {
        Class<T> clazz = getRegisteredClass(fileName);
        Path path = CONFIG_DIR.resolve(fileName);

        if (!exportsConfig) {
            return createAndSaveDefault(fileName, clazz, false);
        }

        if (Files.notExists(path)) {
            return createAndSaveDefault(fileName, clazz, true);
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, clazz);
        }
    }


    /**
     * Saves a configuration object to disk and updates the cache.
     *
     * @param fileName the file name to save
     * @param config   the config object
     * @param <T>      the config type
     * @throws IOException if writing fails
     */
    public <T> void saveConfig(String fileName, T config) throws IOException {
        Objects.requireNonNull(config, "Config object cannot be null");
        cache.put(fileName, config);
        saveConfigInternal(fileName, config);
    }

    /**
     * Internal method to save a config to disk.
     *
     * @param fileName the config file name
     * @param config   the config object
     * @param <T>      the config type
     * @throws IOException if writing fails
     */
    private <T> void saveConfigInternal(String fileName, T config) throws IOException {
        Path path = CONFIG_DIR.resolve(fileName);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            gson.toJson(config, writer);
        }
    }

    /**
     * Retrieves the registered config class for a given file.
     *
     * @param fileName the config file name
     * @param <T>      the config type
     * @return the registered class
     * @throws IllegalArgumentException if not registered
     */
    @SuppressWarnings("unchecked")
    private <T> Class<T> getRegisteredClass(String fileName) {
        Class<?> clazz = registry.get(fileName);
        if (clazz == null) {
            throw new IllegalArgumentException("Config not registered: " + fileName);
        }
        return (Class<T>) clazz;
    }

    /**
     * Creates a default instance of the config class and saves it to disk.
     *
     * @param fileName the config file name
     * @param clazz    the config class
     * @param <T>      the config type
     * @return the created default config
     * @throws IOException if instantiation or writing fails
     */
    private <T> T createAndSaveDefault(String fileName, Class<T> clazz, boolean exports) throws IOException {
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            if(exports) {
                saveConfigInternal(fileName, instance);
            }
            return instance;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IOException("Failed to instantiate default config: " + clazz.getName(), e);
        }
    }

       /* @param configFileName The name of the configuration file that was updated.
            */
    public void triggerModuleReload(String configFileName) {
        if (configFileName == null || calistaGameEngine == null) {
            return;
        }
        // Enqueue the task to the JME thread to ensure thread safety
        calistaGameEngine.enqueue(() -> {
            // Assumes CalistaGameEngine has a method to handle this notification
            calistaGameEngine.onConfigReloaded(configFileName);
        });
    }


    /**
     * Asynchronously preloads a configuration in a background thread.
     * Useful for initializing configurations during engine startup.
     *
     * @param fileName the config file name
     * @param <T>      the config type
     * @return Optional of the config or empty if loading failed
     */
    public <T> Optional<Object> preloadConfigAsync(String fileName, boolean exportsConfig) {
        return Optional.ofNullable(pool.submit(() -> {
            try {
                return getConfig(fileName, exportsConfig);
            } catch (IOException e) {
                return null;
            }
        }).join());
    }

    public Map<String, Class<?>> getRegistry() {
        return registry;
    }
}
