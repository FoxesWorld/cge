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
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The {@code ModuleManager} class is responsible for managing engine modules.
 * It supports the following features:
 * <ul>
 *   <li>Manual and automatic registration of modules</li>
 *   <li>Asynchronous initialization</li>
 *   <li>Dependency resolution</li>
 *   <li>Hot-reloading</li>
 *   <li>Module-loaded callbacks</li>
 * </ul>
 */
public class ModuleManager {
    private static final Logger logger = LoggerFactory.getLogger(ModuleManager.class);
    private final AppStateManager stateManager;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;
    private final ExecutorService initExecutor;
    private final Path modulesDir = Paths.get("modules");

    private final TreeMap<Integer, EngineModule<?>> manual = new TreeMap<>();
    private final Map<String, EngineModule<?>> instances = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Consumer<? extends EngineModule<?>>>> moduleLoadedListeners = new ConcurrentHashMap<>();

    public ModuleManager(CalistaGameEngine calistaGameEngine) {
        this.stateManager = calistaGameEngine.getStateManager();
        this.configService = calistaGameEngine.getConfigService();
        this.taskScheduler = calistaGameEngine.getTaskScheduler();
        this.initExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> { Thread thread = new Thread(r); thread.setDaemon(true); return thread; }
        );
    }

    public synchronized void register(EngineModule<?> module, int priority) {
        manual.put(priority, module);
        logger.info("Adding module {}", module.getClass().getSimpleName());
    }

    public void initializeAll(Application app) {
        manual.values().forEach(m -> {
            stateManager.attach(m);
            logger.info("Attached manual module {}", m.getClass().getSimpleName());
            notifyModuleLoaded(m);
        });
    }

    public void loadAll(Application app, Runnable onComplete) {
        List<ModuleDescriptor> descriptors;
        try {
            descriptors = readManifests(modulesDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<ModuleDescriptor> sorted = topologicalSort(descriptors);
        for (ModuleDescriptor desc : sorted) {
            try {
                @SuppressWarnings("unchecked")
                Class<EngineModule<?>> clazz = (Class<EngineModule<?>>) Class.forName(desc.className);
                EngineModule<?> module = clazz
                        .getDeclaredConstructor(ConfigService.class, TaskScheduler.class)
                        .newInstance(configService, taskScheduler);
                instances.put(desc.name, module);
                register(module, desc.priority);
                logger.info("Registered auto module {}", desc.name);
            } catch (Exception e) {
                logger.error("Failed to instantiate module {} ({}): {}",
                        desc.name, desc.className, e.getMessage(), e);
            }
        }

        // Сортируем вручную зарегистрированные модули по приоритету
        List<EngineModule<?>> sortedManual = new ArrayList<>(manual.values());

        // Начинаем последовательную инициализацию
        runSequentially(app, sortedManual.iterator(), onComplete);
    }

    private void runSequentially(Application app,
                                 Iterator<EngineModule<?>> iterator,
                                 Runnable onComplete) {
        if (!iterator.hasNext()) {
            logger.info("All modules initialized sequentially.");
            onModulesLoaded(onComplete);
            return;
        }

        EngineModule<?> module = iterator.next();
        initExecutor.submit(() -> {
            try {
                stateManager.attach(module);
                logger.info("Sequentially attached module {}", module.getClass().getSimpleName());
                notifyModuleLoaded(module);
            } catch (Exception e) {
                logger.error("Error attaching module {}: {}", module.getClass().getSimpleName(), e.getMessage(), e);
            }
            runSequentially(app, iterator, onComplete); // рекурсивный переход к следующему
        });
    }


    public void onModulesLoaded(Runnable runnable) {
        CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
                logger.info("Custom onModulesLoaded callback executed.");
            } catch (Exception e) {
                logger.error("Error during onModulesLoaded callback", e);
            }
        }, initExecutor);
    }

    private void asyncAttach(EngineModule<?> module, Application app) {
        try {
            stateManager.attach(module);
            logger.info("Attached module {}", module.getClass().getSimpleName());
            notifyModuleLoaded(module);
        } catch (Exception e) {
            logger.error("Error attaching module {}: {}",
                    module.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends EngineModule<?>> void notifyModuleLoaded(T module) {
        moduleLoadedListeners.forEach((clazz, listeners) -> {
            if (clazz.isInstance(module)) {
                for (Consumer<? extends EngineModule<?>> listener : listeners) {
                    ((Consumer<T>) listener).accept(module);
                }
            }
        });
    }

    /**
     * Registers a callback to be invoked when the specified module class is loaded.
     * If the module is already loaded, the callback is invoked immediately.
     */
    @SuppressWarnings("unchecked")
    public <T extends EngineModule<?>> void onModuleLoaded(Class<T> moduleClass, Consumer<T> callback) {
        moduleLoadedListeners
                .computeIfAbsent(moduleClass, k -> new CopyOnWriteArrayList<>())
                .add(callback);
        // invoke immediately if already loaded
        T module = getModule(moduleClass);
        if (module != null) {
            callback.accept(module);
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
                    ModuleDescriptor desc = new Gson()
                            .fromJson(Files.newBufferedReader(path), ModuleDescriptor.class);
                    list.add(desc);
                } catch (IOException | JsonSyntaxException e) {
                    logger.error("Error reading descriptor '{}': {}", path, e.getMessage(), e);
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

    private void dfs(ModuleDescriptor d, Map<String, ModuleDescriptor> map,
                     Set<String> visited, List<ModuleDescriptor> sorted, Set<String> stack) {
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

    public void reload(String name) {
        EngineModule<?> module = instances.get(name);
        if (module != null && module.isEnabled()) {
            initExecutor.submit(() -> {
                module.reloadConfig();
                logger.info("Reloaded module {}", name);
            });
        }
    }

    public void shutdown(Application app) {
        initExecutor.shutdown();
        try {
            if (!initExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                initExecutor.shutdownNow();
                if (!initExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.error("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException e) {
            initExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        manual.values().forEach(stateManager::detach);
        logger.info("ModuleManager shutdown complete");
    }

    public Map<String, EngineModule<?>> getInstances() {
        return Collections.unmodifiableMap(instances);
    }

    @SuppressWarnings("unchecked")
    public <T extends EngineModule<?>> T getModule(Class<T> moduleClass) {
        logger.info("Searching for module: {}", moduleClass.getSimpleName());
        for (EngineModule<?> module : manual.values()) {
            if (moduleClass.isInstance(module)) {
                logger.info("Found module in manual: {}", module.getClass().getSimpleName());
                return (T) module;
            }
        }
        for (EngineModule<?> module : instances.values()) {
            if (moduleClass.isInstance(module)) {
                logger.info("Found module in instances: {}", module.getClass().getSimpleName());
                return (T) module;
            }
        }
        logger.warn("Module {} not found", moduleClass.getSimpleName());
        return null;
    }

    public void update(float tpf) {
        // Optional override
    }
}