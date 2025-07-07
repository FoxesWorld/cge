package org.foxesworld.cge.core.module;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe, non-blocking utility for collecting performance metrics for a specific operation.
 * <p>
 * This class is designed for high-concurrency scenarios, such as tracking the execution time
 * of tasks in a game loop or a server. It uses atomic primitives and lock-free data structures
 * to minimize overhead and contention.
 * <p>
 * Key features:
 * <ul>
 *     <li>Tracks total count, total time, minimum, and maximum execution times.</li>
 *     <li>Calculates overall average and a moving average over a recent window of executions.</li>
 *     <li>Fully thread-safe. All methods can be called from multiple threads concurrently.</li>
 * </ul>
 * <b>Design Note:</b> This class prioritizes low-overhead recording ({@link #recordExecution}) over
 * strict consistency in readers (getters). Getters may return slightly stale or approximate values
 * under high contention, which is a standard and acceptable trade-off for high-performance
 * monitoring tools.
 */
public class OperationMetrics {

    private final AtomicLong minMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxMs = new AtomicLong(0);
    private final AtomicLong totalMs = new AtomicLong(0);
    private final AtomicInteger count = new AtomicInteger(0);

    /**
     * The number of recent executions to consider for the moving average.
     */
    private static final int MOVING_AVG_WINDOW = 100;
    private final ConcurrentLinkedQueue<Long> recentDurations = new ConcurrentLinkedQueue<>();

    /**
     * Records a single execution of the operation with its duration.
     * This method is the primary entry point for feeding data into the metrics collector.
     * It is non-blocking and highly optimized for concurrent calls.
     *
     * @param durationMs The duration of the operation in milliseconds.
     */
    public void recordExecution(long durationMs) {
        // Atomically update min/max using a compare-and-set loop.
        updateMin(durationMs);
        updateMax(durationMs);

        // Atomically update total time and execution count.
        totalMs.addAndGet(durationMs);
        count.incrementAndGet();

        // Add the duration to the queue for the moving average calculation.
        recentDurations.add(durationMs);

        // Trim the queue if it exceeds the window size.
        // ConcurrentLinkedQueue.size() is O(n), but n is small and capped at MOVING_AVG_WINDOW + 1.
        if (recentDurations.size() > MOVING_AVG_WINDOW) {
            recentDurations.poll(); // Removes the oldest element.
        }
    }

    /**
     * Atomically updates the minimum value if the new value is smaller.
     * Uses a non-blocking compare-and-set (CAS) loop.
     */
    private void updateMin(long value) {
        long currentMin;
        do {
            currentMin = minMs.get();
            if (value >= currentMin) {
                break; // New value is not smaller, no need to update.
            }
        } while (!minMs.compareAndSet(currentMin, value));
    }

    /**
     * Atomically updates the maximum value if the new value is larger.
     * Uses a non-blocking compare-and-set (CAS) loop.
     */
    private void updateMax(long value) {
        long currentMax;
        do {
            currentMax = maxMs.get();
            if (value <= currentMax) {
                break; // New value is not larger, no need to update.
            }
        } while (!maxMs.compareAndSet(currentMax, value));
    }

    /**
     * Gets the minimum execution time recorded.
     *
     * @return The minimum duration in milliseconds, or 0 if no executions have been recorded yet.
     */
    public long getMinMs() {
        long min = minMs.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }

    /**
     * Gets the maximum execution time recorded.
     *
     * @return The maximum duration in milliseconds, or 0 if no executions have been recorded yet.
     */
    public long getMaxMs() {
        return maxMs.get();
    }

    /**
     * Gets the total cumulative execution time of all recorded operations.
     *
     * @return The total duration in milliseconds.
     */
    public long getTotalMs() {
        return totalMs.get();
    }

    /**
     * Gets the total number of executions recorded.
     *
     * @return The total count of operations.
     */
    public int getCount() {
        return count.get();
    }

    /**
     * Calculates the average execution time across all recorded operations.
     * <p>
     * Note: Under high concurrency, this value is an approximation as the total duration
     * and count are read non-atomically.
     *
     * @return The average duration in milliseconds, or 0 if no executions have been recorded.
     */
    public double getAvgMs() {
        int currentCount = count.get();
        long currentTotal = totalMs.get();
        return currentCount > 0 ? (double) currentTotal / currentCount : 0;
    }

    /**
     * Calculates the moving average of recent execution times.
     * This average is calculated over the last {@value #MOVING_AVG_WINDOW} executions,
     * providing a more current snapshot of performance.
     *
     * @return The moving average in milliseconds, or 0 if no executions have been recorded.
     */
    public double getMovingAvgMs() {
        // The stream is created over a snapshot of the queue's elements at that moment.
        if (recentDurations.isEmpty()) {
            return 0;
        }

        return recentDurations.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Returns a string representation of the current metrics.
     *
     * @return A formatted string summarizing the key performance metrics.
     */
    @Override
    public String toString() {
        return String.format(Locale.US, "Count: %d, Avg: %.2f ms, Moving Avg: %.2f ms, Min: %d ms, Max: %d ms",
                getCount(), getAvgMs(), getMovingAvgMs(), getMinMs(), getMaxMs());
    }
}