package org.foxesworld.cge.core.loader;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.core.utils.CallbackLatch;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A generic asynchronous JSON list loader that reads a list of entries of type {@code E}
 * from a JSON resource and processes each entry using the {@link #loadAllAsync()} method.
 * <p>
 * Subclasses must specify the JSON resource location, the GSON list type, and the logic to
 * load each entry. Supports both standalone asynchronous loading via {@link #loadAllAsync()}
 * and latch-based synchronization via {@link #loadWithLatch(CallbackLatch)}.
 *
 * @param <E> the type of entries parsed from the JSON resource (e.g., file paths or descriptor objects)
 */
public abstract class AbstractAssetLoader<E> {

    private AssetProgressListener progressListener;

    public void setProgressListener(AssetProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Asynchronously loads all entries defined in the JSON resource,
     * invoking {@link #loadEntryAsync(Object)} for each and returning a
     * {@link CompletableFuture} that completes with the total count of loaded items.
     *
     * @return future with total number of loaded items
     */
    public CompletableFuture<Integer> loadAllAsync() {
        InputStream is = getClass().getClassLoader().getResourceAsStream(getJsonResourcePath());
        if (is == null) {
            logger.error("JSON resource '{}' not found", getJsonResourcePath());
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            int total = 0;
            try (InputStreamReader reader = new InputStreamReader(is)) {
                List<E> entries = gson.fromJson(reader, getListType());
                int count = entries != null ? entries.size() : 0;
                int loaded = 0;
                for (E entry : entries) {
                    try {
                        total += loadEntryAsync(entry).get();
                    } catch (Exception ex) {
                        logger.warn("Failed to load entry {}: {}", entry, ex.getMessage());
                    }
                    loaded++;
                    if (progressListener != null) {
                        progressListener.onProgress(getClass().getSimpleName(), loaded, count);
                    }
                }
            } catch (Exception ex) {
                logger.error("Error parsing JSON '{}': {}", getJsonResourcePath(), ex.getMessage());
            }
            return total;
        }, executor);
    }

    /** Logger instance for this loader. */
    protected final Logger logger = LogManager.getLogger(getClass());

    /** Thread pool for executing asynchronous load tasks. */
    private final ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
    );

    /** GSON instance for JSON parsing. */
    private final Gson gson = new Gson();

    /**
     * Returns the classpath resource path to the JSON file, e.g. "models.json" or "textures.json".
     *
     * @return JSON resource path
     */
    protected abstract String getJsonResourcePath();

    /**
     * Returns the generic list type for GSON deserialization, e.g.
     * {@code new TypeToken<List<E>>(){}.getType()}.
     *
     * @return GSON Type for list of {@code E}
     */
    protected abstract Type getListType();

    /**
     * Processes a single entry of type {@code E}. Implementations should
     * load or register the entry and return the count of items loaded (typically 1).
     *
     * @param entry the entry parsed from JSON
     * @return the number of successfully loaded items (>=0)
     */
    protected abstract CompletableFuture<Integer> loadEntryAsync(E entry);


    /**
     * Loads all entries and coordinates completion with a {@link CallbackLatch}.
     * When loading finishes (whether successfully or not), the latch's
     * {@link CallbackLatch#taskDone()} is invoked exactly once.
     *
     * @param latch the callback latch to signal on completion
     */
    public void loadWithLatch(CallbackLatch latch) {
        loadAllAsync()
                .thenAccept(count -> logger.info("Loaded {} entries from {}", count, getJsonResourcePath()))
                .thenRun(latch::taskDone)
                .exceptionally(ex -> {
                    logger.error("Error loading resource '{}': {}", getJsonResourcePath(), ex.getMessage());
                    latch.taskDone();
                    return null;
                });
    }
}
