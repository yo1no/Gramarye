package com.yo1no.gramarye.magic.definition.store;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;

/** Fixed-heap probe measurements that do not depend on free-memory or GC timing. */
final class P4A3ProbeHeapMetrics {
    private static final long FIXED_INITIAL_HEAP_BYTES = 512L * 1_024 * 1_024;
    private static final long FIXED_MAXIMUM_HEAP_BYTES = 1_024L * 1_024 * 1_024;

    private final List<MemoryPoolMXBean> heapPools;
    private final long heapMax;
    private final long initialCommitted;
    private long sampledPeakUsed;

    private P4A3ProbeHeapMetrics(
            List<MemoryPoolMXBean> heapPools,
            long heapMax,
            long initialCommitted) {
        this.heapPools = heapPools;
        this.heapMax = heapMax;
        this.initialCommitted = initialCommitted;
        sampledPeakUsed = currentUsed(heapPools);
    }

    static P4A3ProbeHeapMetrics start() {
        var pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP)
                .toList();
        if (pools.isEmpty()) {
            throw new IllegalStateException("no heap memory pools are available");
        }
        for (var pool : pools) {
            pool.resetPeakUsage();
        }
        var metrics = new P4A3ProbeHeapMetrics(
                pools,
                Runtime.getRuntime().maxMemory(),
                sumCommitted(pools));
        if (metrics.heapMax > FIXED_MAXIMUM_HEAP_BYTES
                || metrics.initialCommitted < FIXED_INITIAL_HEAP_BYTES) {
            throw new IllegalStateException("probe process is outside its fixed heap envelope");
        }
        return metrics;
    }

    void sample() {
        sampledPeakUsed = Math.max(sampledPeakUsed, currentUsed(heapPools));
    }

    HeapSnapshot finish() {
        sample();
        var poolPeakSum = 0L;
        for (var pool : heapPools) {
            poolPeakSum = Math.addExact(poolPeakSum, pool.getPeakUsage().getUsed());
        }
        return new HeapSnapshot(
                heapMax,
                initialCommitted,
                sampledPeakUsed,
                poolPeakSum);
    }

    private static long sumCommitted(List<MemoryPoolMXBean> pools) {
        var committed = 0L;
        for (var pool : pools) {
            committed = Math.addExact(committed, pool.getUsage().getCommitted());
        }
        return committed;
    }

    private static long currentUsed(List<MemoryPoolMXBean> pools) {
        var used = 0L;
        for (var pool : pools) {
            used = Math.addExact(used, pool.getUsage().getUsed());
        }
        return used;
    }

    record HeapSnapshot(
            long maximum,
            long initialCommitted,
            long peakUsed,
            long poolPeakSum) {
        HeapSnapshot {
            if (maximum <= 0 || initialCommitted < 0 || peakUsed < 0 || poolPeakSum < 0) {
                throw new IllegalArgumentException("heap metrics are outside their valid range");
            }
        }
    }
}
