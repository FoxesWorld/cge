package org.foxesworld.cge.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance task scheduler optimized for game engine workloads.
 * <p>
 * Features:
 * <ul>
 *   <li>Separate thread pools for compute-intensive and IO-bound tasks</li>
 *   <li>First-class support for scheduled and delayed execution</li>
 *   <li>Fine-grained task prioritization</li>
 *   <li>Comprehensive metrics and monitoring</li>
 *   <li>Automatic thread naming for easier debugging</li>
 *   <li>Graceful handling of task exceptions</li>
 * </ul>
 */
public class TaskScheduler {
    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);

    // Core executors for different workload types
    private final ScheduledThreadPoolExecutor scheduledExecutor;
    private final ExecutorService computeExecutor;
    private final ExecutorService ioExecutor;

    // Task monitoring and metrics
    private final ConcurrentHashMap<String, TaskMetrics> metricsMap = new ConcurrentHashMap<>();
    private final AtomicLong totalTasksSubmitted = new AtomicLong(0);
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);
    private final AtomicLong totalTasksFailed = new AtomicLong(0);

    // Thread management
    private final ThreadGroup threadGroup;
    private final int computeThreads;
    private final int ioThreads;

    // Task rejection handler
    private final RejectedExecutionHandler rejectionHandler = (task, executor) -> {
        logger.warn("Task rejected: executor saturated. Consider increasing thread pool size.");
        if (task instanceof Runnable) {
            // Execute in the calling thread as fallback
            try {
                ((Runnable) task).run();
            } catch (Exception e) {
                logger.error("Error executing rejected task in caller thread", e);
            }
        }
    };

    /**
     * Creates a TaskScheduler with thread pools sized based on available processors.
     */
    public TaskScheduler() {
        this(Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().availableProcessors() * 2);
    }

    /**
     * Creates a TaskScheduler with custom thread pool sizes.
     *
     * @param computeThreads number of threads for compute-intensive tasks
     * @param ioThreads number of threads for IO-bound tasks
     */
    public TaskScheduler(int computeThreads, int ioThreads) {
        this.computeThreads = Math.max(2, computeThreads);
        this.ioThreads = Math.max(2, ioThreads);
        this.threadGroup = new ThreadGroup("TaskSchedulerGroup");

        // Initialize thread pools with custom thread factories
        this.scheduledExecutor = new ScheduledThreadPoolExecutor(
                4, // Core scheduler threads
                createThreadFactory("Scheduler", Thread.NORM_PRIORITY),
                rejectionHandler
        );

        this.computeExecutor = new ThreadPoolExecutor(
                this.computeThreads, this.computeThreads,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                createThreadFactory("ComputeTask", Thread.MAX_PRIORITY - 1),
                rejectionHandler
        );

        this.ioExecutor = new ThreadPoolExecutor(
                this.ioThreads, this.ioThreads,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                createThreadFactory("IOTask", Thread.NORM_PRIORITY - 1),
                rejectionHandler
        );

        // Configure scheduled executor to remove tasks after execution
        scheduledExecutor.setRemoveOnCancelPolicy(true);
        logger.info("Java version: " + System.getProperty("java.version"));
        logger.info("TaskScheduler initialized with {} compute threads and {} IO threads",
                computeThreads, ioThreads);
        logger.info("VM args: " + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    /**
     * Submits a compute-intensive task for asynchronous execution.
     *
     * @param task the task to execute
     * @return a Future representing the task
     */
    public Future<?> submit(Runnable task) {
        return submitInternal(task, null, computeExecutor, "default");
    }

    /**
     * Submits an IO-bound task for asynchronous execution.
     *
     * @param task the task to execute
     * @return a Future representing the task
     */
    public Future<?> submitIO(Runnable task) {
        return submitInternal(task, null, ioExecutor, "io");
    }

    /**
     * Submits a task with completion callback.
     *
     * @param task the task to execute
     * @param onComplete callback to run after task completion
     * @return a Future representing the task
     */
    public Future<?> submit(Runnable task, Runnable onComplete) {
        return submitInternal(task, onComplete, computeExecutor, "default");
    }

    /**
     * Submits a task with priority (1-10, higher is more important).
     *
     * @param task the task to execute
     * @param priority priority level (1-10)
     * @return a Future representing the task
     */
    public Future<?> submitWithPriority(Runnable task, int priority) {
        final ExecutorService executor = priority >= 8 ?
                computeExecutor : (priority <= 3 ? ioExecutor : computeExecutor);

        return submitInternal(task, null, executor, "priority-" + priority);
    }

    /**
     * Schedules a task to execute after a delay.
     *
     * @param task the task to execute
     * @param delayMs delay in milliseconds
     * @return a ScheduledFuture representing the task
     */
    public ScheduledFuture<?> schedule(Runnable task, long delayMs) {
        return schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Schedules a task to execute after a delay.
     *
     * @param task the task to execute
     * @param delay the delay amount
     * @param unit the time unit of the delay
     * @return a ScheduledFuture representing the task
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        totalTasksSubmitted.incrementAndGet();
        String taskType = "scheduled";

        return scheduledExecutor.schedule(() -> {
            long startTime = System.nanoTime();
            try {
                task.run();
                totalTasksCompleted.incrementAndGet();
                recordTaskCompletion(taskType, true, startTime);
            } catch (Exception e) {
                totalTasksFailed.incrementAndGet();
                recordTaskCompletion(taskType, false, startTime);
                logger.error("Scheduled task execution failed", e);
            }
        }, delay, unit);
    }

    /**
     * Schedules a task to execute periodically.
     *
     * @param task           the task to execute
     * @param initialDelayMs initial delay in milliseconds
     * @param periodMs       interval in milliseconds
     * @param seconds
     * @return a ScheduledFuture representing the task
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelayMs, long periodMs, TimeUnit seconds) {
        totalTasksSubmitted.incrementAndGet();
        String taskType = "periodic";

        return scheduledExecutor.scheduleAtFixedRate(() -> {
            long startTime = System.nanoTime();
            try {
                task.run();
                totalTasksCompleted.incrementAndGet();
                recordTaskCompletion(taskType, true, startTime);
            } catch (Exception e) {
                totalTasksFailed.incrementAndGet();
                recordTaskCompletion(taskType, false, startTime);
                logger.error("Periodic task execution failed", e);
            }
        }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Executes a task synchronously in the current thread.
     *
     * @param task the task to execute
     */
    public void executeSync(Runnable task) {
        long startTime = System.nanoTime();
        try {
            task.run();
            recordTaskCompletion("sync", true, startTime);
        } catch (Exception e) {
            recordTaskCompletion("sync", false, startTime);
            logger.error("Synchronous task execution failed", e);
        }
    }

    /**
     * Waits for all tasks to complete execution.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if all tasks completed, false if timeout occurred
     */
    public boolean awaitAllTasksCompletion(long timeoutMs) {
        CompletableFuture<Void> computeDone = CompletableFuture.runAsync(() -> {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) computeExecutor;
            while (tpe.getActiveCount() > 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        CompletableFuture<Void> ioDone = CompletableFuture.runAsync(() -> {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) ioExecutor;
            while (tpe.getActiveCount() > 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        try {
            CompletableFuture.allOf(computeDone, ioDone)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }

    /**
     * Shuts down the scheduler gracefully.
     *
     * @param timeoutMs time to wait for tasks to complete in milliseconds
     * @return true if shutdown completed successfully
     */
    public boolean shutdown(long timeoutMs) {
        logger.info("TaskScheduler shutting down...");

        scheduledExecutor.shutdown();
        computeExecutor.shutdown();
        ioExecutor.shutdown();

        try {
            // Wait for scheduled tasks first
            if (!scheduledExecutor.awaitTermination(timeoutMs / 3, TimeUnit.MILLISECONDS)) {
                scheduledExecutor.shutdownNow();
            }

            // Then wait for compute tasks
            if (!computeExecutor.awaitTermination(timeoutMs / 3, TimeUnit.MILLISECONDS)) {
                computeExecutor.shutdownNow();
            }

            // Finally wait for IO tasks
            if (!ioExecutor.awaitTermination(timeoutMs / 3, TimeUnit.MILLISECONDS)) {
                ioExecutor.shutdownNow();
            }

            logger.info("TaskScheduler shutdown complete");
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("TaskScheduler shutdown interrupted");
            return false;
        }
    }

    /**
     * Gets the metrics for a specific task type.
     *
     * @param taskType the type of task
     * @return metrics for the task type
     */
    public TaskMetrics getMetrics(String taskType) {
        return metricsMap.computeIfAbsent(taskType, k -> new TaskMetrics());
    }

    /**
     * Gets a snapshot of all task metrics.
     *
     * @return map of task types to metrics
     */
    public Map<String, TaskMetrics> getAllMetrics() {
        return new ConcurrentHashMap<>(metricsMap);
    }

    /**
     * Resets all task metrics.
     */
    public void resetMetrics() {
        metricsMap.clear();
        totalTasksSubmitted.set(0);
        totalTasksCompleted.set(0);
        totalTasksFailed.set(0);
    }

    /**
     * Gets the total number of tasks submitted.
     *
     * @return count of submitted tasks
     */
    public long getTotalTasksSubmitted() {
        return totalTasksSubmitted.get();
    }

    /**
     * Gets the total number of tasks completed.
     *
     * @return count of completed tasks
     */
    public long getTotalTasksCompleted() {
        return totalTasksCompleted.get();
    }

    /**
     * Gets the total number of tasks that failed.
     *
     * @return count of failed tasks
     */
    public long getTotalTasksFailed() {
        return totalTasksFailed.get();
    }

    /**
     * Gets the underlying executor for compute tasks.
     *
     * @return the executor service
     */
    public ExecutorService getExecutor() {
        return computeExecutor;
    }

    /**
     * Gets the thread group used by this scheduler.
     *
     * @return the thread group
     */
    public ThreadGroup getThreadGroup() {
        return threadGroup;
    }

    /**
     * Internal method to handle task submission with metrics tracking.
     */
    private Future<?> submitInternal(Runnable task, Runnable onComplete, ExecutorService executor, String taskType) {
        totalTasksSubmitted.incrementAndGet();
        long startTime = System.nanoTime();

        return executor.submit(() -> {
            Thread currentThread = Thread.currentThread();
            String originalName = currentThread.getName();
            try {
                // Update thread name for better debugging
                currentThread.setName(originalName + "-" + taskType);

                // Execute the task
                task.run();
                totalTasksCompleted.incrementAndGet();
                recordTaskCompletion(taskType, true, startTime);

                // Execute completion callback if provided
                if (onComplete != null) {
                    try {
                        onComplete.run();
                    } catch (Exception e) {
                        logger.error("Task completion callback failed", e);
                    }
                }
            } catch (Exception e) {
                totalTasksFailed.incrementAndGet();
                recordTaskCompletion(taskType, false, startTime);
                logger.error("Task execution failed: " + taskType, e);
            } finally {
                // Restore original thread name
                currentThread.setName(originalName);
            }
        });
    }

    /**
     * Records task execution metrics.
     */
    private void recordTaskCompletion(String taskType, boolean success, long startTimeNanos) {
        long durationNanos = System.nanoTime() - startTimeNanos;
        getMetrics(taskType).recordExecution(durationNanos, success);
    }

    /**
     * Creates a thread factory with custom naming pattern and priority.
     */
    private ThreadFactory createThreadFactory(String prefix, int priority) {
        AtomicInteger counter = new AtomicInteger(0);
        return r -> {
            Thread thread = new Thread(threadGroup, r,
                    "CGE-" + prefix + "-" + counter.incrementAndGet());
            thread.setPriority(priority);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) ->
                    logger.error("Uncaught exception in thread " + t.getName(), e));
            return thread;
        };
    }

    /**
     * Metrics for task execution.
     */
    public static class TaskMetrics {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalTimeNanos = new AtomicLong(0);
        private final AtomicLong maxTimeNanos = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);

        /**
         * Records the execution of a task.
         *
         * @param durationNanos duration in nanoseconds
         * @param success whether the task completed successfully
         */
        public void recordExecution(long durationNanos, boolean success) {
            count.incrementAndGet();
            totalTimeNanos.addAndGet(durationNanos);

            // Update max execution time if greater
            long currentMax = maxTimeNanos.get();
            while (durationNanos > currentMax) {
                if (maxTimeNanos.compareAndSet(currentMax, durationNanos)) {
                    break;
                }
                currentMax = maxTimeNanos.get();
            }

            if (success) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        }

        /**
         * Gets the total number of tasks executed.
         *
         * @return task count
         */
        public long getCount() {
            return count.get();
        }

        /**
         * Gets the average execution time in milliseconds.
         *
         * @return average time in milliseconds
         */
        public double getAverageTimeMs() {
            long c = count.get();
            return c > 0 ? totalTimeNanos.get() / (c * 1_000_000.0) : 0;
        }

        /**
         * Gets the maximum execution time in milliseconds.
         *
         * @return maximum time in milliseconds
         */
        public double getMaxTimeMs() {
            return maxTimeNanos.get() / 1_000_000.0;
        }

        /**
         * Gets the success rate (0.0 to 1.0).
         *
         * @return success rate
         */
        public double getSuccessRate() {
            long c = count.get();
            return c > 0 ? (double)successCount.get() / c : 0;
        }

        /**
         * Gets the number of successful task completions.
         *
         * @return success count
         */
        public long getSuccessCount() {
            return successCount.get();
        }

        /**
         * Gets the number of task failures.
         *
         * @return failure count
         */
        public long getFailureCount() {
            return failureCount.get();
        }

        @Override
        public String toString() {
            return String.format(
                    "TaskMetrics[count=%d, avgTime=%.2fms, maxTime=%.2fms, successRate=%.2f%%]",
                    count.get(), getAverageTimeMs(), getMaxTimeMs(), getSuccessRate() * 100);
        }
    }
}