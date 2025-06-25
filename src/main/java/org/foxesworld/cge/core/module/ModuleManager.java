package org.foxesworld.cge.core.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.jme3.app.Application;
import com.jme3.app.state.AppStateManager;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.ConfigService;
import org.foxesworld.cge.core.TaskScheduler;
import org.foxesworld.cge.core.module.health.ModuleHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AAA-grade module management system for the Calista Game Engine.
 * Handles the entire lifecycle of engine modules: discovery, dependency resolution,
 * parallel initialization, hot-reloading, and robust error recovery.
 */
public class ModuleManager {
    private static final Logger logger = LoggerFactory.getLogger(ModuleManager.class);
    private static final int DEFAULT_THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 60;
    private static final int MODULE_INIT_TIMEOUT_SECONDS = 30;

    private final CalistaGameEngine gameEngine;
    private final AppStateManager stateManager;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;

    // Thread-safe containers
    private final ConcurrentSkipListMap<Integer, EngineModule<?>> manualModules = new ConcurrentSkipListMap<>();
    private final ConcurrentHashMap<String, EngineModule<?>> moduleInstances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, EngineModule<?>> modulesByClass = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ModuleHealth> moduleHealth = new ConcurrentHashMap<>();

    private final ExecutorService initExecutor;
    private final Path modulesDir;
    private final Gson gson;

    private final AtomicBoolean initializing = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    // For onModuleLoaded (event after module init/attach)
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Runnable>> moduleLoadListeners = new ConcurrentHashMap<>();

    public ModuleManager(CalistaGameEngine gameEngine) {
        this(gameEngine, Paths.get("modules"));
    }

    public ModuleManager(CalistaGameEngine gameEngine, Path modulesDir) {
        this.gameEngine = Objects.requireNonNull(gameEngine, "Game engine cannot be null");
        this.stateManager = Objects.requireNonNull(gameEngine.getStateManager(), "State manager cannot be null");
        this.configService = Objects.requireNonNull(gameEngine.getConfigService(), "Config service cannot be null");
        this.taskScheduler = Objects.requireNonNull(gameEngine.getTaskScheduler(), "Task scheduler cannot be null");
        this.modulesDir = Objects.requireNonNull(modulesDir, "Modules directory cannot be null");

        this.initExecutor = Executors.newFixedThreadPool(
                DEFAULT_THREAD_COUNT,
                r -> {
                    Thread thread = new Thread(r, "ModuleInit-Worker");
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((t, e) ->
                            logger.error("Uncaught exception in module initialization thread: {}", t.getName(), e));
                    return thread;
                });

        this.gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

        logger.info("ModuleManager initialized with modules directory: {}", modulesDir);
        try {
            if (!Files.exists(modulesDir)) {
                Files.createDirectories(modulesDir);
                logger.info("Created modules directory: {}", modulesDir);
            }
        } catch (IOException e) {
            logger.warn("Failed to create modules directory {}: {}", modulesDir, e.getMessage());
        }
    }

    /**
     * Registers a module manually with a given priority and IMMEDIATELY initializes and attaches it.
     * Lower priority values mean higher execution priority.
     */
    public synchronized ModuleManager register(EngineModule<?> module, int priority) {
        if (initializing.get() || initialized.get()) {
            throw new IllegalStateException("Cannot register modules after initialization has started");
        }
        if (shuttingDown.get()) {
            throw new IllegalStateException("Cannot register modules during shutdown");
        }

        Objects.requireNonNull(module, "Module cannot be null");
        String moduleName = module.getClass().getSimpleName();

        // Replace if already registered
        if (moduleInstances.containsKey(moduleName)) {
            logger.warn("Module {} already registered, replacing previous instance", moduleName);
        }

        manualModules.put(priority, module);
        moduleInstances.put(moduleName, module);
        modulesByClass.put(module.getClass(), module);
        moduleHealth.put(moduleName, new ModuleHealth(moduleName));

        logger.info("Registered module {} with priority {}", moduleName, priority);

        // Immediately initialize and attach
        try {
            logger.debug("Immediately initializing/attaching module {} after registration", moduleName);
            if (!module.isInitialized()) module.initialize(stateManager, gameEngine);
            if (!module.isAttached()) stateManager.attach(module);
            moduleHealth.get(moduleName).setStatus(ModuleState.RUNNING);
            logger.info("Module {} initialized and attached after registration", moduleName);
            notifyModuleLoaded(module.getClass());
        } catch (Exception e) {
            moduleHealth.get(moduleName).setStatus(ModuleState.FAILED, e.getMessage());
            logger.error("Failed to immediately initialize/attach module {}: {}", moduleName, e.getMessage(), e);
            tryRecoverModule(module, gameEngine);
        }
        return this;
    }

    /**
     * Initializes and attaches a single module by its class, if not already attached.
     * Returns true if initialization and attachment succeeded.
     */
    public synchronized <T extends EngineModule<?>> boolean initModule(Class<T> moduleClass) {
        Objects.requireNonNull(moduleClass, "Module class cannot be null");
        T module = getModule(moduleClass);
        if (module == null) {
            logger.warn("Module {} not registered, cannot initialize", moduleClass.getSimpleName());
            return false;
        }
        String moduleName = module.getClass().getSimpleName();
        Application app = gameEngine;
        try {
            logger.debug("Initializing module {} via initModule", moduleName);
            if (!module.isInitialized()) module.initialize(stateManager, app);
            if (!module.isAttached()) stateManager.attach(module);
            moduleHealth.get(moduleName).setStatus(ModuleState.RUNNING);
            logger.info("Module {} initialized and attached", moduleName);
            notifyModuleLoaded(moduleClass);
            return true;
        } catch (Exception e) {
            moduleHealth.get(moduleName).setStatus(ModuleState.FAILED, e.getMessage());
            logger.error("Failed to initialize/attach module {}: {}", moduleName, e.getMessage(), e);
            tryRecoverModule(module, app);
            return false;
        }
    }

    public boolean shutdownModule(Class<? extends EngineModule<?>> moduleClass) {
        EngineModule<?> module = getModule(moduleClass);
        if (module != null) {
            module.setEnabled(false);
            stateManager.detach(module);
            return true;
        }
        return false;
    }

    /**
     * Registers a callback to be executed once a module of the given class is loaded.
     * If the module is already loaded and initialized, the callback is executed immediately.
     * Returns the loaded module instance if present, otherwise null.
     */
    public <T extends EngineModule<?>> T onModuleLoaded(Class<T> moduleClass, Runnable run) {
        Objects.requireNonNull(moduleClass, "Module class cannot be null");
        Objects.requireNonNull(run, "Callback runnable cannot be null");
        T loaded = getModule(moduleClass);
        if (loaded != null && loaded.isInitialized()) {
            run.run();
            return loaded;
        }
        moduleLoadListeners.computeIfAbsent(moduleClass, c -> new CopyOnWriteArrayList<>()).add(run);
        return loaded;
    }

    /**
     * Internal: Call to notify all listeners that a module of the given class has been loaded.
     */
    private void notifyModuleLoaded(Class<?> moduleClass) {
        List<Runnable> listeners = moduleLoadListeners.remove(moduleClass);
        if (listeners != null) listeners.forEach(r -> {
            try { r.run(); }
            catch (Exception e) { logger.error("onModuleLoaded callback error", e); }
        });
    }

    public EngineModule<?> getModuleByConfigFile(String configFileName) {
        if (configFileName == null || configFileName.isBlank()) return null;
        for (EngineModule<?> module : moduleInstances.values()) {
            if (configFileName.equalsIgnoreCase(module.getName())) return module;
        }
        logger.warn("No module found for config file: {}", configFileName);
        return null;
    }

    @Deprecated
    public synchronized ModuleManager initializeAll(Application app) {
        Objects.requireNonNull(app, "Application cannot be null");
        if (initialized.get()) {
            logger.warn("ModuleManager already initialized, skipping");
            return this;
        }
        if (!initializing.compareAndSet(false, true)) {
            logger.warn("ModuleManager initialization already in progress, skipping");
            return this;
        }
        int successCount = 0, failCount = 0;
        for (Map.Entry<Integer, EngineModule<?>> entry : manualModules.entrySet()) {
            EngineModule<?> module = entry.getValue();
            String moduleName = module.getClass().getSimpleName();
            try {
                logger.debug("Initializing module {} (priority {})", moduleName, entry.getKey());
                if (!module.isInitialized()) module.initialize(stateManager, app);
                if (!module.isAttached()) stateManager.attach(module);
                successCount++;
                moduleHealth.get(moduleName).setStatus(ModuleState.RUNNING);
                logger.info("Attached manual module {} (priority {})", moduleName, entry.getKey());
                notifyModuleLoaded(module.getClass());
            } catch (Exception e) {
                failCount++;
                moduleHealth.get(moduleName).setStatus(ModuleState.FAILED, e.getMessage());
                logger.error("Failed to attach manual module {}: {}", moduleName, e.getMessage(), e);
                tryRecoverModule(module, app);
            }
        }
        initialized.set(true);
        initializing.set(false);
        logger.info("Initialized {} manual modules successfully, {} failed", successCount, failCount);
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
     * @throws NullPointerException if app is null
     */
    public synchronized ModuleManager loadAll(Application app, Runnable onComplete) {
        Objects.requireNonNull(app, "Application cannot be null");

        if (!initializing.compareAndSet(false, true)) {
            throw new IllegalStateException("ModuleManager is already initializing");
        }

        if (initialized.get()) {
            logger.warn("ModuleManager already initialized, executing callback immediately");
            safeExecute(onComplete);
            return this;
        }

        if (shuttingDown.get()) {
            throw new IllegalStateException("Cannot load modules during shutdown");
        }

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Discover module descriptors from filesystem
                List<ModuleDescriptor> descriptors = discoverModules();

                // 2. Process and instantiate auto-discovered modules
                List<ModuleDescriptor> sorted = resolveAndSortDependencies(descriptors);
                instantiateModules(sorted);

                // 3. Initialize and attach all modules in parallel
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                // Process manual modules in priority order
                for (Map.Entry<Integer, EngineModule<?>> entry : manualModules.entrySet()) {
                    final int priority = entry.getKey();
                    final EngineModule<?> module = entry.getValue();
                    final String moduleName = module.getClass().getSimpleName();

                    // Add module health entry if not already present
                    moduleHealth.putIfAbsent(moduleName, new ModuleHealth(moduleName));

                    // Set health status to initializing
                    moduleHealth.get(moduleName).setStatus(ModuleState.INITIALIZING);

                    futures.add(CompletableFuture.runAsync(() -> {
                                try {
                                    asyncAttach(module, app);
                                    moduleHealth.get(moduleName).setStatus(ModuleState.RUNNING);

                                    logger.info("Attached manual module {} (priority {})", moduleName, priority);
                                } catch (Exception e) {
                                    moduleHealth.get(moduleName).setStatus(ModuleState.FAILED, e.getMessage());

                                    logger.error("Error attaching manual module {}: {}",
                                            moduleName, e.getMessage(), e);

                                    // Attempt recovery
                                    tryRecoverModule(module, app);
                                }
                            }, initExecutor).orTimeout(MODULE_INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .exceptionally(ex -> {
                                if (ex instanceof TimeoutException) {
                                    moduleHealth.get(moduleName).setStatus(
                                            ModuleState.PAUSED, "Initialization timed out");

                                    logger.error("Module {} initialization timed out", moduleName);
                                }
                                return null;
                            }));
                }

                // Set up final completion handling
                CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                allFutures.whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Error during module initialization", ex);
                    }

                    // Even with errors, we consider initialization complete
                    initialized.set(true);
                    initializing.set(false);

                    int totalModules = moduleInstances.size();
                    int activeModules = (int) moduleHealth.values().stream()
                            .filter(h -> h.getStatus() == ModuleState.RUNNING)
                            .count();

                    logger.info("Module initialization complete. Total modules: {}, Active: {}, Failed: {}",
                            totalModules, activeModules, (totalModules - activeModules));

                    // Execute the completion callback
                    onModulesLoaded(onComplete);

                    // Schedule health check
                    scheduleHealthCheck();
                });

            } catch (Exception e) {
                logger.error("Critical error during module discovery/initialization", e);
                initializing.set(false);
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
                Files.createDirectories(modulesDir);
                logger.info("Created modules directory: {}", modulesDir);
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modulesDir, "*.json")) {
                for (Path path : stream) {
                    try {
                        String json = Files.readString(path);
                        ModuleDescriptor descriptor = gson.fromJson(json, ModuleDescriptor.class);

                        // Validate the descriptor
                        if (descriptor == null) {
                            logger.warn("Invalid module descriptor at {}: null after parsing", path);
                            continue;
                        }

                        if (descriptor.name == null || descriptor.name.isBlank()) {
                            logger.warn("Skipping module descriptor {} - missing name", path);
                            continue;
                        }

                        if (descriptor.className == null || descriptor.className.isBlank()) {
                            logger.warn("Skipping module descriptor {} - missing className", path);
                            continue;
                        }

                        // Normalize values
                        descriptor.dependencies = descriptor.dependencies != null ?
                                descriptor.dependencies : List.of(new String[0]);

                        descriptors.add(descriptor);
                        logger.debug("Found module descriptor: {} ({})", descriptor.name, path);
                    } catch (IOException e) {
                        logger.error("Error reading module descriptor file '{}': {}", path, e.getMessage());
                    } catch (JsonSyntaxException e) {
                        logger.error("Invalid JSON in module descriptor '{}': {}", path, e.getMessage());
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
     * @throws IllegalStateException if circular dependencies are detected
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
            if (descriptor.dependencies != null) {
                for (String dependency : descriptor.dependencies) {
                    if (!moduleMap.containsKey(dependency) && !moduleInstances.containsKey(dependency)) {
                        logger.warn("Module '{}' depends on '{}' which is not found. Module may fail to initialize.",
                                descriptor.name, dependency);
                    }
                }
            }
        }

        // Sort modules topologically
        List<ModuleDescriptor> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> processing = new HashSet<>();

        for (ModuleDescriptor descriptor : descriptors) {
            if (!visited.contains(descriptor.name)) {
                try {
                    topoSort(descriptor, moduleMap, visited, processing, sorted);
                } catch (IllegalStateException e) {
                    logger.error("Dependency resolution error for module {}: {}", descriptor.name, e.getMessage());
                    // Continue with other modules
                }
            }
        }

        // Final sort by priority (for modules with same dependency level)
        sorted.sort(Comparator.comparingInt(d -> d.priority));

        return sorted;
    }

    /**
     * Recursive topological sort algorithm to resolve dependencies.
     *
     * @param current the current module descriptor being processed
     * @param moduleMap map of all module descriptors by name
     * @param visited set of already visited modules
     * @param processing set of modules currently being processed (for cycle detection)
     * @param result sorted list of module descriptors
     * @throws IllegalStateException if circular dependencies are detected
     */
    private void topoSort(ModuleDescriptor current,
                          Map<String, ModuleDescriptor> moduleMap,
                          Set<String> visited,
                          Set<String> processing,
                          List<ModuleDescriptor> result) {

        processing.add(current.name);

        if (current.dependencies != null) {
            for (String dependencyName : current.dependencies) {
                // Skip if dependency is already visited
                if (visited.contains(dependencyName)) {
                    continue;
                }

                // Detect circular dependencies
                if (processing.contains(dependencyName)) {
                    String cycle = String.join(" -> ", processing) + " -> " + dependencyName;
                    logger.error("Circular dependency detected: {}", cycle);
                    throw new IllegalStateException("Circular dependency detected: " + cycle);
                }

                // Process dependency if it exists
                ModuleDescriptor dependency = moduleMap.get(dependencyName);
                if (dependency != null) {
                    topoSort(dependency, moduleMap, visited, processing, result);
                }
                // If dependency doesn't exist, we've already warned in the calling method
            }
        }

        processing.remove(current.name);
        visited.add(current.name);
        result.add(current);
    }

    /**
     * Instantiates modules from descriptors and registers them.
     *
     * @param descriptors list of sorted module descriptors to instantiate
     */
    private void instantiateModules(List<ModuleDescriptor> descriptors) {
        for (ModuleDescriptor descriptor : descriptors) {
            try {
                // Skip if already instantiated manually
                if (moduleInstances.containsKey(descriptor.name)) {
                    logger.debug("Module {} already instantiated manually, skipping", descriptor.name);
                    continue;
                }

                // Add health tracking for this module
                moduleHealth.put(descriptor.name, new ModuleHealth(descriptor.name));

                @SuppressWarnings("unchecked")
                Class<? extends EngineModule<?>> moduleClass;
                try {
                    moduleClass = (Class<? extends EngineModule<?>>) Class.forName(descriptor.className);
                } catch (ClassCastException e) {
                    logger.error("Class {} is not a subclass of EngineModule", descriptor.className);
                    moduleHealth.get(descriptor.name).setStatus(ModuleState.FAILED,
                            "Class is not a valid EngineModule");
                    continue;
                }

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
                        try {
                            // Try default constructor
                            module = moduleClass.getDeclaredConstructor().newInstance();
                            logger.debug("Using default constructor for module {}", descriptor.name);
                        } catch (NoSuchMethodException e3) {
                            throw new InstantiationException("No suitable constructor found for " + descriptor.name);
                        }
                    }
                }

                if (module != null) {
                    moduleInstances.put(descriptor.name, module);
                    modulesByClass.put(moduleClass, module);
                    manualModules.put(descriptor.priority, module);
                    moduleHealth.get(descriptor.name).setStatus(ModuleState.INITIALIZING);
                    logger.info("Instantiated module {} ({})", descriptor.name, descriptor.className);
                }

            } catch (ClassNotFoundException e) {
                logger.error("Module class not found: {} ({})", descriptor.className, descriptor.name);
                moduleHealth.get(descriptor.name).setStatus(ModuleState.FAILED, "Class not found");
            } catch (Exception e) {
                logger.error("Failed to instantiate module {} ({}): {}",
                        descriptor.name, descriptor.className, e.getMessage(), e);
                moduleHealth.get(descriptor.name).setStatus(ModuleState.FAILED,
                        "Instantiation failed: " + e.getMessage());
            }
        }
    }

    /**
     * Attaches a module to the application state manager.
     *
     * @param module the module to attach
     * @param app the application context
     * @throws Exception if an error occurs during attachment
     */
    private void asyncAttach(EngineModule<?> module, Application app) throws Exception {
        String moduleName = module.getClass().getSimpleName();
        try {
            if (!module.isInitialized()) {
                module.initialize(stateManager, app);
            }
            stateManager.attach(module);
            module.setOnAllModulesLoadedRunnable(null); // Clear any existing callback
            logger.debug("Attached module {}", moduleName);
        } catch (Exception e) {
            logger.error("Error attaching module {}: {}",
                    moduleName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Attempts to recover a failed module.
     *
     * @param module the failed module
     * @param app the application context
     */
    private void tryRecoverModule(EngineModule<?> module, Application app) {
        String moduleName = module.getClass().getSimpleName();

        if (module.isRecoverable()) {
            try {
                logger.info("Attempting to recover module {}", moduleName);

                // Update health status
                moduleHealth.get(moduleName).setStatus(ModuleState.RECOVERING);

                // Try recovery
                module.recover();

                // If successful, try to attach again
                if (!module.isEnabled() && !module.isAttached()) {
                    stateManager.attach(module);
                }

                // Update health status
                moduleHealth.get(moduleName).setStatus(ModuleState.RUNNING, "Recovered from failure");

                logger.info("Successfully recovered module {}", moduleName);
            } catch (Exception e) {
                logger.error("Failed to recover module {}: {}", moduleName, e.getMessage(), e);
                moduleHealth.get(moduleName).setStatus(ModuleState.FAILED,
                        "Recovery failed: " + e.getMessage());
            }
        } else {
            logger.info("Module {} is not recoverable, skipping recovery attempt", moduleName);
        }
    }

    /**
     * Executes the callback when all modules have been loaded.
     *
     * @param callback the callback to execute
     */
    private void onModulesLoaded(Runnable callback) {
        safeExecute(callback);
    }

    /**
     * Safely executes a runnable, catching and logging any exceptions.
     *
     * @param runnable the runnable to execute
     */
    private void safeExecute(Runnable runnable) {
        if (runnable != null) {
            try {
                runnable.run();
                logger.debug("Callback executed successfully");
            } catch (Exception e) {
                logger.error("Error executing callback", e);
            }
        }
    }

    /**
     * Handles critical errors during module loading.
     *
     * @param e the exception that occurred
     * @param callback the callback to execute
     */
    private void onModuleLoadError(Exception e, Runnable callback) {
        logger.error("Critical error occurred during module loading", e);

        // Try to execute callback even on error
        safeExecute(callback);
    }

    /**
     * Schedules periodic health checks for modules.
     */
    private void scheduleHealthCheck() {
        if (initialized.get() && !shuttingDown.get()) {
            taskScheduler.scheduleAtFixedRate(() -> {
                if (!shuttingDown.get()) {
                    checkModulesHealth();
                }
            }, 30, 30, TimeUnit.SECONDS);
        }
    }

    /**
     * Checks the health of all modules and attempts recovery if needed.
     */
    private void checkModulesHealth() {
        //logger.debug("Performing module health check");

        for (Map.Entry<String, EngineModule<?>> entry : moduleInstances.entrySet()) {
            String name = entry.getKey();
            EngineModule<?> module = entry.getValue();

            if (!module.isEnabled() && moduleHealth.get(name).getStatus() == ModuleState.RUNNING) {
                logger.warn("Module {} is marked active but is disabled", name);
                moduleHealth.get(name).setStatus(ModuleState.UNLOADED, "Module disabled unexpectedly");

                // Try to recover if recoverable
                if (module.isRecoverable()) {
                    try {
                        module.recover();
                        if (module.isEnabled()) {
                            moduleHealth.get(name).setStatus(ModuleState.RUNNING, "Auto-recovered");
                            logger.info("Successfully auto-recovered module {}", name);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to auto-recover module {}: {}", name, e.getMessage());
                    }
                }
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
        if (module != null) {
            if (!module.isEnabled()) {
                logger.warn("Module {} is disabled, enabling before reload", name);
                module.setEnabled(true);
            }

            moduleHealth.get(name).setStatus(ModuleState.RECOVERING);

            CompletableFuture.runAsync(() -> {
                try {
                    module.reloadConfig();
                    moduleHealth.get(name).setStatus(ModuleState.RUNNING, "Reloaded successfully");
                    logger.info("Reloaded module {}", name);
                } catch (Exception e) {
                    moduleHealth.get(name).setStatus(ModuleState.FAILED,
                            "Reload failed: " + e.getMessage());
                    logger.error("Error reloading module {}: {}", name, e.getMessage(), e);

                    // Try recovery
                    tryRecoverModule(module, gameEngine);
                }
            }, taskScheduler.getExecutor());

            return true;
        } else {
            logger.warn("Module {} not found, cannot reload", name);
            return false;
        }
    }

    /**
     * Batch-reloads multiple modules by name.
     *
     * @param moduleNames collection of module names to reload
     * @return the number of modules that were successfully targeted for reload
     * @throws NullPointerException if moduleNames is null
     */
    public int reloadMultiple(Collection<String> moduleNames) {
        Objects.requireNonNull(moduleNames, "Module names collection cannot be null");

        int count = 0;
        for (String name : moduleNames) {
            if (reload(name)) {
                count++;
            }
        }
        logger.info("Initiated reload for {}/{} modules", count, moduleNames.size());
        return count;
    }

    /**
     * Shuts down the module manager, properly disposing resources.
     *
     * @param app the application context
     */
    public void shutdown(Application app) {
        if (!shuttingDown.compareAndSet(false, true)) {
            logger.warn("ModuleManager shutdown already in progress");
            return;
        }

        logger.info("Shutting down ModuleManager");

        // First detach all modules from the state manager in reverse priority order
        List<Map.Entry<Integer, EngineModule<?>>> modulesToDetach =
                new ArrayList<>(manualModules.entrySet());

        // Reverse the list to detach in reverse priority order
        Collections.reverse(modulesToDetach);

        for (Map.Entry<Integer, EngineModule<?>> entry : modulesToDetach) {
            EngineModule<?> module = entry.getValue();
            String moduleName = module.getClass().getSimpleName();

            try {
                if (module.isInitialized() && module.isAttached()) {
                    logger.debug("Detaching module {}", moduleName);
                    stateManager.detach(module);
                    logger.debug("Detached module {}", moduleName);
                }
            } catch (Exception e) {
                logger.error("Error detaching module {}: {}",
                        moduleName, e.getMessage(), e);
            }
        }

        // Then shutdown the executor service
        initExecutor.shutdown();
        try {
            if (!initExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Executor service did not terminate in time, forcing shutdown");
                List<Runnable> pendingTasks = initExecutor.shutdownNow();
                logger.warn("{} tasks were not executed", pendingTasks.size());

                if (!initExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS / 2, TimeUnit.SECONDS)) {
                    logger.error("Executor service still did not terminate");
                }
            }
        } catch (InterruptedException e) {
            logger.warn("Shutdown interrupted", e);
            initExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clear data structures
        moduleInstances.clear();
        modulesByClass.clear();
        manualModules.clear();
        moduleHealth.clear();

        // Reset state flags
        initialized.set(false);
        initializing.set(false);

        logger.info("ModuleManager shutdown complete");
    }

    /**
     * Gets a module by its class.
     *
     * @param moduleClass the class of the module to find
     * @param <T>         the type of the module
     * @return the module instance or null if not found
     * @throws NullPointerException if moduleClass is null
     */
    @SuppressWarnings("unchecked")
    public <T extends EngineModule<?>> T getModule(Class<T> moduleClass) {
        Objects.requireNonNull(moduleClass, "Module class cannot be null");

        // Fast lookup using the class map
        EngineModule<?> module = modulesByClass.get(moduleClass);
        if (module != null) {
            return (T) module;
        }

        // Fallback to iteration for subclass matching (supports interface and inheritance)
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
     * Gets health information for all modules.
     *
     * @return map of module names to health information
     */
    public Map<String, ModuleHealth> getModulesHealth() {
        return Collections.unmodifiableMap(moduleHealth);
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
     * Updates the module manager and performs periodic maintenance.
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
        return initialized.get();
    }

    /**
     * Checks if the module manager is currently initializing.
     *
     * @return true if initialization is in progress
     */
    public boolean isInitializing() {
        return initializing.get();
    }

    /**
     * Checks if the module manager is shutting down.
     *
     * @return true if shutdown is in progress
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

}