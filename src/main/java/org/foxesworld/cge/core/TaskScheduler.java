package org.foxesworld.cge.core;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * A simple task scheduler for engine modules using a work-stealing pool.
 */
public class TaskScheduler {
    private final ForkJoinPool pool;

    public TaskScheduler() {
        this.pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Submit a task for asynchronous execution.
     */
    public Future<?> submit(Runnable task) {
        return pool.submit(task);
    }

    /**
     * Shutdown the scheduler and await termination.
     */
    public void shutdown() throws InterruptedException {
        pool.shutdown();
        pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
    }
}
