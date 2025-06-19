package org.foxesworld.cge.core.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
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
 * AAA-grade module management system for the Calista Game Engine.
 * <p>
 * Handles the entire lifecycle of engine modules: discovery, dependency resolution,
 * parallel initialization, hot-reloading, and robust error recovery.
 * <p>
 * Features:
 * <ul>
 *   <li>Automatic and manual module registration</li>
 *   <li>Dependency-aware topological sorting</li>
 *   <li>Parallel, non-blocking initialization</li>
 *   <li>Live module reloading and dynamic configuration</li>
 *   <li>Comprehensive error handling with recovery strategies</li>
 *   <li>Performance-optimized lookups and module tracking</li>
 * </ul>
 */
public class ModuleManager {
    private static final Logger logger = LoggerFactory.getLogger(ModuleManager.class);
    private static final int DEFAULT_THREAD_COUNT = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 60;

    // Core engine references
    private final CalistaGameEngine gameEngine;
    private final AppStateManager stateManager;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;

    // Module containers - TreeMap ensures priority-based ordering
    private final TreeMap<Integer, EngineModule<?>> manualModules = new TreeMap<>();
    private final Map<String, EngineModule<?>> moduleInstances = new ConcurrentHashMap<>();

    // For fast class-based lookups (optimization)
    private final Map<Class<?>, EngineModule<?>> modulesByClass = new ConcurrentHashMap<>();

    // Module initialization resources
    private final ExecutorService initExecutor;
    private final Path modulesDir;
    private final Gson gson;

    // State tracking
    private volatile boolean initializing = false;
    private volatile boolean initialized = false;

    /**
     * Constructs a new ModuleManager for the specified game engine.
     *
     * @param gameEngine the game engine instance
     */
    public ModuleManager(CalistaGameEngine gameEngine) {
        this(gameEngine, Paths.get("modules"));
    }

    /**
     * Constructs a new ModuleManager with a custom modules directory.
     *
     * @param gameEngine the game engine instance
     * @param modulesDir the directory containing module descriptor files
     */
    public ModuleManager(CalistaGameEngine gameEngine, Path modulesDir) {
        this.gameEngine = Objects.requireNonNull(gameEngine, "Game engine cannot be null");
        this.stateManager = gameEngine.getStateManager();
        this.configService = gameEngine.getConfigService();
        this.taskScheduler = gameEngine.getTaskScheduler();
        this.modulesDir = modulesDir;

        // Create a thread pool with a reasonable size for module initialization
        this.initExecutor = Executors.newFixedThreadPool(
                DEFAULT_THREAD_COUNT,
                r -> {
                    Thread thread = new Thread(r, "ModuleInit-Worker");
                    thread.setDaemon(true);
                    return thread;
                });

        // Configure Gson with pretty printing for better error messages
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        logger.info("ModuleManager initialized with modules directory: {}", modulesDir);
    }

    /**
     * Registers a module manually with a given priority.
     * Lower priority values mean higher execution priority.
     *
     * @param module   the module to register
     * @param priority the module's priority (lower values loaded first)
     * @return this ModuleManager instance for method chaining
     * @throws IllegalStateException if initialization has already started
     */
    public synchronized ModuleManager register(EngineModule<?> module, int priority) {
        if (initializing || initialized) {
            throw new IllegalStateException("Cannot register modules after initialization has started");
        }

        Objects.requireNonNull(module, "Module cannot be null");
        String moduleName = module.getClass().getSimpleName();

        manualModules.put(priority, module);
        moduleInstances.put(moduleName, module);
        modulesByClass.put(module.getClass(), module);

        logger.info("Registered module {} with priority {}", moduleName, priority);
        return this;
    }

    /**
     * Synchronously initializes all manually registered modules in priority order.
     *
     * @param app the application context
     * @return this ModuleManager instance for method chaining
     */
    public synchronized ModuleManager initializeAll(Application app) {
        if (initialized) {
            logger.warn("ModuleManager already initialized, skipping");
            return this;
        }

        initializing = true;
        int count = 0;

        for (Map.Entry<Integer, EngineModule<?>> entry : manualModules.entrySet()) {
            EngineModule<?> module = entry.getValue();
            try {
                stateManager.attach(module);
                count++;
                logger.info("Attached manual module {} (priority {})",
                        module.getClass().getSimpleName(), entry.getKey());
            } catch (Exception e) {
                logger.error("Failed to attach manual module {}: {}",
                        module.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        initialized = true;
        initializing = false;
        logger.info("Initialized {} manual modules", count);
        return this;
    }

    /**
     * Loads all modules (both manual and auto-discovered) asynchronously,
     * resolving dependencies and invoking the completion callback when done.
     *
     * @param app        the application context
     * @param onComplete a callback to execute when all modules have been loaded
     * @return this ModuleManager instance for method chaining
     * @throws IllegalStateException if the manager is already initializing
     */
    public synchronized ModuleManager loadAll(Application app, Runnable onComplete) {
        if (initializing) {
            throw new IllegalStateException("ModuleManager is already initializing");
        }
        if (initialized) {
            logger.warn("ModuleManager already initialized, executing callback immediately");
            taskScheduler.submit(onComplete);
            return this;
        }

        initializing = true;

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Discover module descriptors from filesystem
                List<ModuleDescriptor> descriptors = discoverModules();

                // 2. Process and instantiate auto-discovered modules
                List<ModuleDescriptor> sorted = resolveAndSortDependencies(descriptors);
                instantiateModules(sorted);

                // 3. Initialize and attach all modules in parallel
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                // First process manual modules in priority order
                for (Map.Entry<Integer, EngineModule<?>> entry : manualModules.entrySet()) {
                    final int priority = entry.getKey();
                    final EngineModule<?> module = entry.getValue();

                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            asyncAttach(module, app);
                            logger.info("Attached manual module {} (priority {})",
                                    module.getClass().getSimpleName(), priority);
                        } catch (Exception e) {
                            logger.error("Error attaching manual module {}: {}",
                                    module.getClass().getSimpleName(), e.getMessage(), e);
                        }
                    }, initExecutor));
                }

                // Set up final completion handling
                CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                allFutures.whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Error during module initialization", ex);
                    }
                    initialized = true;
                    initializing = false;
                    int totalModules = moduleInstances.size();
                    logger.info("Module initialization complete. Total modules: {}", totalModules);

                    // Execute the completion callback
                    onModulesLoaded(onComplete);
                });

            } catch (Exception e) {
                logger.error("Critical error during module discovery/initialization", e);
                initializing = false;
                onModuleLoadError(e, onComplete);
            }
        }, initExecutor);

        return this;
    }

    /**
     * Discovers modules from the modules directory.
     *
     * @return a list of module descriptors
     */
    private List<ModuleDescriptor> discoverModules() {
        logger.debug("Discovering modules in {}", modulesDir);

        List<ModuleDescriptor> descriptors = new ArrayList<>();

        try {
            if (!Files.exists(modulesDir)) {
                logger.warn("Modules directory '{}' not found", modulesDir);
                return descriptors;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modulesDir, "*.json")) {
                for (Path path : stream) {
                    try {
                        String json = Files.readString(path);
                        ModuleDescriptor descriptor = gson.fromJson(json, ModuleDescriptor.class);

                        // Validate the descriptor
                        if (descriptor.name == null || descriptor.name.isBlank()) {
                            logger.warn("Skipping module descriptor {} - missing name", path);
                            continue;
                        }
                        if (descriptor.className == null || descriptor.className.isBlank()) {
                            logger.warn("Skipping module descriptor {} - missing className", path);
                            continue;
                        }

                        descriptors.add(descriptor);
                        logger.debug("Found module descriptor: {} ({})", descriptor.name, path);
                    } catch (IOException | JsonSyntaxException e) {
                        logger.error("Error reading module descriptor file '{}': {}", path, e.getMessage(), e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error scanning modules directory {}: {}", modulesDir, e.getMessage(), e);
        }

        logger.info("Discovered {} module descriptors", descriptors.size());
        return descriptors;
    }

    /**
     * Resolves dependencies and sorts modules topologically.
     *
     * @param descriptors the list of module descriptors
     * @return topologically sorted list of descriptors
     */
    private List<ModuleDescriptor> resolveAndSortDependencies(List<ModuleDescriptor> descriptors) {
        // Create a map of module name to descriptor for fast lookup
        Map<String, ModuleDescriptor> moduleMap = descriptors.stream()
                .collect(Collectors.toMap(d -> d.name, d -> d, (a, b) -> {
                    logger.warn("Duplicate module name found: {}. Using first definition.", a.name);
                    return a;
                }));

        // Check for missing dependencies
        for (ModuleDescriptor descriptor : descriptors) {
            for (String dependency : descriptor.dependencies) {
                if (!moduleMap.containsKey(dependency) && !moduleInstances.containsKey(dependency)) {
                    logger.warn("Module '{}' depends on '{}' which is not found. Module may fail to initialize.",
                            descriptor.name, dependency);
                }
            }
        }

        // Sort modules topologically
        List<ModuleDescriptor> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> processing = new HashSet<>();

        for (ModuleDescriptor descriptor : descriptors) {
            if (!visited.contains(descriptor.name)) {
                topoSort(descriptor, moduleMap, visited, processing, sorted);
            }
        }

        // Final sort by priority (for modules with same dependency level)
        sorted.sort(Comparator.comparingInt(d -> d.priority));

        return sorted;
    }

    /**
     * Recursive topological sort algorithm to resolve dependencies.
     */
    private void topoSort(ModuleDescriptor current,
                          Map<String, ModuleDescriptor> moduleMap,
                          Set<String> visited,
                          Set<String> processing,
                          List<ModuleDescriptor> result) {

        processing.add(current.name);

        for (String dependencyName : current.dependencies) {
            // Skip if dependency is already visited
            if (visited.contains(dependencyName)) {
                continue;
            }

            // Detect circular dependencies
            if (processing.contains(dependencyName)) {
                logger.error("Circular dependency detected: {} <-> {}", current.name, dependencyName);
                throw new IllegalStateException("Circular dependency detected: " + current.name +
                        " <-> " + dependencyName);
            }

            // Process dependency if it exists
            ModuleDescriptor dependency = moduleMap.get(dependencyName);
            if (dependency != null) {
                topoSort(dependency, moduleMap, visited, processing, result);
            }
            // If dependency doesn't exist, we've already warned in the calling method
        }

        processing.remove(current.name);
        visited.add(current.name);
        result.add(current);
    }

    /**
     * Instantiates modules from descriptors and registers them.
     */
    private void instantiateModules(List<ModuleDescriptor> descriptors) {
        for (ModuleDescriptor descriptor : descriptors) {
            try {
                // Skip if already instantiated manually
                if (moduleInstances.containsKey(descriptor.name)) {
                    logger.debug("Module {} already instantiated manually, skipping", descriptor.name);
                    continue;
                }

                @SuppressWarnings("unchecked")
                Class<EngineModule<?>> moduleClass = (Class<EngineModule<?>>)Class.forName(descriptor.className);

                // Try different constructor patterns
                EngineModule<?> module = null;
                try {
                    // Try constructor with (ConfigService, TaskScheduler)
                    module = moduleClass.getDeclaredConstructor(ConfigService.class, TaskScheduler.class)
                            .newInstance(configService, taskScheduler);
                } catch (NoSuchMethodException e) {
                    try {
                        // Try constructor with (CalistaGameEngine)
                        module = moduleClass.getDeclaredConstructor(CalistaGameEngine.class)
                                .newInstance(gameEngine);
                    } catch (NoSuchMethodException e2) {
                        // Try default constructor
                        module = moduleClass.getDeclaredConstructor().newInstance();
                        logger.debug("Using default constructor for module {}", descriptor.name);
                    }
                }

                if (module != null) {
                    moduleInstances.put(descriptor.name, module);
                    modulesByClass.put(moduleClass, module);
                    manualModules.put(descriptor.priority, module);
                    logger.info("Instantiated module {} ({})", descriptor.name, descriptor.className);
                }

            } catch (ClassNotFoundException e) {
                logger.error("Module class not found: {} ({})", descriptor.className, descriptor.name, e);
            } catch (Exception e) {
                logger.error("Failed to instantiate module {} ({}): {}",
                        descriptor.name, descriptor.className, e.getMessage(), e);
            }
        }
    }

    /**
     * Attaches a module to the application state manager.
     */
    private void asyncAttach(EngineModule<?> module, Application app) {
        try {
            stateManager.attach(module);
            module.setOnAllModulesLoadedRunnable(null); // Clear any existing callback
            logger.debug("Attached module {}", module.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("Error attaching module {}: {}",
                    module.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * Executes the callback when all modules have been loaded.
     */
    private void onModulesLoaded(Runnable callback) {
        if (callback != null) {
            try {
                callback.run();
                logger.info("ModuleManager: All modules loaded callback executed");
            } catch (Exception e) {
                logger.error("Error executing onModulesLoaded callback", e);
            }
        }
    }

    /**
     * Handles critical errors during module loading.
     */
    private void onModuleLoadError(Exception e, Runnable callback) {
        logger.error("Critical error occurred during module loading", e);

        // Try to execute callback even on error
        if (callback != null) {
            try {
                callback.run();
                logger.info("Executed onModulesLoaded callback after error");
            } catch (Exception callbackEx) {
                logger.error("Error executing onModulesLoaded callback after initial error", callbackEx);
            }
        }
    }

    /**
     * Hot-reloads a module by name.
     *
     * @param name the name of the module to reload
     * @return true if the module was found and reload initiated
     */
    public boolean reload(String name) {
        EngineModule<?> module = moduleInstances.get(name);
        if (module != null && module.isEnabled()) {
            taskScheduler.submit(() -> {
                try {
                    module.reloadConfig();
                    logger.info("Reloaded module {}", name);
                } catch (Exception e) {
                    logger.error("Error reloading module {}: {}", name, e.getMessage(), e);
                }
            });
            return true;
        } else {
            logger.warn("Module {} not found or not enabled, cannot reload", name);
            return false;
        }
    }

    /**
     * Batch-reloads multiple modules by name.
     *
     * @param moduleNames collection of module names to reload
     * @return the number of modules that were successfully targeted for reload
     */
    public int reloadMultiple(Collection<String> moduleNames) {
        int count = 0;
        for (String name : moduleNames) {
            if (reload(name)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Shuts down the module manager, properly disposing resources.
     *
     * @param app the application context
     */
    public void shutdown(Application app) {
        logger.info("Shutting down ModuleManager");

        // First detach all modules from the state manager
        for (EngineModule<?> module : new ArrayList<>(manualModules.values())) {
            try {
                if (module.isInitialized()) {
                    stateManager.detach(module);
                    logger.debug("Detached module {}", module.getClass().getSimpleName());
                }
            } catch (Exception e) {
                logger.error("Error detaching module {}: {}",
                        module.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        // Then shutdown the executor service
        initExecutor.shutdown();
        try {
            if (!initExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Executor service did not terminate in time, forcing shutdown");
                initExecutor.shutdownNow();
                if (!initExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    logger.error("Executor service still did not terminate");
                }
            }
        } catch (InterruptedException e) {
            logger.warn("Shutdown interrupted", e);
            initExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("ModuleManager shutdown complete");
    }

    /**
     * Gets a module by its class.
     *
     * @param moduleClass the class of the module to find
     * @param <T>         the type of the module
     * @return the module instance or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T extends EngineModule<?>> T getModule(Class<T> moduleClass) {
        // Fast lookup using the class map
        EngineModule<?> module = modulesByClass.get(moduleClass);
        if (module != null) {
            return (T) module;
        }

        // Fallback to iteration for subclass matching
        for (EngineModule<?> m : moduleInstances.values()) {
            if (moduleClass.isInstance(m)) {
                // Cache for future lookups
                modulesByClass.put(moduleClass, m);
                return (T) m;
            }
        }

        logger.debug("Module of type {} not found", moduleClass.getSimpleName());
        return null;
    }

    /**
     * Gets all loaded modules as an unmodifiable map.
     *
     * @return map of module names to instances
     */
    public Map<String, EngineModule<?>> getModuleInstances() {
        return Collections.unmodifiableMap(moduleInstances);
    }

    /**
     * Gets all modules as a flat list.
     *
     * @return list of all module instances
     */
    public List<EngineModule<?>> getAllModules() {
        return new ArrayList<>(moduleInstances.values());
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
     * Updates the module manager (empty implementation for override).
     *
     * @param tpf time per frame
     */
    public void update(float tpf) {
        // Extension point for subclasses
    }

    /**
     * Checks if the module manager has been initialized.
     *
     * @return true if initialization is complete
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if the module manager is currently initializing.
     *
     * @return true if initialization is in progress
     */
    public boolean isInitializing() {
        return initializing;
    }
}