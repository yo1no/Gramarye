package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.Gramarye;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Test-only heap and exact clone-event accounting for one dedicated process. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.DEDICATED_SERVER)
final class P4C2ProbeServerLifecycle {
    private static final long FIXED_INITIAL_HEAP_BYTES = 512L * 1_024L * 1_024L;
    private static final long FIXED_MAXIMUM_HEAP_BYTES = 1_024L * 1_024L * 1_024L;

    private static MinecraftServer activeServer;
    private static HeapMetrics heapMetrics;
    private static long startedAtNanos;
    private static boolean finished;
    private static CloneExpectation cloneExpectation;

    private P4C2ProbeServerLifecycle() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (activeServer != null || heapMetrics != null) {
            throw new IllegalStateException("P4-C2 metrics lifecycle installed twice");
        }
        activeServer = event.getServer();
        heapMetrics = HeapMetrics.start();
        startedAtNanos = System.nanoTime();
        finished = false;
        cloneExpectation = null;
    }

    @SubscribeEvent
    static void onClone(PlayerEvent.Clone event) {
        var expected = cloneExpectation;
        if (expected == null) {
            return;
        }
        if (!event.getOriginal().getUUID().equals(expected.playerId)
                || !event.getEntity().getUUID().equals(expected.playerId)
                || event.isWasDeath() != expected.wasDeath
                || expected.cloneCount != 0) {
            throw new AssertionError("P4-C2 observed an unexpected Clone event");
        }
        expected.cloneCount++;
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        var expected = cloneExpectation;
        if (expected == null) {
            return;
        }
        if (!event.getEntity().getUUID().equals(expected.playerId)
                || event.isEndConquered() != expected.endConquered
                || expected.respawnCount != 0) {
            throw new AssertionError("P4-C2 observed an unexpected respawn event");
        }
        expected.respawnCount++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerStopped(ServerStoppedEvent event) {
        if (activeServer == event.getServer()) {
            activeServer = null;
            heapMetrics = null;
            startedAtNanos = 0;
            finished = false;
            cloneExpectation = null;
        }
    }

    static void beginClone(
            MinecraftServer server,
            UUID playerId,
            boolean wasDeath,
            boolean endConquered) {
        requireSession(server);
        if (cloneExpectation != null) {
            throw new IllegalStateException("P4-C2 clone expectation overlaps");
        }
        cloneExpectation = new CloneExpectation(playerId, wasDeath, endConquered);
    }

    static void finishClone(MinecraftServer server) {
        requireSession(server);
        var expected = cloneExpectation;
        cloneExpectation = null;
        if (expected == null
                || expected.cloneCount != 1
                || expected.respawnCount != 1) {
            throw new AssertionError("P4-C2 lifecycle did not emit one clone and respawn");
        }
    }

    static void sample(MinecraftServer server) {
        requireSession(server).sample();
    }

    static ServerMetrics finish(MinecraftServer server) {
        var metrics = requireSession(server);
        if (finished || cloneExpectation != null) {
            throw new IllegalStateException("P4-C2 metrics cannot finish now");
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

    private static HeapMetrics requireSession(MinecraftServer server) {
        if (activeServer != server || heapMetrics == null || finished) {
            throw new IllegalStateException("P4-C2 metrics lifecycle is unavailable");
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

    private static final class CloneExpectation {
        private final UUID playerId;
        private final boolean wasDeath;
        private final boolean endConquered;
        private int cloneCount;
        private int respawnCount;

        private CloneExpectation(
                UUID playerId, boolean wasDeath, boolean endConquered) {
            this.playerId = playerId;
            this.wasDeath = wasDeath;
            this.endConquered = endConquered;
        }
    }

    private static final class HeapMetrics {
        private final List<MemoryPoolMXBean> heapPools;
        private final long maximum;
        private final long initialCommitted;
        private long sampledPeakUsed;

        private HeapMetrics(
                List<MemoryPoolMXBean> heapPools,
                long maximum,
                long initialCommitted) {
            this.heapPools = heapPools;
            this.maximum = maximum;
            this.initialCommitted = initialCommitted;
            sampledPeakUsed = currentUsed(heapPools);
        }

        private static HeapMetrics start() {
            var pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                    .filter(pool -> pool.getType() == MemoryType.HEAP)
                    .toList();
            if (pools.isEmpty()) {
                throw new IllegalStateException("no heap memory pools are available");
            }
            for (var pool : pools) {
                pool.resetPeakUsage();
            }
            var metrics = new HeapMetrics(
                    pools,
                    Runtime.getRuntime().maxMemory(),
                    sumCommitted(pools));
            if (metrics.maximum > FIXED_MAXIMUM_HEAP_BYTES
                    || metrics.initialCommitted < FIXED_INITIAL_HEAP_BYTES) {
                throw new IllegalStateException("P4-C2 process is outside fixed heap");
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
                    maximum, initialCommitted, sampledPeakUsed, poolPeakSum);
        }

        private static long sumCommitted(List<MemoryPoolMXBean> pools) {
            var result = 0L;
            for (var pool : pools) {
                result = Math.addExact(result, pool.getUsage().getCommitted());
            }
            return result;
        }

        private static long currentUsed(List<MemoryPoolMXBean> pools) {
            var result = 0L;
            for (var pool : pools) {
                result = Math.addExact(result, pool.getUsage().getUsed());
            }
            return result;
        }

        private record HeapSnapshot(
                long maximum,
                long initialCommitted,
                long peakUsed,
                long poolPeakSum) {
        }
    }
}
