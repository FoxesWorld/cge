package org.foxesworld.cge.core;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A task scheduler for engine modules using a work-stealing pool.
 */
public class TaskScheduler {
    private final ForkJoinPool pool;
    private static final Logger logger = Logger.getLogger(TaskScheduler.class.getName());

    // Constructor with default thread pool size (based on available processors)
    public TaskScheduler() {
        this(Runtime.getRuntime().availableProcessors());
    }

    // Constructor with custom pool size
    public TaskScheduler(int threads) {
        this.pool = new ForkJoinPool(threads);
    }

    /**
     * Submit a task for asynchronous execution.
     */
    public Future<?> submit(Runnable task) {
        return pool.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Task execution failed", e);
            }
        });
    }

    /**
     * Submit a task for asynchronous execution with a callback for completion.
     */
    public Future<?> submit(Runnable task, Runnable onComplete) {
        return pool.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Task execution failed", e);
            } finally {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    /**
     * Submit a task with priority (in future, prioritize based on task queue or other mechanisms).
     * Currently, ForkJoinPool doesn't support priorities, but we can introduce a simple workaround.
     */
    public Future<?> submitWithPriority(Runnable task, int priority) {
        // For now, we simply use submit and log priority
        logger.info("Submitting task with priority: " + priority);
        return submit(task);
    }

    /**
     * Waits for all tasks to finish execution.
     */
    public void awaitAllTasksCompletion() throws InterruptedException {
        while (!pool.isQuiescent()) {
            Thread.sleep(10);
        }
    }

    /**
     * Shutdown the scheduler and await termination.
     */
    public void shutdown() throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            logger.warning("TaskScheduler did not terminate in time");
        }
    }

    /**
     * Execute task synchronously (in the current thread).
     */
    public void executeSync(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Synchronous task execution failed", e);
        }
    }

    public ForkJoinPool getPool() {
        return pool;
    }
}
