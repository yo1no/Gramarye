package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.Gramarye;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Test-only lifecycle measurement that starts before production ServerStarting ingress. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.DEDICATED_SERVER)
final class P4B2ProbeServerLifecycle {
    private static MinecraftServer activeServer;
    private static P4A3ProbeHeapMetrics heapMetrics;
    private static long startedAtNanos;
    private static boolean finished;

    private P4B2ProbeServerLifecycle() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (activeServer != null || heapMetrics != null) {
            throw new IllegalStateException("P4-B2 metrics lifecycle was installed twice");
        }
        activeServer = event.getServer();
        startedAtNanos = System.nanoTime();
        heapMetrics = P4A3ProbeHeapMetrics.start();
        finished = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerStopped(ServerStoppedEvent event) {
        if (activeServer == event.getServer()) {
            activeServer = null;
            heapMetrics = null;
            startedAtNanos = 0;
            finished = false;
        }
    }

    static void sample(MinecraftServer server) {
        requireSession(server).sample();
    }

    static ServerMetrics finish(MinecraftServer server) {
        var metrics = requireSession(server);
        if (finished) {
            throw new IllegalStateException("P4-B2 metrics were finished twice");
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

    private static P4A3ProbeHeapMetrics requireSession(MinecraftServer server) {
        if (activeServer != server || heapMetrics == null || finished) {
            throw new IllegalStateException("P4-B2 metrics lifecycle is unavailable");
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
}
