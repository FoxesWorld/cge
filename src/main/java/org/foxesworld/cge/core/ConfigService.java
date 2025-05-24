package org.foxesworld.cge.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * Centralized service for loading, caching, and hot-reloading JSON configurations.
 */
public class ConfigService {
    private static final Path CONFIG_DIR = Paths.get("config");
    private final Gson gson;
    private final ForkJoinPool pool;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> registry = new ConcurrentHashMap<>();

    public ConfigService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        ensureConfigDir();
    }

    private void ensureConfigDir() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config directory", e);
        }
    }

    /**
     * Registers configuration file and its class type.
     */
    public <T> void registerConfig(String fileName, Class<T> clazz) {
        registry.put(fileName, clazz);
    }

    /**
     * Asynchronously load or get cached config.
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String fileName) throws IOException {
        return (T) cache.computeIfAbsent(fileName, key -> {
            try {
                return loadConfig(key);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Reloads config from disk and updates cache.
     */
    @SuppressWarnings("unchecked")
    public <T> T reloadConfig(String fileName) throws IOException {
        T cfg = loadConfig(fileName);
        cache.put(fileName, cfg);
        return cfg;
    }

    private <T> T loadConfig(String fileName) throws IOException {
        Class<T> clazz = (Class<T>) registry.get(fileName);
        if (clazz == null) {
            throw new IllegalArgumentException("Unregistered config: " + fileName);
        }
        Path path = CONFIG_DIR.resolve(fileName);
        if (!Files.exists(path)) {
            // create default
            try {
                T instance = clazz.getDeclaredConstructor().newInstance();
                saveConfigInternal(fileName, instance);
                return instance;
            } catch (Exception e) {
                throw new IOException("Failed to instantiate default config", e);
            }
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return gson.fromJson(reader, clazz);
        }
    }

    /**
     * Saves config object to disk.
     */
    public <T> void saveConfig(String fileName, T config) throws IOException {
        cache.put(fileName, config);
        saveConfigInternal(fileName, config);
    }

    private <T> void saveConfigInternal(String fileName, T config) throws IOException {
        Path path = CONFIG_DIR.resolve(fileName);
        try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            gson.toJson(config, writer);
        }
    }
}


