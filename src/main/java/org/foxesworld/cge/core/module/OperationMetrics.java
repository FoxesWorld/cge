package org.foxesworld.cge.core.module;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance metrics for a specific operation.
 */
public class OperationMetrics {
    private final AtomicLong minMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxMs = new AtomicLong(0);
    private final AtomicLong totalMs = new AtomicLong(0);
    private final AtomicInteger count = new AtomicInteger(0);

    // For calculating moving average
    private static final int MOVING_AVG_WINDOW = 100;
    private final ConcurrentLinkedQueue<Long> recentDurations = new ConcurrentLinkedQueue<>();

    public void recordExecution(long durationMs) {
        // Update min/max atomically
        updateMin(durationMs);
        updateMax(durationMs);

        // Update total and count
        totalMs.addAndGet(durationMs);
        count.incrementAndGet();

        // Add to recent durations for moving average
        recentDurations.add(durationMs);
        while (recentDurations.size() > MOVING_AVG_WINDOW) {
            recentDurations.poll();
        }
    }

    private void updateMin(long value) {
        long currentMin = minMs.get();
        while (value < currentMin) {
            if (minMs.compareAndSet(currentMin, value)) {
                break;
            }
            currentMin = minMs.get();
        }
    }

    private void updateMax(long value) {
        long currentMax = maxMs.get();
        while (value > currentMax) {
            if (maxMs.compareAndSet(currentMax, value)) {
                break;
            }
            currentMax = maxMs.get();
        }
    }

    public long getMinMs() {
        return minMs.get() == Long.MAX_VALUE ? 0 : minMs.get();
    }

    public long getMaxMs() {
        return maxMs.get();
    }

    public long getTotalMs() {
        return totalMs.get();
    }

    public int getCount() {
        return count.get();
    }

    public double getAvgMs() {
        int countValue = count.get();
        return countValue > 0 ? (double)totalMs.get() / countValue : 0;
    }

    /**
     * Gets the moving average of recent execution times.
     *
     * @return the moving average in milliseconds
     */
    public double getMovingAvgMs() {
        if (recentDurations.isEmpty()) {
            return 0;
        }

        return recentDurations.stream()
                .mapToLong(Long::valueOf)
                .average()
                .orElse(0);
    }

    @Override
    public String toString() {
        return String.format("Count=%d, Avg=%.2f ms, Min=%d ms, Max=%d ms",
                getCount(), getAvgMs(), getMinMs(), getMaxMs());
    }
}