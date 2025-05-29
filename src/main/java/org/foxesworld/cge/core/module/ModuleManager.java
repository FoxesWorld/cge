package org.foxesworld.cge.core.module;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.jme3.app.Application;
import com.jme3.app.state.AppStateManager;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Enhanced ModuleManager supporting manual and automatic registration,
 * async init, dependency resolution, and hot-swap.
 */
public class ModuleManager {
    private static final Logger logger = LoggerFactory.getLogger(ModuleManager.class);
    private final AppStateManager stateManager;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;

    // Manual registry
    private final TreeMap<Integer, EngineModule<?>> manual = new TreeMap<>();
    // Auto-loaded instances and descriptors
    private final Map<String, EngineModule<?>> instances = new ConcurrentHashMap<>();

    // ExecutorService with a fixed pool size based on the system
    private final ExecutorService initExecutor;

    private final Path modulesDir = Paths.get("modules");

    public ModuleManager(CalistaGameEngine calistaGameEngine) {
        this.stateManager = calistaGameEngine.getStateManager();
        this.configService = calistaGameEngine.getConfigService();
        this.taskScheduler = calistaGameEngine.getTaskScheduler();
        this.initExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setDaemon(true); // Set threads to daemon for high-load environments
                        return thread;
                    }
                });
    }

    /**
     * Manual registration of a module with explicit priority.
     */
    public synchronized void register(EngineModule<?> module, int priority) {
        manual.put(priority, module);
        logger.info("Adding module {}", module.getClass().getSimpleName());
    }

    /**
     * Synchronous initialization of all manually registered modules.
     */
    public void initializeAll(Application app) {
        manual.values().forEach(m -> {
            stateManager.attach(m);
            logger.info("Attached manual module {}", m.getClass().getSimpleName());
        });
    }

    /**
     * Automatic loading: read module descriptors, resolve dependencies, register,
     * and initialize modules (manual + auto) asynchronously.
     */
    public void loadAll(Application app, Runnable onComplete) {
        List<ModuleDescriptor> descriptors = null;
        try {
            descriptors = readManifests(modulesDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<ModuleDescriptor> sorted = topologicalSort(descriptors);

        // Instantiate and register all auto modules
        for (ModuleDescriptor desc : sorted) {
            try {
                @SuppressWarnings("unchecked")
                Class<EngineModule<?>> clazz = (Class<EngineModule<?>>) Class.forName(desc.className);
                EngineModule<?> module = clazz.getDeclaredConstructor(ConfigService.class, TaskScheduler.class)
                        .newInstance(configService, taskScheduler);
                instances.put(desc.name, module);
                register(module, desc.priority);
                logger.info("Registered auto module {}", desc.name);
            } catch (Exception e) {
                logger.error("Failed to instantiate module {} (class: {}): {}", desc.name, desc.className, e.getMessage(), e);
            }
        }

        // Initialize all modules asynchronously
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (EngineModule<?> module : manual.values()) {
            futures.add(CompletableFuture.runAsync(() -> asyncAttach(module, app), initExecutor));
        }

        // Wait for all initialization tasks to complete and then trigger callback
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    //onComplete.run();  // Run the provided callback
                    onModulesLoaded(onComplete);  // Trigger custom method when all modules are loaded
                })
                .exceptionally(ex -> {
                    logger.error("Error during module initialization", ex);
                    return null;
                });
    }

    /**
     * A new method to set a custom callback that will be invoked when all modules are loaded.
     */
    public void onModulesLoaded(Runnable runnable) {
        CompletableFuture.runAsync(() -> {
            try {
                runnable.run();  // Run the provided callback
                logger.info("Custom onModulesLoaded callback executed.");
            } catch (Exception e) {
                logger.error("Error during onModulesLoaded callback execution", e);
            }
        }, initExecutor);
    }

    private void asyncAttach(EngineModule<?> module, Application app) {
        try {
            stateManager.attach(module);
            logger.info("Attached module {}", module.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("Error attaching module {}: {}", module.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private List<ModuleDescriptor> readManifests(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            logger.warn("Modules directory '{}' not found", dir);
            return Collections.emptyList();
        }
        List<ModuleDescriptor> list = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path path : stream) {
                try {
                    ModuleDescriptor desc = new Gson().fromJson(Files.newBufferedReader(path), ModuleDescriptor.class);
                    list.add(desc);
                } catch (IOException | JsonSyntaxException e) {
                    logger.error("Error reading module descriptor file '{}': {}", path, e.getMessage(), e);
                }
            }
        }
        return list;
    }

    private List<ModuleDescriptor> topologicalSort(List<ModuleDescriptor> descriptors) {
        Map<String, ModuleDescriptor> map = descriptors.stream()
                .collect(Collectors.toMap(d -> d.name, d -> d));
        List<ModuleDescriptor> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (ModuleDescriptor d : descriptors) {
            dfs(d, map, visited, sorted, new HashSet<>());
        }
        sorted.sort(Comparator.comparingInt(d -> d.priority));
        return sorted;
    }

    private void dfs(ModuleDescriptor d,
                     Map<String, ModuleDescriptor> map,
                     Set<String> visited,
                     List<ModuleDescriptor> sorted,
                     Set<String> stack) {
        if (visited.contains(d.name)) return;
        if (!stack.add(d.name)) throw new IllegalStateException("Cyclic dependency: " + d.name);
        for (String dep : d.dependencies) {
            ModuleDescriptor pd = map.get(dep);
            if (pd != null) dfs(pd, map, visited, sorted, stack);
        }
        stack.remove(d.name);
        visited.add(d.name);
        sorted.add(d);
    }

    /**
     * Hot-reload a module by name (if reloadable).
     */
    public void reload(String name) {
        EngineModule<?> module = instances.get(name);
        if (module != null && module.isEnabled()) {
            initExecutor.submit(() -> {
                module.reloadConfig();
                logger.info("Reloaded module {}", name);
            });
        }
    }

    /**
     * Shutdown: detach all and stop executor.
     */
    public void shutdown(Application app) {
        initExecutor.shutdown();
        try {
            if (!initExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                initExecutor.shutdownNow();
                if (!initExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.error("ExecutorService did not terminate in time");
                }
            }
        } catch (InterruptedException e) {
            initExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        manual.values().forEach(stateManager::detach);
        logger.info("ModuleManager shutdown complete");
    }

    /**
     * @return all loaded module instances by name.
     */
    public Map<String, EngineModule<?>> getInstances() {
        return Collections.unmodifiableMap(instances);
    }

    @SuppressWarnings("unchecked")
    public <T extends EngineModule<?>> T getModule(Class<T> moduleClass) {
        logger.info("Searching for module: {}", moduleClass.getSimpleName());
        // Поиск в manual
        for (EngineModule<?> module : manual.values()) {
            if (moduleClass.isInstance(module)) {
                logger.info("Found module in manual: {}", module.getClass().getSimpleName());
                return (T) module;
            }
        }
        // Поиск в instances (автомодулях)
        for (EngineModule<?> module : instances.values()) {
            if (moduleClass.isInstance(module)) {
                logger.info("Found module in instances: {}", module.getClass().getSimpleName());
                return (T) module;
            }
        }
        logger.warn("Module {} not found", moduleClass.getSimpleName());
        return null;
    }
}
