package org.foxesworld.cge.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * A high-performance, modern task scheduler optimized for game engine workloads on Java 17.
 * It is built upon Java's CompletableFuture for powerful asynchronous programming.
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li><b>CompletableFuture API:</b> All task submissions return {@link CompletableFuture},
 *       enabling powerful, non-blocking asynchronous workflows.</li>
 *   <li><b>Type-Safe Task Distinction:</b> Uses a {@link TaskType} enum to clearly
 *       separate compute-intensive and IO-bound tasks onto optimized thread pools.</li>
 *   <li><b>Optimized IO Pool:</b> Employs a cached thread pool for IO-bound tasks,
 *       efficiently handling numerous blocking operations.</li>
 *   <li><b>Centralized Metrics & Error Handling:</b> A unified wrapping mechanism
 *       ensures all tasks have consistent metrics and robust exception logging.</li>
 *   <li><b>Support for Callables:</b> Natively handles tasks that return results.</li>
 * </ul>
 *
 * @version 2.0-java17
 * @author CalistaF0X & Gemini
 */
public class TaskScheduler {
    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);

    /**
     * Defines the type of task to guide its execution on the appropriate thread pool.
     */
    public enum TaskType {
        /** For CPU-intensive operations like physics, AI calculations, complex logic. */
        COMPUTE,
        /** For blocking operations like file I/O, network requests, database access. */
        IO
    }

    private final ScheduledThreadPoolExecutor scheduledExecutor;
    private final ExecutorService computeExecutor;
    private final ExecutorService ioExecutor;

    private final ConcurrentHashMap<String, TaskMetrics> metricsMap = new ConcurrentHashMap<>();
    private final AtomicLong totalTasksSubmitted = new AtomicLong(0);

    private final ThreadGroup threadGroup;

    /**
     * Creates a TaskScheduler with thread pools sized based on available processors.
     */
    public TaskScheduler() {
        // Defaults: N cores for compute, 4*N for IO max threads (a reasonable starting point)
        this(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors() * 4);
    }

    /**
     * Creates a TaskScheduler with custom thread pool sizes.
     *
     * @param computeThreads number of threads for compute-intensive tasks.
     * @param maxIoThreads   maximum number of threads for the IO-bound task pool.
     */
    public TaskScheduler(int computeThreads, int maxIoThreads) {
        this.threadGroup = new ThreadGroup("CGE-TaskGroup");
        int coreSchedulerThreads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

        this.scheduledExecutor = new ScheduledThreadPoolExecutor(
                coreSchedulerThreads,
                createThreadFactory("Scheduler", Thread.NORM_PRIORITY)
        );
        this.scheduledExecutor.setRemoveOnCancelPolicy(true);

        this.computeExecutor = new ThreadPoolExecutor(
                computeThreads, computeThreads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                createThreadFactory("Compute", Thread.NORM_PRIORITY)
        );

        this.ioExecutor = createIoExecutor(maxIoThreads);

        logger.info("TaskScheduler initialized [Compute Threads: {}, IO Pool: Cached, Max IO Threads: {}]",
                computeThreads, maxIoThreads);
    }

    /**
     * Submits a task that returns a value.
     *
     * @param task     The task to execute.
     * @param taskType The type of task (COMPUTE or IO).
     * @return A CompletableFuture representing the pending result of the task.
     */
    public <T> CompletableFuture<T> submit(Callable<T> task, TaskType taskType) {
        ExecutorService executor = getExecutorForType(taskType);
        Callable<T> wrappedTask = wrapTask(task, taskType.name());
        totalTasksSubmitted.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return wrappedTask.call();
            } catch (Exception e) {
                // This will be caught and propagated by CompletableFuture
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Submits a fire-and-forget task.
     *
     * @param task     The task to execute.
     * @param taskType The type of task (COMPUTE or IO).
     * @return A CompletableFuture<Void> that completes when the task is done.
     */
    public CompletableFuture<Void> submit(Runnable task, TaskType taskType) {
        Callable<Void> callable = Executors.callable(task, null);
        return submit(callable, taskType);
    }

    /**
     * Schedules a task to run after a given delay.
     *
     * @param task    The task to execute.
     * @param delay   The time from now to delay execution.
     * @param unit    The time unit of the delay parameter.
     * @return A ScheduledFuture that can be used to cancel the task.
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        Runnable wrappedTask = wrapScheduledTask(task, "scheduled");
        totalTasksSubmitted.incrementAndGet();
        return scheduledExecutor.schedule(wrappedTask, delay, unit);
    }

    /**
     * Schedules a task to execute periodically.
     *
     * @param task         The task to execute.
     * @param initialDelay The time to delay first execution.
     * @param period       The period between successive executions.
     * @param unit         The time unit of the initialDelay and period parameters.
     * @return A ScheduledFuture that can be used to cancel the task.
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        Runnable wrappedTask = wrapScheduledTask(task, "periodic");
        totalTasksSubmitted.incrementAndGet();
        return scheduledExecutor.scheduleAtFixedRate(wrappedTask, initialDelay, period, unit);
    }

    /**
     * Shuts down the scheduler gracefully.
     * It will wait for currently running tasks to finish but will not accept new tasks.
     *
     * @param timeout The maximum time to wait.
     * @param unit    The time unit of the timeout argument.
     * @return true if all executors terminated, false if the timeout elapsed.
     */
    public boolean shutdown(long timeout, TimeUnit unit) {
        logger.info("TaskScheduler shutting down...");
        boolean allTerminated = true;
        long singleTimeout = timeout > 3 ? timeout / 3 : timeout;

        for (ExecutorService executor : new ExecutorService[]{computeExecutor, ioExecutor, scheduledExecutor}) {
            executor.shutdown();
        }

        try {
            if (!computeExecutor.awaitTermination(singleTimeout, unit)) {
                logger.warn("Compute executor did not terminate in time.");
                computeExecutor.shutdownNow();
                allTerminated = false;
            }
            if (!ioExecutor.awaitTermination(singleTimeout, unit)) {
                logger.warn("I/O executor did not terminate in time.");
                ioExecutor.shutdownNow();
                allTerminated = false;
            }
            if (!scheduledExecutor.awaitTermination(singleTimeout, unit)) {
                logger.warn("Scheduled executor did not terminate in time.");
                scheduledExecutor.shutdownNow();
                allTerminated = false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Shutdown was interrupted.", e);
            computeExecutor.shutdownNow();
            ioExecutor.shutdownNow();
            scheduledExecutor.shutdownNow();
            return false;
        }

        if(allTerminated) {
            logger.info("TaskScheduler shutdown complete.");
        } else {
            logger.warn("TaskScheduler shutdown finished forcefully.");
        }
        return allTerminated;
    }

    // --- Metrics and Monitoring ---

    public TaskMetrics getMetrics(String taskType) {
        return metricsMap.computeIfAbsent(taskType, k -> new TaskMetrics());
    }

    public Map<String, TaskMetrics> getAllMetrics() {
        return new ConcurrentHashMap<>(metricsMap);
    }

    public void resetMetrics() {
        metricsMap.clear();
        totalTasksSubmitted.set(0);
    }

    public long getTotalTasksSubmitted() {
        return totalTasksSubmitted.get();
    }

    public ExecutorService getComputeExecutor() {
        return computeExecutor;
    }

    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    // --- Private Helper Methods ---

    private ExecutorService getExecutorForType(TaskType taskType) {
        return (taskType == TaskType.COMPUTE) ? computeExecutor : ioExecutor;
    }

    private <T> Callable<T> wrapTask(Callable<T> task, String taskType) {
        return () -> {
            long startTime = System.nanoTime();
            boolean success = false;
            try {
                T result = task.call();
                success = true;
                return result;
            } catch (Exception e) {
                logger.error("Task [{}] execution failed", taskType, e);
                throw e; // Re-throw to be handled by CompletableFuture
            } finally {
                recordTaskCompletion(taskType, success, startTime);
            }
        };
    }

    private Runnable wrapScheduledTask(Runnable task, String taskType) {
        return () -> {
            long startTime = System.nanoTime();
            boolean success = false;
            try {
                task.run();
                success = true;
            } catch (Exception e) {
                logger.error("Scheduled task [{}] execution failed", taskType, e);
                // Don't re-throw for scheduled tasks, or they will stop executing.
            } finally {
                recordTaskCompletion(taskType, success, startTime);
            }
        };
    }

    private void recordTaskCompletion(String taskType, boolean success, long startTimeNanos) {
        long durationNanos = System.nanoTime() - startTimeNanos;
        getMetrics(taskType).recordExecution(durationNanos, success);
    }

    private ThreadFactory createThreadFactory(String prefix, int priority) {
        AtomicInteger counter = new AtomicInteger(0);
        return r -> {
            Thread thread = new Thread(threadGroup, r, "CGE-" + prefix + "-" + counter.incrementAndGet());
            thread.setPriority(priority);
            thread.setDaemon(true); // Daemon threads won't prevent JVM exit
            thread.setUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception in thread {}", t.getName(), e));
            return thread;
        };
    }

    /**
     * Creates an I/O executor configured as a cached thread pool.
     * This is ideal for numerous, short-lived, blocking I/O tasks on Java 17.
     *
     * @param maxIoThreads The maximum number of threads allowed in the pool.
     * @return A configured ExecutorService for I/O tasks.
     */
    private ExecutorService createIoExecutor(int maxIoThreads) {
        return new ThreadPoolExecutor(
                0, // Core pool size - start with zero threads
                maxIoThreads, // Max threads - grow up to this limit
                60L, TimeUnit.SECONDS, // Keep-alive time for idle threads
                new SynchronousQueue<>(), // A queue that hands off tasks directly, forcing new thread creation if all are busy
                createThreadFactory("IO", Thread.NORM_PRIORITY)
        );
    }

    /**
     * Holds performance metrics for a specific type of task. This class is thread-safe.
     */
    public static class TaskMetrics {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalTimeNanos = new AtomicLong(0);
        private final AtomicLong maxTimeNanos = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);

        public void recordExecution(long durationNanos, boolean success) {
            count.incrementAndGet();
            totalTimeNanos.addAndGet(durationNanos);
            maxTimeNanos.accumulateAndGet(durationNanos, Math::max); // More concise way to set max

            if (success) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        }

        public long getCount() { return count.get(); }
        public double getAverageTimeMs() {
            long c = count.get();
            return c > 0 ? totalTimeNanos.get() / (c * 1_000_000.0) : 0;
        }
        public double getMaxTimeMs() { return maxTimeNanos.get() / 1_000_000.0; }
        public long getSuccessCount() { return successCount.get(); }
        public long getFailureCount() { return failureCount.get(); }
        public double getSuccessRate() {
            long c = count.get();
            return c > 0 ? (double) successCount.get() / c : 1.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "Metrics[count=%d, avgTime=%.3fms, maxTime=%.3fms, successRate=%.2f%%]",
                    getCount(), getAverageTimeMs(), getMaxTimeMs(), getSuccessRate() * 100);
        }
    }
}