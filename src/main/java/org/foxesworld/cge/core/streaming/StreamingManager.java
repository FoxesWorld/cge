package org.foxesworld.cge.core.streaming;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Универсальный менеджер для асинхронного стриминга данных по ключу.
 * @param <K> тип ключа
 * @param <V> тип данных
 */
public class StreamingManager<K, V> {
    /** Провайдер данных: по ключу возвращает значение или кидает исключение */
    @FunctionalInterface
    public interface DataProvider<K, V> {
        V load(K key) throws Exception;
    }

    private final DataProvider<K, V> provider;
    private final ExecutorService executor;

    // Кэш опционально хранит загруженные элементы
    private final Map<K, V> cache;
    private final boolean useCache;

    private volatile boolean shutdown = false;

    /**
     * Создаёт StreamingManager без кэширования.
     */
    public StreamingManager(DataProvider<K, V> provider) {
        this(provider, false, 0);
    }

    /**
     * Создаёт StreamingManager с или без кэширования и с заданным пулом потоков.
     * @param provider источник данных
     * @param useCache включить ли кэширование результатов
     * @param threads  число потоков в пуле (если <=0, используется number of processors)
     */
    public StreamingManager(DataProvider<K, V> provider, boolean useCache, int threads) {
        this.provider = Objects.requireNonNull(provider, "DataProvider must not be null");
        this.useCache = useCache;
        this.cache = useCache ? new ConcurrentHashMap<>() : null;
        int n = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "StreamingManager-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Асинхронно получает данные по ключу.
     * @param key       ключ (не null)
     * @param onSuccess вызывается при успешной загрузке данных
     * @param onError   вызывается при ошибке загрузки (не null)
     */
    public void streamAsync(K key, Consumer<V> onSuccess, Consumer<Throwable> onError) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(onError, "Error callback must not be null");
        if (shutdown) {
            onError.accept(new IllegalStateException("Manager is shutdown"));
            return;
        }
        // если кэш включён и данные уже есть
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

    /**
     * Синхронная попытка получения (и кэширования) значения, если включён кэш.
     */
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "Key must not be null");
        if (!useCache) {
            throw new UnsupportedOperationException("Cache is disabled");
        }
        return Optional.ofNullable(cache.get(key));
    }

    /**
     * Удаляет из кэша конкретный элемент (если кэш включён).
     */
    public void purge(K key) {
        if (useCache) {
            cache.remove(key);
        }
    }

    /**
     * Очищает весь кэш.
     */
    public void clearCache() {
        if (useCache) {
            cache.clear();
        }
    }

    /**
     * Завершает работу менеджера, больше не принимает задачи.
     */
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
