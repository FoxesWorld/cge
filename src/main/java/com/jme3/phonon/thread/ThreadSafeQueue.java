package com.jme3.phonon.thread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance, thread-safe task queue for batching runnables to be executed
 * in the calling thread (e.g., in a render or update loop).
 *
 * Features:
 * - Lock-free concurrent enqueuing (using ConcurrentLinkedQueue)
 * - Minimal allocations per cycle
 * - Robust error handling (logs exceptions, never interrupts iteration)
 * - Java 17+ compatible (no reflection, no deprecated APIs)
 */
public class ThreadSafeQueue implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ThreadSafeQueue.class.getName());

    // Fast, lock-free, unbounded queue for multi-threaded producers
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    // Optionally: Reuse a list for batch execution to reduce allocations
    private final List<Runnable> buffer = new ArrayList<>(32);

    /**
     * Enqueue a task for later execution.
     *
     * @param task the Runnable to enqueue
     * @throws IllegalArgumentException if task is null
     */
    public void enqueue(Runnable task) {
        if (task == null) throw new IllegalArgumentException("Runnable task cannot be null.");
        queue.offer(task);
    }

    /**
     * Executes all queued tasks in the calling thread.
     * Any exceptions in tasks are logged, but do not interrupt processing.
     */
    @Override
    public void run() {
        // Drain queue into buffer to execute outside the queue
        buffer.clear();
        Runnable task;
        while ((task = queue.poll()) != null) {
            buffer.add(task);
        }
        if (buffer.isEmpty()) return;

        for (Runnable r : buffer) {
            try {
                r.run();
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Exception in ThreadSafeQueue task!", t);
            }
        }
    }

    /**
     * Returns the number of tasks currently queued.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Returns true if the queue is empty.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}