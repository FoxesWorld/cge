package org.foxesworld.cge.core.utils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class that calls a final callback after a set number of asynchronous tasks complete.
 */
public class CallbackLatch {
    private final AtomicInteger remaining;
    private final Runnable finalCallback;

    /**
     * Creates a new CallbackLatch.
     *
     * @param count         the number of tasks to wait for
     * @param finalCallback the callback to run after all tasks complete
     */
    public CallbackLatch(int count, Runnable finalCallback) {
        if (count <= 0) {
            throw new IllegalArgumentException("Task count must be > 0");
        }
        this.remaining = new AtomicInteger(count);
        this.finalCallback = finalCallback;
    }

    /**
     * Call this method when a task is complete.
     * When all tasks are done, the final callback is invoked once.
     */
    public void taskDone() {
        if (remaining.decrementAndGet() == 0) {
            finalCallback.run();
        }
    }
}
