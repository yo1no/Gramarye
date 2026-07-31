package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.Gramarye;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Test-only fixed-heap measurement installed before production ServerStarting ingress. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.DEDICATED_SERVER)
final class P4D3ProbeServerLifecycle {
    private static final long FIXED_INITIAL_HEAP_BYTES = 512L * 1_024 * 1_024;
    private static final long FIXED_MAXIMUM_HEAP_BYTES = 1_024L * 1_024 * 1_024;

    private static MinecraftServer activeServer;
    private static D3HeapMetrics heapMetrics;
    private static long startedAtNanos;
    private static boolean finished;

    private P4D3ProbeServerLifecycle() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (activeServer != null || heapMetrics != null) {
            throw new IllegalStateException("P4-D3 metrics lifecycle was installed twice");
        }
        activeServer = event.getServer();
        startedAtNanos = System.nanoTime();
        heapMetrics = D3HeapMetrics.start();
        finished = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerStopped(ServerStoppedEvent event) {
        if (activeServer == event.getServer()) {
            activeServer = null;
            heapMetrics = null;
            startedAtNanos = 0L;
            finished = false;
        }
    }

    static void sample(MinecraftServer server) {
        requireSession(server).sample();
    }

    static ServerMetrics finish(MinecraftServer server) {
        var metrics = requireSession(server);
        if (finished) {
            throw new IllegalStateException("P4-D3 metrics were finished twice");
        }
        finished = true;
        var snapshot = metrics.finish();
        return new ServerMetrics(
                snapshot.maximum(),
                snapshot.initialCommitted(),
                snapshot.peakUsed(),
                snapshot.poolPeakSum(),
                (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private static D3HeapMetrics requireSession(MinecraftServer server) {
        if (activeServer != server || heapMetrics == null || finished) {
            throw new IllegalStateException("P4-D3 metrics lifecycle is unavailable");
        }
        return heapMetrics;
    }

    record ServerMetrics(
            long maximum,
            long initialCommitted,
            long sampledPeak,
            long poolPeakSum,
            long elapsedMillis) {
    }

    /** Runtime-isolated equivalent of the approved A3 JMX sampler. */
    private static final class D3HeapMetrics {
        private final List<MemoryPoolMXBean> heapPools;
        private final long heapMax;
        private final long initialCommitted;
        private long sampledPeakUsed;

        private D3HeapMetrics(
                List<MemoryPoolMXBean> heapPools,
                long heapMax,
                long initialCommitted) {
            this.heapPools = heapPools;
            this.heapMax = heapMax;
            this.initialCommitted = initialCommitted;
            sampledPeakUsed = currentUsed(heapPools);
        }

        private static D3HeapMetrics start() {
            var pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                    .filter(pool -> pool.getType() == MemoryType.HEAP)
                    .toList();
            if (pools.isEmpty()) {
                throw new IllegalStateException("no heap memory pools are available");
            }
            for (var pool : pools) {
                pool.resetPeakUsage();
            }
            var metrics = new D3HeapMetrics(
                    pools, Runtime.getRuntime().maxMemory(), sumCommitted(pools));
            if (metrics.heapMax > FIXED_MAXIMUM_HEAP_BYTES
                    || metrics.initialCommitted < FIXED_INITIAL_HEAP_BYTES) {
                throw new IllegalStateException(
                        "P4-D3 process is outside its fixed heap envelope");
            }
            return metrics;
        }

        private void sample() {
            sampledPeakUsed = Math.max(sampledPeakUsed, currentUsed(heapPools));
        }

        private HeapSnapshot finish() {
            sample();
            var poolPeakSum = 0L;
            for (var pool : heapPools) {
                poolPeakSum = Math.addExact(
                        poolPeakSum, pool.getPeakUsage().getUsed());
            }
            return new HeapSnapshot(
                    heapMax, initialCommitted, sampledPeakUsed, poolPeakSum);
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

        private record HeapSnapshot(
                long maximum,
                long initialCommitted,
                long peakUsed,
                long poolPeakSum) {
        }
    }
}
