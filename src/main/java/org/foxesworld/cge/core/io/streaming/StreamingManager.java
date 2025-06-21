package org.foxesworld.cge.core.io.streaming;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class StreamingManager<K, V> {
    @FunctionalInterface
    public interface DataProvider<K, V> {
        V load(K key) throws Exception;
    }

    private final DataProvider<K, V> provider;
    private final ExecutorService executor;
    private final Map<K, V> cache;
    private final boolean useCache;
    private volatile boolean shutdown = false;

    public StreamingManager(DataProvider<K, V> provider) {
        this(provider, false, 0);
    }

    public StreamingManager(DataProvider<K, V> provider, boolean useCache, int threads) {
        this.provider = Objects.requireNonNull(provider, "DataProvider must not be null");
        this.useCache = useCache;
        this.cache = useCache ? new ConcurrentHashMap<>() : null;
        int nThreads = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(nThreads, r -> {
            Thread t = new Thread(r, "StreamingManager-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    public void streamAsync(K key, Consumer<V> onSuccess, Consumer<Throwable> onError) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(onError, "onError callback must not be null");
        if (shutdown) {
            onError.accept(new IllegalStateException("Manager is shutdown"));
            return;
        }
        if (useCache) {
            V cached = cache.get(key);
            if (cached != null) {
                onSuccess.accept(cached);
                return;
            }
        }

        executor.submit(() -> {
            try {
                V value;
                if (useCache) {
                    value = cache.computeIfAbsent(key, k -> {
                        try {
                            return provider.load(k);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    });
                } else {
                    value = provider.load(key);
                }
                onSuccess.accept(value);
            } catch (CompletionException ce) {
                onError.accept(ce.getCause());
            } catch (Throwable t) {
                onError.accept(t);
            }
        });
    }

    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "Key must not be null");
        if (!useCache) {
            throw new UnsupportedOperationException("Cache is disabled");
        }
        return Optional.ofNullable(cache.get(key));
    }

    public void purge(K key) {
        if (useCache) {
            cache.remove(key);
        }
    }

    public void clearCache() {
        if (useCache) {
            cache.clear();
        }
    }

    public synchronized void shutdown() {
        if (shutdown) return;
        shutdown = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
