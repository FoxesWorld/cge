package org.foxesworld.cge.core.loader;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.io.progressBar.ProgressListener;
import org.foxesworld.cge.core.utils.CallbackLatch;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generic robust asynchronous JSON list loader with improved concurrency and streamlined checks.
 *
 * @param <E> Entry type parsed from JSON (e.g. file path, descriptor)
 */
public abstract class AbstractAssetLoader<E> {

    private int entriesCount = 0;

    private static final Logger logger = LogManager.getLogger(AbstractAssetLoader.class);

    /**
     * Shared thread pool for all loaders, sized to available processors or at least 2.
     */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors())
    );

    /**
     * Single shared GSON instance.
     */
    private static final Gson GSON = new Gson();

    private ProgressListener progressListener;
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private CompletableFuture<Integer> loadFuture;

    /**
     * Sets a progress listener for this loader.
     */
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Asynchronously loads JSON resource entries exactly once per loader instance.
     * Subsequent invocations return the same future.
     *
     * @return completable future containing the total number of loaded items
     */
    public CompletableFuture<Integer> loadAllAsync() {
        if (loaded.get()) {
            return loadFuture != null ? loadFuture : CompletableFuture.completedFuture(0);
        }
        synchronized (this) {
            if (loaded.get()) {
                return loadFuture != null ? loadFuture : CompletableFuture.completedFuture(0);
            }
            loaded.set(true);
            loadFuture = CompletableFuture.supplyAsync(() -> {
                int totalLoaded = 0;
                String resourcePath = getJsonResourcePath();
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is == null) {
                        logger.error("JSON resource '{}' not found.", resourcePath);
                        return 0;
                    }
                    try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        List<E> entries = GSON.fromJson(reader, getListType());
                        if (entries == null || entries.isEmpty()) {
                            return 0;
                        } else {
                            entriesCount = entries.size();
                        }
                        Set<E> uniqueEntries = new LinkedHashSet<>(entries);
                        int entryCount = uniqueEntries.size();
                        int loadedCount = 0;

                        for (E entry : uniqueEntries) {
                            try {
                                totalLoaded += loadEntryAsync(entry).get();
                            } catch (Exception ex) {
                                logger.warn("Failed to load entry '{}': {}", entry, ex.getMessage());
                            }
                            loadedCount++;
                            if (progressListener != null) {
                                progressListener.onProgress(
                                        getClass().getSimpleName(), loadedCount, entryCount
                                );
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Error loading resource '{}': {}", resourcePath, ex.getMessage());
                }
                return totalLoaded;
            }, EXECUTOR);
            return loadFuture;
        }
    }

    /**
     * Loads all entries and signals completion using the provided latch.
     * The latch is always signaled, even if loading fails.
     *
     * @param latch latch to signal on completion
     */
    public void loadWithLatch(CallbackLatch latch) {
        loadAllAsync().thenAccept(count -> logger.info("Loaded {} entries from {}", count, getJsonResourcePath())).whenComplete((result, error) -> latch.taskDone());
    }

    public int getFileEntries(){
        return entriesCount;
    }

    /**
     * @return Classpath resource path to the JSON file (e.g. "models.json").
     */
    protected abstract String getJsonResourcePath();

    /**
     * @return GSON Type for deserializing a List<E>.
     */
    protected abstract Type getListType();

    /**
     * Implementations should load or register the entry and return items loaded (commonly 1).
     *
     * @param entry parsed entry from JSON
     * @return future with number of items loaded
     */
    protected abstract CompletableFuture<Integer> loadEntryAsync(E entry);
}